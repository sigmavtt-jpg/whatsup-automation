package com.whatsup.automation.presentation.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsup.automation.data.local.contacts.DeviceContactItem
import com.whatsup.automation.data.local.contacts.DeviceContactsManager
import com.whatsup.automation.domain.model.Group
import com.whatsup.automation.domain.model.GroupMember
import com.whatsup.automation.domain.model.GroupMessageLog
import com.whatsup.automation.domain.repository.GroupRepository
import com.whatsup.automation.domain.usecase.CreatePartitionedCampaignUseCase
import com.whatsup.automation.domain.usecase.ManageGroupMembersUseCase
import com.whatsup.automation.domain.usecase.SendCampaignBroadcastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val searchQuery: String = "",
    val isCreateModalOpen: Boolean = false,
    val newGroupName: String = "",
    val newGroupDescription: String = "",
    val isPartitionMode: Boolean = true,
    val partitionSize: Int = 100,
    val deviceContacts: List<DeviceContactItem> = emptyList(),
    val contactSearchQuery: String = "",
    val selectedContactPhones: Set<String> = emptySet(),
    val selectedGroup: Group? = null,
    val selectedGroupMembers: List<GroupMember> = emptyList(),
    val selectedGroupInsiteLogs: List<GroupMessageLog> = emptyList(),
    val selectedGroupCompletedLogs: List<GroupMessageLog> = emptyList(),
    val activeDetailsTab: Int = 0, // 0 = الأعضاء وإدارتهم, 1 = سجل المستلمين (المنتهي), 2 = قيد الإرسال (الجاري)
    val broadcastTexts: Map<Long, String> = emptyMap(), // groupId -> broadcast message text
    val isBroadcastingMap: Map<Long, Boolean> = emptyMap(), // groupId -> boolean
    val isPausedMap: Map<Long, Boolean> = emptyMap(), // groupId -> boolean
    val broadcastingProgress: Map<Long, Pair<Int, Int>> = emptyMap(), // groupId -> Pair(sent, total)
    val isAddMemberModalOpen: Boolean = false,
    val addMemberSearchQuery: String = "",
    val selectedMembersToAdd: Set<String> = emptySet(),
    val customAddName: String = "",
    val customAddPhone: String = "",
    val isSuccessMessageShowing: Boolean = false
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val deviceContactsManager: DeviceContactsManager,
    private val createPartitionedCampaignUseCase: CreatePartitionedCampaignUseCase,
    private val sendCampaignBroadcastUseCase: SendCampaignBroadcastUseCase,
    private val manageGroupMembersUseCase: ManageGroupMembersUseCase,
    private val notificationRepository: com.whatsup.automation.domain.repository.NotificationRepository
) : ViewModel() {

    val notifications = notificationRepository.notifications
    val unreadNotificationsCount = notificationRepository.unreadCount

    fun markNotificationsAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead()
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            notificationRepository.clearAll()
        }
    }

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()
    private var detailsJob: kotlinx.coroutines.Job? = null

    init {
        loadGroups()
        loadDeviceContacts()
    }

    private fun loadGroups() {
        viewModelScope.launch {
            groupRepository.getAllGroups().collect { groupList ->
                _uiState.update { it.copy(groups = groupList) }
            }
        }
    }

    fun loadDeviceContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val phonebookContacts = deviceContactsManager.getAllDeviceContacts()
            val combined = mutableListOf<DeviceContactItem>()
            val seen = mutableSetOf<String>()

            for (c in phonebookContacts) {
                val normalized = deviceContactsManager.normalizePhoneNumber(c.phone)
                if (deviceContactsManager.isValidDisplayName(c.name) && seen.add(normalized)) {
                    combined.add(c.copy(phone = normalized))
                }
            }

            _uiState.update { it.copy(deviceContacts = combined) }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onContactSearchQueryChange(query: String) {
        _uiState.update { it.copy(contactSearchQuery = query) }
    }

    fun onPartitionModeChange(enabled: Boolean) {
        _uiState.update { it.copy(isPartitionMode = enabled) }
    }

    fun onPartitionSizeChange(size: Int) {
        val validSize = when {
            size <= 0 -> 100
            size > 5000 -> 5000
            else -> size
        }
        _uiState.update { it.copy(partitionSize = validSize) }
    }

    fun openCreateModal() {
        loadDeviceContacts()
        _uiState.update {
            it.copy(
                isCreateModalOpen = true,
                newGroupName = "",
                newGroupDescription = "",
                contactSearchQuery = "",
                selectedContactPhones = emptySet(),
                isPartitionMode = true,
                partitionSize = 100
            )
        }
    }

    fun closeCreateModal() {
        _uiState.update { it.copy(isCreateModalOpen = false) }
    }

    fun onNewGroupNameChange(name: String) {
        _uiState.update { it.copy(newGroupName = name) }
    }

    fun onNewGroupDescriptionChange(desc: String) {
        _uiState.update { it.copy(newGroupDescription = desc) }
    }

    fun toggleContactSelection(phone: String) {
        _uiState.update { current ->
            val updated = current.selectedContactPhones.toMutableSet()
            if (updated.contains(phone)) {
                updated.remove(phone)
            } else {
                updated.add(phone)
            }
            current.copy(selectedContactPhones = updated)
        }
    }

    fun selectAllContacts() {
        _uiState.update { current ->
            val allPhones = current.deviceContacts.map { it.phone }.toSet()
            current.copy(selectedContactPhones = allPhones)
        }
    }

    fun clearContactSelection() {
        _uiState.update { it.copy(selectedContactPhones = emptySet()) }
    }

    fun createGroup() {
        val currentState = _uiState.value
        val name = currentState.newGroupName.trim()
        if (name.isBlank() || currentState.selectedContactPhones.isEmpty()) return

        val members = currentState.deviceContacts
            .filter { currentState.selectedContactPhones.contains(it.phone) }
            .map { GroupMember(contactName = it.name, phone = it.phone) }

        viewModelScope.launch {
            createPartitionedCampaignUseCase.execute(
                baseName = name,
                description = currentState.newGroupDescription.trim(),
                members = members,
                partitionSize = currentState.partitionSize,
                isPartitionMode = currentState.isPartitionMode
            )
            closeCreateModal()
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            groupRepository.deleteGroup(groupId)
            if (_uiState.value.selectedGroup?.id == groupId) {
                closeGroupDetails()
            }
        }
    }

    fun selectGroupForDetails(group: Group) {
        _uiState.update { it.copy(selectedGroup = group, activeDetailsTab = 0) }

        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            launch {
                groupRepository.getGroupMembers(group.id).collect { members ->
                    _uiState.update { it.copy(selectedGroupMembers = members) }
                }
            }
            launch {
                groupRepository.getInsiteLogsForGroup(group.id).collect { logs ->
                    _uiState.update { it.copy(selectedGroupInsiteLogs = logs) }
                }
            }
            launch {
                groupRepository.getCompletedLogsForGroup(group.id).collect { logs ->
                    _uiState.update { it.copy(selectedGroupCompletedLogs = logs) }
                }
            }
        }
    }

    fun closeGroupDetails() {
        detailsJob?.cancel()
        detailsJob = null
        _uiState.update {
            it.copy(
                selectedGroup = null,
                selectedGroupMembers = emptyList(),
                selectedGroupInsiteLogs = emptyList(),
                selectedGroupCompletedLogs = emptyList(),
                isAddMemberModalOpen = false
            )
        }
    }

    fun setDetailsTab(tabIndex: Int) {
        _uiState.update { it.copy(activeDetailsTab = tabIndex) }
    }

    fun removeMemberFromGroup(memberId: Long) {
        viewModelScope.launch {
            manageGroupMembersUseCase.removeMember(memberId)
        }
    }

    fun openAddMemberModal() {
        loadDeviceContacts()
        _uiState.update {
            it.copy(
                isAddMemberModalOpen = true,
                addMemberSearchQuery = "",
                selectedMembersToAdd = emptySet(),
                customAddName = "",
                customAddPhone = ""
            )
        }
    }

    fun closeAddMemberModal() {
        _uiState.update { it.copy(isAddMemberModalOpen = false) }
    }

    fun onAddMemberSearchQueryChange(query: String) {
        _uiState.update { it.copy(addMemberSearchQuery = query) }
    }

    fun toggleMemberToAddSelection(phone: String) {
        _uiState.update { current ->
            val updated = current.selectedMembersToAdd.toMutableSet()
            if (updated.contains(phone)) {
                updated.remove(phone)
            } else {
                updated.add(phone)
            }
            current.copy(selectedMembersToAdd = updated)
        }
    }

    fun onCustomAddNameChange(name: String) {
        _uiState.update { it.copy(customAddName = name) }
    }

    fun onCustomAddPhoneChange(phone: String) {
        _uiState.update { it.copy(customAddPhone = phone) }
    }

    fun addSelectedContactsToGroup() {
        val currentGroup = _uiState.value.selectedGroup ?: return
        val currentMemberPhones = _uiState.value.selectedGroupMembers.map { it.phone }.toSet()
        val toAdd = _uiState.value.deviceContacts
            .filter { _uiState.value.selectedMembersToAdd.contains(it.phone) && !currentMemberPhones.contains(it.phone) }

        viewModelScope.launch {
            for (contact in toAdd) {
                manageGroupMembersUseCase.addMember(
                    groupId = currentGroup.id,
                    name = contact.name,
                    phone = contact.phone
                )
            }
            closeAddMemberModal()
        }
    }

    fun addCustomContactToGroup() {
        val currentGroup = _uiState.value.selectedGroup ?: return
        val name = _uiState.value.customAddName.trim()
        val phone = _uiState.value.customAddPhone.trim()
        if (name.isBlank() || phone.isBlank()) return

        viewModelScope.launch {
            manageGroupMembersUseCase.addMember(
                groupId = currentGroup.id,
                name = name,
                phone = phone
            )
            closeAddMemberModal()
        }
    }

    fun onBroadcastTextChange(groupId: Long, text: String) {
        _uiState.update { current ->
            val updatedMap = current.broadcastTexts.toMutableMap()
            updatedMap[groupId] = text
            current.copy(broadcastTexts = updatedMap)
        }
    }

    fun pauseBroadcast(groupId: Long) {
        sendCampaignBroadcastUseCase.pause(groupId)
        _uiState.update { current ->
            val pMap = current.isPausedMap.toMutableMap()
            pMap[groupId] = true
            current.copy(isPausedMap = pMap)
        }
    }

    fun resumeBroadcast(groupId: Long) {
        sendCampaignBroadcastUseCase.resume(groupId)
        _uiState.update { current ->
            val pMap = current.isPausedMap.toMutableMap()
            pMap[groupId] = false
            current.copy(isPausedMap = pMap)
        }
    }

    fun cancelBroadcast(groupId: Long) {
        sendCampaignBroadcastUseCase.cancel(groupId)
        _uiState.update { current ->
            val pMap = current.isPausedMap.toMutableMap()
            pMap[groupId] = false
            val bMap = current.isBroadcastingMap.toMutableMap()
            bMap[groupId] = false
            current.copy(isPausedMap = pMap, isBroadcastingMap = bMap)
        }
    }

    fun sendBroadcast(groupId: Long) {
        val text = _uiState.value.broadcastTexts[groupId]?.trim() ?: ""
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { current ->
                val broadMap = current.isBroadcastingMap.toMutableMap()
                broadMap[groupId] = true
                val pauseMap = current.isPausedMap.toMutableMap()
                pauseMap[groupId] = false
                current.copy(isBroadcastingMap = broadMap, isPausedMap = pauseMap)
            }

            // مراقبة حالة الإيقاف المؤقت بالتزامن
            val pauseObserverJob = launch {
                sendCampaignBroadcastUseCase.isPaused(groupId).collect { isPaused ->
                    _uiState.update { current ->
                        val pMap = current.isPausedMap.toMutableMap()
                        pMap[groupId] = isPaused
                        current.copy(isPausedMap = pMap)
                    }
                }
            }

            sendCampaignBroadcastUseCase.execute(
                groupId = groupId,
                messageText = text,
                onProgress = { sent, total ->
                    _uiState.update { current ->
                        val progMap = current.broadcastingProgress.toMutableMap()
                        progMap[groupId] = Pair(sent, total)
                        current.copy(broadcastingProgress = progMap)
                    }
                }
            )

            pauseObserverJob.cancel()

            _uiState.update { current ->
                val broadMap = current.isBroadcastingMap.toMutableMap()
                broadMap[groupId] = false
                val pauseMap = current.isPausedMap.toMutableMap()
                pauseMap[groupId] = false
                val textsMap = current.broadcastTexts.toMutableMap()
                textsMap[groupId] = ""
                current.copy(
                    isBroadcastingMap = broadMap,
                    isPausedMap = pauseMap,
                    broadcastTexts = textsMap,
                    isSuccessMessageShowing = true
                )
            }
        }
    }

    fun calculateEstimatedTime(memberCount: Int): String {
        return sendCampaignBroadcastUseCase.calculateEstimatedTime(memberCount)
    }
}

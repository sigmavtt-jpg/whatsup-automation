package com.whatsup.automation.presentation.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsup.automation.data.local.contacts.DeviceContactItem
import com.whatsup.automation.data.local.contacts.DeviceContactsManager
import com.whatsup.automation.domain.model.Group
import com.whatsup.automation.domain.model.GroupMember
import com.whatsup.automation.domain.model.GroupMessageLog
import com.whatsup.automation.data.local.entity.SyncedContactDao
import com.whatsup.automation.domain.repository.GroupRepository
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
    val deviceContacts: List<DeviceContactItem> = emptyList(),
    val contactSearchQuery: String = "",
    val selectedContactPhones: Set<String> = emptySet(),
    val selectedGroup: Group? = null,
    val selectedGroupMembers: List<GroupMember> = emptyList(),
    val selectedGroupInsiteLogs: List<GroupMessageLog> = emptyList(),
    val selectedGroupCompletedLogs: List<GroupMessageLog> = emptyList(),
    val activeDetailsTab: Int = 0, // 0 = سجل المرسل, 1 = سجل المنتهي
    val broadcastTexts: Map<Long, String> = emptyMap(), // groupId -> broadcast message text
    val isBroadcasting: Boolean = false,
    val isSuccessMessageShowing: Boolean = false
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val deviceContactsManager: DeviceContactsManager
) : ViewModel() {

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

            // 1. جهات اتصال الهاتف الفعلي
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

    fun openCreateModal() {
        loadDeviceContacts()
        _uiState.update {
            it.copy(
                isCreateModalOpen = true,
                newGroupName = "",
                newGroupDescription = "",
                contactSearchQuery = "",
                selectedContactPhones = emptySet()
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
        if (name.isBlank()) return

        val members = currentState.deviceContacts
            .filter { currentState.selectedContactPhones.contains(it.phone) }
            .map { GroupMember(contactName = it.name, phone = it.phone) }

        viewModelScope.launch {
            groupRepository.createGroup(
                name = name,
                description = currentState.newGroupDescription.trim(),
                members = members
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
                selectedGroupCompletedLogs = emptyList()
            )
        }
    }

    fun setDetailsTab(tabIndex: Int) {
        _uiState.update { it.copy(activeDetailsTab = tabIndex) }
    }

    fun onBroadcastTextChange(groupId: Long, text: String) {
        _uiState.update { current ->
            val updatedMap = current.broadcastTexts.toMutableMap()
            updatedMap[groupId] = text
            current.copy(broadcastTexts = updatedMap)
        }
    }

    fun sendBroadcast(groupId: Long) {
        val text = _uiState.value.broadcastTexts[groupId]?.trim() ?: ""
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isBroadcasting = true) }
            groupRepository.sendBroadcastMessageToGroup(groupId, text)
            _uiState.update { current ->
                val updatedMap = current.broadcastTexts.toMutableMap()
                updatedMap[groupId] = ""
                current.copy(isBroadcasting = false, isSuccessMessageShowing = true)
            }
        }
    }
}

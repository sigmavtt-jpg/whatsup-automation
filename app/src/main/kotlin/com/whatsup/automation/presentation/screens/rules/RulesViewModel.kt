package com.whatsup.automation.presentation.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsup.automation.domain.model.PatternType
import com.whatsup.automation.domain.model.Rule
import com.whatsup.automation.domain.model.RuleAction
import com.whatsup.automation.domain.repository.RuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RulesUiState(
    val rules: List<Rule> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val editingRule: Rule? = null,
    val formattingSettings: com.whatsup.automation.domain.model.ContactFormattingSettings = com.whatsup.automation.domain.model.ContactFormattingSettings()
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val contactFormattingRepository: com.whatsup.automation.domain.repository.ContactFormattingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RulesUiState())
    val uiState: StateFlow<RulesUiState> = _uiState.asStateFlow()

    init {
        observeRules()
        observeFormattingSettings()
    }

    private fun observeFormattingSettings() {
        viewModelScope.launch {
            try {
                contactFormattingRepository.getSettings().collect { settings ->
                    _uiState.update { it.copy(formattingSettings = settings) }
                }
            } catch (_: Exception) {}
        }
    }

    fun updateFormattingSettings(settings: com.whatsup.automation.domain.model.ContactFormattingSettings) {
        viewModelScope.launch {
            try {
                contactFormattingRepository.updateSettings(settings)
            } catch (_: Exception) {}
        }
    }

    private fun observeRules() {
        viewModelScope.launch {
            try {
                ruleRepository.getAllRules().collect { ruleList ->
                    val sorted = ruleList.sortedBy { it.priority }
                    _uiState.update { current ->
                        current.copy(rules = sorted, isLoading = false)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refreshRules() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            kotlinx.coroutines.delay(400)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun toggleRule(ruleId: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            try {
                ruleRepository.toggleRule(ruleId, isEnabled)
            } catch (_: Exception) {}
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            try {
                ruleRepository.deleteRule(ruleId)
            } catch (_: Exception) {}
        }
    }

    fun startEditingRule(rule: Rule?) {
        _uiState.update { it.copy(editingRule = rule) }
    }

    fun addRule(
        name: String,
        description: String,
        patternType: PatternType,
        patternValue: String,
        replyMessage: String?,
        isSaveContact: Boolean = false,
        priority: Int = 1
    ) {
        viewModelScope.launch {
            try {
                val finalReply = if (!replyMessage.isNullOrBlank()) {
                    replyMessage.trim()
                } else {
                    if (isSaveContact) "تم حفظك باسم {name} بنجاح ✅" else "تم استلام رسالتك بنجاح."
                }

                val action: RuleAction = if (isSaveContact) {
                    RuleAction.SaveContactAndReply(finalReply)
                } else {
                    RuleAction.SendReply(finalReply)
                }

                val newRule = Rule(
                    name = name.trim(),
                    description = description.trim(),
                    patternType = patternType,
                    patternValue = patternValue.trim(),
                    actions = listOf(action),
                    priority = priority,
                    isEnabled = true
                )
                ruleRepository.insertRule(newRule)
            } catch (_: Exception) {}
        }
    }

    fun editRule(
        ruleId: Long,
        name: String,
        description: String,
        patternType: PatternType,
        patternValue: String,
        replyMessage: String?,
        isSaveContact: Boolean = false,
        priority: Int = 1
    ) {
        viewModelScope.launch {
            try {
                val existing = ruleRepository.getRuleById(ruleId)
                val finalReply = if (!replyMessage.isNullOrBlank()) {
                    replyMessage.trim()
                } else {
                    if (isSaveContact) "تم حفظك باسم {name} بنجاح ✅" else "تم استلام رسالتك بنجاح."
                }

                val action: RuleAction = if (isSaveContact) {
                    RuleAction.SaveContactAndReply(finalReply)
                } else {
                    RuleAction.SendReply(finalReply)
                }

                val updated = Rule(
                    id = ruleId,
                    name = name.trim(),
                    description = description.trim(),
                    patternType = patternType,
                    patternValue = patternValue.trim(),
                    actions = listOf(action),
                    priority = priority,
                    isEnabled = existing?.isEnabled ?: true,
                    createdAt = existing?.createdAt ?: java.time.Instant.now()
                )
                ruleRepository.updateRule(updated)
                _uiState.update { it.copy(editingRule = null) }
            } catch (_: Exception) {}
        }
    }
}

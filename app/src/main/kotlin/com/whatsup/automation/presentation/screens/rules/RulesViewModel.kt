package com.whatsup.automation.presentation.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsup.automation.domain.model.PatternType
import com.whatsup.automation.domain.model.Rule
import com.whatsup.automation.domain.model.RuleAction
import com.whatsup.automation.domain.repository.RuleRepository
import com.whatsup.automation.domain.usecase.MatchRuleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TestRuleResult(
    val matchedRule: Rule,
    val resultingReply: String,
    val extractedName: String? = null
)

data class RulesUiState(
    val rules: List<Rule> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val testInput: String = "",
    val testResult: TestRuleResult? = null,
    val editingRule: Rule? = null
)

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val matchRuleUseCase: MatchRuleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RulesUiState())
    val uiState: StateFlow<RulesUiState> = _uiState.asStateFlow()

    init {
        observeRules()
    }

    private fun observeRules() {
        viewModelScope.launch {
            try {
                ruleRepository.getAllRules().collect { ruleList ->
                    val sorted = ruleList.sortedBy { it.priority }
                    _uiState.update { current ->
                        current.copy(rules = sorted, isLoading = false)
                    }
                    // إعادة تقييم الفحص التجريبي إن وُجد نص
                    if (_uiState.value.testInput.isNotBlank()) {
                        onTestInputChange(_uiState.value.testInput)
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
        priority: Int = 1
    ) {
        viewModelScope.launch {
            try {
                val actions = mutableListOf<RuleAction>()
                if (!replyMessage.isNullOrBlank()) {
                    actions.add(RuleAction.SendReply(replyMessage.trim()))
                }
                if (actions.isEmpty()) {
                    actions.add(RuleAction.SendReply("تم استلام رسالتك بنجاح."))
                }

                val newRule = Rule(
                    name = name.trim(),
                    description = description.trim(),
                    patternType = patternType,
                    patternValue = patternValue.trim(),
                    actions = actions,
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
        priority: Int = 1
    ) {
        viewModelScope.launch {
            try {
                val existing = ruleRepository.getRuleById(ruleId)
                val actions = mutableListOf<RuleAction>()
                if (!replyMessage.isNullOrBlank()) {
                    actions.add(RuleAction.SendReply(replyMessage.trim()))
                }
                if (actions.isEmpty()) {
                    actions.add(RuleAction.SendReply("تم استلام رسالتك بنجاح."))
                }

                val updated = Rule(
                    id = ruleId,
                    name = name.trim(),
                    description = description.trim(),
                    patternType = patternType,
                    patternValue = patternValue.trim(),
                    actions = actions,
                    priority = priority,
                    isEnabled = existing?.isEnabled ?: true,
                    createdAt = existing?.createdAt ?: java.time.Instant.now()
                )
                ruleRepository.updateRule(updated)
                _uiState.update { it.copy(editingRule = null) }
            } catch (_: Exception) {}
        }
    }

    fun onTestInputChange(input: String) {
        if (input.isBlank()) {
            _uiState.update { it.copy(testInput = input, testResult = null) }
            return
        }

        try {
            val enabledRules = _uiState.value.rules.filter { it.isEnabled }.sortedBy { it.priority }
            val matchedRule = enabledRules.firstOrNull { matchRuleUseCase.matches(it, input) }

            val result = if (matchedRule != null) {
                var resultingReply = ""
                var extractedName: String? = null
                for (action in matchedRule.actions) {
                    when (action) {
                        is RuleAction.SendReply -> {
                            val replyLines = action.message.lines().map { it.trim() }.filter { it.isNotBlank() }
                            resultingReply = if (replyLines.isNotEmpty()) replyLines.first() else action.message
                        }
                    }
                }
                TestRuleResult(
                    matchedRule = matchedRule,
                    resultingReply = resultingReply,
                    extractedName = extractedName
                )
            } else {
                null
            }

            _uiState.update { it.copy(testInput = input, testResult = result) }
        } catch (e: Exception) {
            _uiState.update { it.copy(testInput = input, testResult = null) }
        }
    }
}

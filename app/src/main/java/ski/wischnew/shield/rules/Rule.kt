package ski.wischnew.shield.rules

import java.util.UUID

enum class RuleType {
    KEYWORD,
    NUMBER,
    COUNTRY
}

enum class RuleAction {
    BLOCK,
    ALLOW
}

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val type: RuleType,
    val pattern: String,
    val action: RuleAction = RuleAction.BLOCK,
    val partialNumber: Boolean = false,
    val enabled: Boolean = true
)

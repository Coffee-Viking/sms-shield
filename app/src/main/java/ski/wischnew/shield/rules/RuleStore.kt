package ski.wischnew.shield.rules

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

class RuleStore(context: Context) {
    private val prefs = context.getSharedPreferences("sms_shield_rules", Context.MODE_PRIVATE)

    fun getRules(): List<Rule> {
        val raw = prefs.getString("rules", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    Rule(
                        id = obj.getString("id"),
                        type = RuleType.valueOf(obj.getString("type")),
                        pattern = obj.getString("pattern"),
                        action = RuleAction.valueOf(obj.optString("action", RuleAction.BLOCK.name)),
                        partialNumber = obj.optBoolean("partialNumber", false),
                        enabled = obj.optBoolean("enabled", true)
                    )
                )
            }
        }
    }

    fun addRule(rule: Rule) {
        val updated = getRules().toMutableList().apply { add(rule) }
        saveRules(updated)
    }

    fun addRuleIfMissing(rule: Rule): Boolean {
        if (findDuplicate(rule) != null) return false
        addRule(rule)
        return true
    }

    fun updateRule(rule: Rule) {
        val updated = getRules().map { existing ->
            if (existing.id == rule.id) rule else existing
        }
        saveRules(updated)
    }

    fun deleteRule(id: String) {
        val updated = getRules().filterNot { it.id == id }
        saveRules(updated)
    }

    fun deleteRules(ids: Set<String>) {
        if (ids.isEmpty()) return
        val updated = getRules().filterNot { it.id in ids }
        saveRules(updated)
    }

    fun findDuplicate(rule: Rule, ignoreId: String? = null): Rule? {
        return getRules().firstOrNull { existing ->
            existing.id != ignoreId &&
                existing.action == rule.action &&
                rulesOverlap(existing, rule)
        }
    }

    fun findConflict(rule: Rule, ignoreId: String? = null): Rule? {
        return getRules().firstOrNull { existing ->
            existing.id != ignoreId &&
                existing.action != rule.action &&
                rulesOverlap(existing, rule)
        }
    }

    fun conflictingRules(rule: Rule, ignoreId: String? = null): List<Rule> {
        return getRules().filter { existing ->
            existing.id != ignoreId &&
                existing.action != rule.action &&
                rulesOverlap(existing, rule)
        }
    }

    private fun rulesOverlap(first: Rule, second: Rule): Boolean {
        if (first.type != second.type) return false
        return when (first.type) {
            RuleType.KEYWORD -> normalizeText(first.pattern) == normalizeText(second.pattern)
            RuleType.NUMBER -> normalizeNumberPattern(first.pattern) == normalizeNumberPattern(second.pattern)
            RuleType.COUNTRY -> first.pattern.trim().uppercase(Locale.ROOT) == second.pattern.trim().uppercase(Locale.ROOT)
        }
    }

    private fun normalizeText(value: String): String {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    }

    private fun normalizeNumberPattern(value: String): String {
        return value.trim().filter { it.isDigit() || it == '+' }
    }

    private fun saveRules(rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { rule ->
            arr.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("type", rule.type.name)
                    .put("pattern", rule.pattern)
                    .put("action", rule.action.name)
                    .put("partialNumber", rule.partialNumber)
                    .put("enabled", rule.enabled)
            )
        }
        prefs.edit().putString("rules", arr.toString()).apply()
    }
}

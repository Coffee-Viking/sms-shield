package ski.wischnew.shield.rules

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.text.Normalizer
import java.util.Locale

class FilterEngine {
    private val phoneUtil = PhoneNumberUtil.getInstance()

    enum class Decision {
        ALLOW,
        BLOCK
    }

    fun shouldBlock(sender: String?, body: String, rules: List<Rule>, defaultRegion: String): Boolean {
        return decide(sender, body, rules, defaultRegion) == Decision.BLOCK
    }

    fun decide(sender: String?, body: String, rules: List<Rule>, defaultRegion: String): Decision {
        val normalizedBody = normalizeText(body)
        val senderDigits = normalizeDigits(sender.orEmpty())
        val senderCountry = getCountryIso(sender, defaultRegion)

        val enabledRules = rules.filter { it.enabled }
        val allowMatched = enabledRules
            .filter { it.action == RuleAction.ALLOW }
            .any { rule ->
                when (rule.type) {
                    RuleType.KEYWORD -> wildcardContains(normalizedBody, normalizeText(rule.pattern))
                    RuleType.NUMBER -> {
                        val patternDigits = normalizeDigits(rule.pattern)
                        if (rule.partialNumber) senderDigits.contains(patternDigits) else wildcardExact(senderDigits, patternDigits)
                    }
                    RuleType.COUNTRY -> senderCountry.equals(rule.pattern.uppercase(Locale.ROOT), ignoreCase = true)
                }
            }
        if (allowMatched) return Decision.ALLOW

        val blockMatched = enabledRules
            .filter { it.action == RuleAction.BLOCK }
            .any { rule ->
                when (rule.type) {
                RuleType.KEYWORD -> wildcardContains(normalizedBody, normalizeText(rule.pattern))
                RuleType.NUMBER -> {
                    val patternDigits = normalizeDigits(rule.pattern)
                    if (rule.partialNumber) {
                        senderDigits.contains(patternDigits)
                    } else {
                        wildcardExact(senderDigits, patternDigits)
                    }
                }
                RuleType.COUNTRY -> senderCountry.equals(rule.pattern.uppercase(Locale.ROOT), ignoreCase = true)
            }
        }
        return if (blockMatched) Decision.BLOCK else Decision.ALLOW
    }

    private fun normalizeText(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    private fun normalizeDigits(number: String): String = number.filter { it.isDigit() || it == '+' }

    private fun wildcardExact(value: String, pattern: String): Boolean {
        val regex = pattern.split("*").joinToString(".*") { Regex.escape(it) }
        return Regex("^$regex$", RegexOption.IGNORE_CASE).containsMatchIn(value)
    }

    private fun wildcardContains(value: String, pattern: String): Boolean {
        val regex = pattern.split("*").joinToString(".*") { Regex.escape(it) }
        return Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(value)
    }

    private fun getCountryIso(number: String?, defaultRegion: String): String? {
        if (number.isNullOrBlank()) return null
        return try {
            val parsed = phoneUtil.parse(number, defaultRegion)
            phoneUtil.getRegionCodeForNumber(parsed)
        } catch (_: NumberParseException) {
            null
        }
    }
}

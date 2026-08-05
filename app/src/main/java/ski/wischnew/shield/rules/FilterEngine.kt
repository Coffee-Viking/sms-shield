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

    fun matchingRules(
        sender: String?,
        body: String,
        rules: List<Rule>,
        defaultRegion: String,
        action: RuleAction
    ): List<Rule> {
        val normalizedBody = normalizeText(body)
        val senderAddress = senderAddress(sender)
        val normalizedSender = normalizeText(senderAddress)
        val senderDigits = normalizeDigits(senderAddress)
        val senderCountry = getCountryIso(senderAddress, defaultRegion)
        return rules.filter { rule ->
            rule.enabled &&
                rule.action == action &&
                ruleMatches(rule, normalizedBody, normalizedSender, senderDigits, senderCountry)
        }
    }

    fun decide(sender: String?, body: String, rules: List<Rule>, defaultRegion: String): Decision {
        val normalizedBody = normalizeText(body)
        val senderAddress = senderAddress(sender)
        val normalizedSender = normalizeText(senderAddress)
        val senderDigits = normalizeDigits(senderAddress)
        val senderCountry = getCountryIso(senderAddress, defaultRegion)

        val enabledRules = rules.filter { it.enabled }
        val allowMatched = enabledRules
            .filter { it.action == RuleAction.ALLOW }
            .any { rule ->
                ruleMatches(rule, normalizedBody, normalizedSender, senderDigits, senderCountry)
            }
        if (allowMatched) return Decision.ALLOW

        val blockMatched = enabledRules
            .filter { it.action == RuleAction.BLOCK }
            .any { rule ->
                ruleMatches(rule, normalizedBody, normalizedSender, senderDigits, senderCountry)
            }
        return if (blockMatched) Decision.BLOCK else Decision.ALLOW
    }

    private fun normalizeText(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFKC)
            .withoutInvisibleFormatting()
            .lowercase(Locale.ROOT)

    private fun senderAddress(sender: String?): String =
        sender.orEmpty().removePrefix("To:").trim()

    private fun normalizeDigits(number: String): String = number.filter { it.isDigit() || it == '+' }

    private fun ruleMatches(
        rule: Rule,
        normalizedBody: String,
        normalizedSender: String,
        senderDigits: String,
        senderCountry: String?
    ): Boolean {
        return when (rule.type) {
            RuleType.KEYWORD -> wildcardContains(normalizedBody, normalizeText(rule.pattern))
            RuleType.SENDER -> wildcardExact(normalizedSender, normalizeText(rule.pattern))
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

    private fun String.withoutInvisibleFormatting(): String {
        return buildString(length) {
            this@withoutInvisibleFormatting.forEach { char ->
                when (Character.getType(char)) {
                    Character.FORMAT.toInt() -> Unit
                    Character.CONTROL.toInt(),
                    Character.LINE_SEPARATOR.toInt(),
                    Character.PARAGRAPH_SEPARATOR.toInt() -> append(' ')
                    else -> append(char)
                }
            }
        }.trim()
    }
}

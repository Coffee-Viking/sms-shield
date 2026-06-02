package ski.wischnew.shield.sms

import kotlin.math.abs

object OtpDetector {
    private val keywordPattern = Regex(
        pattern = """(?:\b(?:otp|code|mtan|one[\s-]?time(?:\s+password)?|verification\s+code|security\s+code|login\s+code|sign[\s-]?in\s+code)\b|验证码)""",
        option = RegexOption.IGNORE_CASE
    )
    private val codePattern = Regex("""(?<!\d)(?:\d[\s-]?){4,8}(?!\d)""")

    fun findOtp(text: String): String? {
        val keywordPositions = keywordPattern.findAll(text).map { it.range.first }.toList()
        if (keywordPositions.isEmpty()) return null

        return codePattern.findAll(text)
            .mapNotNull { match ->
                val code = match.value.filter { it.isDigit() }
                if (code.length !in 4..8) return@mapNotNull null
                val nearestKeywordDistance = keywordPositions.minOf { abs(match.range.first - it) }
                OtpCandidate(code = code, distance = nearestKeywordDistance, position = match.range.first)
            }
            .minWithOrNull(compareBy<OtpCandidate> { it.distance }.thenBy { it.position })
            ?.code
    }

    private data class OtpCandidate(
        val code: String,
        val distance: Int,
        val position: Int
    )
}

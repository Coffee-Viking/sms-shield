package ski.wischnew.shield.sms

data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String?
) {
    val slotLabel: String = "SIM ${slotIndex + 1}"

    val descriptiveName: String?
        get() = listOf(displayName, carrierName)
            .map { it.orEmpty().trim() }
            .firstOrNull { it.isNotBlank() && !it.equals(slotLabel, ignoreCase = true) }

    val shortLabel: String
        get() = slotLabel

    val detailLabel: String
        get() = descriptiveName?.let { "$slotLabel, $it" } ?: slotLabel
}

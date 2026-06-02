package ski.wischnew.shield.sms

data class SmsMessageRecord(
    val id: Long = System.currentTimeMillis(),
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val blocked: Boolean,
    val outgoing: Boolean = false,
    val archived: Boolean = false,
    val deliveryStatus: String? = null,
    val simSubscriptionId: Int? = null,
    val simSlotIndex: Int? = null,
    val simDisplayName: String? = null,
    val simCarrierName: String? = null,
    val autoArchiveFrozen: Boolean = false,
    val blockOverride: Boolean = false
)

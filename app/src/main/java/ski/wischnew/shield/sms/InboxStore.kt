package ski.wischnew.shield.sms

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import ski.wischnew.shield.rules.FilterEngine
import ski.wischnew.shield.rules.Rule
import kotlin.math.abs
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

data class MessageImportResult(
    val imported: Int,
    val duplicates: Int
)

class InboxStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("sms_shield_inbox", Context.MODE_PRIVATE)

    fun listMessages(): List<SmsMessageRecord> {
        val raw = prefs.getString("messages", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    SmsMessageRecord(
                        id = obj.getLong("id"),
                        sender = obj.getString("sender"),
                        body = obj.getString("body"),
                        timestamp = obj.getLong("timestamp"),
                        blocked = obj.getBoolean("blocked"),
                        outgoing = obj.optBoolean("outgoing", false),
                        archived = obj.optBoolean("archived", false),
                        deliveryStatus = obj.optString("deliveryStatus", "").takeIf { it.isNotBlank() },
                        simSubscriptionId = obj.optNullableInt("simSubscriptionId"),
                        simSlotIndex = obj.optNullableInt("simSlotIndex"),
                        simDisplayName = obj.optString("simDisplayName", "").takeIf { it.isNotBlank() },
                        simCarrierName = obj.optString("simCarrierName", "").takeIf { it.isNotBlank() },
                        autoArchiveFrozen = obj.optBoolean("autoArchiveFrozen", false)
                    )
                )
            }
        }.sortedByDescending { it.timestamp }
    }

    fun addMessage(message: SmsMessageRecord): Boolean {
        val existing = listMessages()
        if (existing.any { it.isLikelyDuplicateOf(message) }) return false
        val updated = existing.toMutableList().apply { add(0, message) }.take(500)
        saveMessages(updated)
        return true
    }

    fun addReceivedMessage(message: SmsMessageRecord): Boolean {
        val existing = listMessages()
        if (existing.any { it.isLikelyDuplicateOf(message) }) return false
        val storedMessage = writeToSystemSmsProvider(message, outgoing = false, adoptProviderId = true)
        val updated = existing.toMutableList().apply { add(0, storedMessage) }.take(500)
        saveMessages(updated)
        return true
    }

    fun addSentMessage(message: SmsMessageRecord): Boolean {
        val existing = listMessages()
        if (existing.any { it.isLikelyDuplicateOf(message) }) return false
        val storedMessage = writeToSystemSmsProvider(message, outgoing = true, adoptProviderId = false)
        val updated = existing.toMutableList().apply { add(0, storedMessage) }.take(500)
        saveMessages(updated)
        return true
    }

    fun deleteMessage(id: Long) {
        saveMessages(listMessages().filterNot { it.id == id })
    }

    fun deleteMessages(ids: Set<Long>): Int {
        if (ids.isEmpty()) return 0
        val before = listMessages()
        val updated = before.filterNot { it.id in ids }
        if (updated.size != before.size) saveMessages(updated)
        return before.size - updated.size
    }

    fun updateBlockedState(id: Long, blocked: Boolean): Boolean {
        var changed = false
        val updated = listMessages().map { message ->
            if (message.id == id && (message.blocked != blocked || message.archived)) {
                changed = true
                message.copy(blocked = blocked, archived = false)
            } else {
                message
            }
        }
        if (changed) saveMessages(updated)
        return changed
    }

    fun updateArchivedState(id: Long, archived: Boolean): Boolean {
        var changed = false
        val updated = listMessages().map { message ->
            if (message.id == id && message.archived != archived) {
                changed = true
                message.copy(archived = archived)
            } else {
                message
            }
        }
        if (changed) saveMessages(updated)
        return changed
    }

    fun updateArchivedState(ids: Set<Long>, archived: Boolean): Int {
        if (ids.isEmpty()) return 0
        var changed = 0
        val updated = listMessages().map { message ->
            if (message.id in ids && message.archived != archived) {
                changed++
                message.copy(archived = archived)
            } else {
                message
            }
        }
        if (changed > 0) saveMessages(updated)
        return changed
    }

    fun updateAutoArchiveFrozen(ids: Set<Long>, frozen: Boolean): Int {
        if (ids.isEmpty()) return 0
        var changed = 0
        val updated = listMessages().map { message ->
            if (message.id in ids && message.autoArchiveFrozen != frozen) {
                changed++
                message.copy(autoArchiveFrozen = frozen)
            } else {
                message
            }
        }
        if (changed > 0) saveMessages(updated)
        return changed
    }

    fun returnMessagesToInbox(ids: Set<Long>): Int {
        if (ids.isEmpty()) return 0
        var changed = 0
        val updated = listMessages().map { message ->
            if (message.id in ids && (message.blocked || message.archived)) {
                changed++
                message.copy(blocked = false, archived = false)
            } else {
                message
            }
        }
        if (changed > 0) saveMessages(updated)
        return changed
    }

    fun updateDeliveryStatus(id: Long, status: String): Boolean {
        var changed = false
        val updated = listMessages().map { message ->
            if (message.id == id && message.deliveryStatus != status) {
                changed = true
                message.copy(deliveryStatus = status)
            } else {
                message
            }
        }
        if (changed) saveMessages(updated)
        return changed
    }

    fun applyRulesToMessages(rules: List<Rule>, defaultRegion: String): Int {
        val engine = FilterEngine()
        var changed = 0
        val updated = listMessages().map { message ->
            val shouldBlock = engine.shouldBlock(
                sender = message.sender,
                body = message.body,
                rules = rules,
                defaultRegion = defaultRegion
            )
            val shouldArchive = if (shouldBlock) false else message.archived
            if (message.blocked != shouldBlock || message.archived != shouldArchive) {
                changed++
                message.copy(blocked = shouldBlock, archived = shouldArchive)
            } else {
                message
            }
        }
        if (changed > 0) saveMessages(updated)
        return changed
    }

    fun moveNoLongerBlockedToInbox(rules: List<Rule>, defaultRegion: String): Int {
        val engine = FilterEngine()
        var moved = 0
        val updated = listMessages().map { message ->
            if (!message.blocked) {
                message
            } else {
                val stillBlocked = engine.shouldBlock(
                    sender = message.sender,
                    body = message.body,
                    rules = rules,
                    defaultRegion = defaultRegion
                )
                if (stillBlocked) {
                    message
                } else {
                    moved++
                    message.copy(blocked = false, archived = false)
                }
            }
        }
        if (moved > 0) saveMessages(updated)
        return moved
    }

    fun autoArchiveOlderThan(days: Int, now: Long = System.currentTimeMillis()): Int {
        if (days <= 0) return 0
        val cutoff = now - days * MILLIS_PER_DAY
        var changed = 0
        val updated = listMessages().map { message ->
            if (!message.blocked && !message.archived && !message.autoArchiveFrozen && message.timestamp < cutoff) {
                changed++
                message.copy(archived = true)
            } else {
                message
            }
        }
        if (changed > 0) saveMessages(updated)
        return changed
    }

    fun countBlockedOlderThan(days: Int, now: Long = System.currentTimeMillis()): Int {
        if (days <= 0) return 0
        val cutoff = now - days * MILLIS_PER_DAY
        return listMessages().count { it.blocked && it.timestamp < cutoff }
    }

    fun deleteBlockedOlderThan(days: Int, now: Long = System.currentTimeMillis()): Int {
        if (days <= 0) return 0
        val cutoff = now - days * MILLIS_PER_DAY
        val before = listMessages()
        val updated = before.filterNot { it.blocked && it.timestamp < cutoff }
        if (updated.size != before.size) saveMessages(updated)
        return before.size - updated.size
    }

    fun exportMessages(predicate: (SmsMessageRecord) -> Boolean): JSONArray {
        val arr = JSONArray()
        listMessages().filter(predicate).forEach { message ->
            arr.put(message.toJson())
        }
        return arr
    }

    fun importMessages(importedMessages: List<SmsMessageRecord>): MessageImportResult {
        if (importedMessages.isEmpty()) return MessageImportResult(imported = 0, duplicates = 0)
        val existing = listMessages().toMutableList()
        val usedIds = existing.map { it.id }.toMutableSet()
        var duplicates = 0
        var imported = 0
        var nextId = max(System.currentTimeMillis(), usedIds.maxOrNull() ?: 0L)

        importedMessages.forEach { incoming ->
            if (existing.any { it.isLikelyDuplicateOf(incoming) }) {
                duplicates++
                return@forEach
            }
            val id = if (incoming.id in usedIds) {
                do {
                    nextId++
                } while (nextId in usedIds)
                nextId
            } else {
                incoming.id
            }
            usedIds += id
            existing += incoming.copy(id = id)
            imported++
        }

        if (imported > 0) {
            saveMessages(existing.sortedByDescending { it.timestamp }.take(1000))
        }
        return MessageImportResult(imported = imported, duplicates = duplicates)
    }

    fun importFromDeviceInbox(): Int {
        val existing = listMessages().associateBy { it.id }.toMutableMap()
        val activeSims = SimRepository.activeSims(context)
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            SimRepository.smsSubscriptionColumn()
        )

        return try {
            prefs.edit().putBoolean("last_import_attempted", true).apply()
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val subscriptionIndex = cursor.getColumnIndex(SimRepository.smsSubscriptionColumn())
                var imported = 0

                while (cursor.moveToNext() && existing.size < 1000) {
                    val id = cursor.getLong(idIndex)
                    if (existing.containsKey(id)) continue

                    val type = cursor.getInt(typeIndex)
                    val outgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                        type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                        type == Telephony.Sms.MESSAGE_TYPE_QUEUED
                    val address = cursor.getString(addressIndex).orEmpty()
                    val sender = if (outgoing) "To: $address" else address
                    val body = cursor.getString(bodyIndex).orEmpty()
                    val timestamp = cursor.getLong(dateIndex)
                    val subscriptionId = if (subscriptionIndex >= 0 && !cursor.isNull(subscriptionIndex)) {
                        cursor.getInt(subscriptionIndex)
                    } else {
                        null
                    }
                    val simFields = SimRepository.fieldsFromSmsProvider(subscriptionId, activeSims)
                    val importedMessage = SmsMessageRecord(
                        id = id,
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        blocked = false,
                        outgoing = outgoing,
                        archived = false,
                        deliveryStatus = null,
                        simSubscriptionId = simFields.subscriptionId,
                        simSlotIndex = simFields.slotIndex,
                        simDisplayName = simFields.displayName,
                        simCarrierName = simFields.carrierName
                    )
                    if (existing.values.any { it.isLikelyDuplicateOf(importedMessage) }) continue
                    existing[id] = importedMessage
                    imported++
                }
                saveMessages(existing.values.sortedByDescending { it.timestamp }.take(1000))
                imported
            } ?: 0
        } catch (_: SecurityException) {
            0
        } catch (_: IllegalArgumentException) {
            0
        }
    }

    private fun saveMessages(messages: List<SmsMessageRecord>) {
        val arr = JSONArray()
        messages.forEach { message ->
            arr.put(message.toJson())
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }

    private fun writeToSystemSmsProvider(
        message: SmsMessageRecord,
        outgoing: Boolean,
        adoptProviderId: Boolean
    ): SmsMessageRecord {
        if (Telephony.Sms.getDefaultSmsPackage(context) != context.packageName) return message

        val inserted = insertSystemSms(message, outgoing, includeSubscription = true)
            ?: insertSystemSms(message, outgoing, includeSubscription = false)
            ?: return message
        val providerId = inserted.lastPathSegment?.toLongOrNull()
        return if (adoptProviderId && providerId != null) {
            message.copy(id = providerId)
        } else {
            message
        }
    }

    private fun insertSystemSms(message: SmsMessageRecord, outgoing: Boolean, includeSubscription: Boolean): Uri? {
        val address = if (outgoing) message.sender.removePrefix("To: ").trim() else message.sender
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, message.body)
            put(Telephony.Sms.DATE, message.timestamp)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(
                Telephony.Sms.TYPE,
                if (outgoing) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX
            )
            if (includeSubscription) {
                message.simSubscriptionId?.let { put(SimRepository.smsSubscriptionColumn(), it) }
            }
        }
        val target = if (outgoing) Telephony.Sms.Sent.CONTENT_URI else Telephony.Sms.Inbox.CONTENT_URI
        return runCatching {
            context.contentResolver.insert(target, values)
        }.getOrNull()
    }

    private fun SmsMessageRecord.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("sender", sender)
            .put("body", body)
            .put("timestamp", timestamp)
            .put("blocked", blocked)
            .put("outgoing", outgoing)
            .put("archived", archived)
            .put("deliveryStatus", deliveryStatus.orEmpty())
            .put("simSubscriptionId", simSubscriptionId ?: JSONObject.NULL)
            .put("simSlotIndex", simSlotIndex ?: JSONObject.NULL)
            .put("simDisplayName", simDisplayName.orEmpty())
            .put("simCarrierName", simCarrierName.orEmpty())
            .put("autoArchiveFrozen", autoArchiveFrozen)
    }

    private fun SmsMessageRecord.isLikelyDuplicateOf(other: SmsMessageRecord): Boolean {
        return outgoing == other.outgoing &&
            sender == other.sender &&
            body == other.body &&
            abs(timestamp - other.timestamp) <= DUPLICATE_WINDOW_MILLIS
    }

    private fun JSONObject.optNullableInt(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }

    companion object {
        const val ACTION_MESSAGES_UPDATED = "ski.wischnew.shield.MESSAGES_UPDATED"
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val DUPLICATE_WINDOW_MILLIS = 10_000L

        fun notifyMessagesUpdated(context: Context) {
            context.sendBroadcast(Intent(ACTION_MESSAGES_UPDATED).setPackage(context.packageName))
        }

        fun messageFromJson(obj: JSONObject, blocked: Boolean? = null, archived: Boolean? = null): SmsMessageRecord {
            return SmsMessageRecord(
                id = obj.optLong("id", System.currentTimeMillis()),
                sender = obj.optString("sender", ""),
                body = obj.optString("body", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                blocked = blocked ?: obj.optBoolean("blocked", false),
                outgoing = obj.optBoolean("outgoing", false),
                archived = archived ?: obj.optBoolean("archived", false),
                deliveryStatus = obj.optString("deliveryStatus", "").takeIf { it.isNotBlank() },
                simSubscriptionId = obj.optNullableInt("simSubscriptionId"),
                simSlotIndex = obj.optNullableInt("simSlotIndex"),
                simDisplayName = obj.optString("simDisplayName", "").takeIf { it.isNotBlank() },
                simCarrierName = obj.optString("simCarrierName", "").takeIf { it.isNotBlank() },
                autoArchiveFrozen = obj.optBoolean("autoArchiveFrozen", false)
            )
        }
    }
}

private fun JSONObject.optNullableInt(name: String): Int? {
    return if (has(name) && !isNull(name)) optInt(name) else null
}

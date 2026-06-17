package ski.wischnew.shield.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import ski.wischnew.shield.rules.FilterEngine
import ski.wischnew.shield.rules.Rule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
                        systemMessageId = obj.optNullableLong("systemMessageId"),
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
                        autoArchiveFrozen = obj.optBoolean("autoArchiveFrozen", false),
                        blockOverride = obj.optBoolean("blockOverride", false)
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

    fun addReceivedMessage(message: SmsMessageRecord): SmsMessageRecord? {
        val existing = listMessages()
        if (existing.any { it.isLikelyDuplicateOf(message) }) return null
        val storedMessage = writeToSystemSmsProvider(message, outgoing = false, adoptProviderId = true)
        val updated = existing.toMutableList().apply { add(0, storedMessage) }.take(500)
        saveMessages(updated)
        return storedMessage
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
        val messages = listMessages()
        markSystemMessagesDeleted(messages.filter { it.id == id })
        saveMessages(messages.filterNot { it.id == id })
    }

    fun deleteMessages(ids: Set<Long>): Int {
        if (ids.isEmpty()) return 0
        val before = listMessages()
        markSystemMessagesDeleted(before.filter { it.id in ids })
        val updated = before.filterNot { it.id in ids }
        if (updated.size != before.size) saveMessages(updated)
        return before.size - updated.size
    }

    fun updateBlockedState(id: Long, blocked: Boolean): Boolean {
        var changed = false
        val updated = listMessages().map { message ->
            if (message.id == id && !message.outgoing && (message.blocked != blocked || message.archived)) {
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
            val autoArchiveFrozen = if (archived) false else message.autoArchiveFrozen
            if (message.id == id && (message.archived != archived || message.autoArchiveFrozen != autoArchiveFrozen)) {
                changed = true
                message.copy(
                    archived = archived,
                    autoArchiveFrozen = autoArchiveFrozen
                )
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
            val autoArchiveFrozen = if (archived) false else message.autoArchiveFrozen
            if (message.id in ids && (message.archived != archived || message.autoArchiveFrozen != autoArchiveFrozen)) {
                changed++
                message.copy(
                    archived = archived,
                    autoArchiveFrozen = autoArchiveFrozen
                )
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

    fun restoreMessageState(restoredMessage: SmsMessageRecord): Boolean {
        var changed = false
        val updated = listMessages().map { message ->
            if (message.id == restoredMessage.id) {
                changed = true
                restoredMessage
            } else {
                message
            }
        }
        if (changed) saveMessages(updated)
        return changed
    }

    fun returnMessagesToInbox(ids: Set<Long>, freezeAutoArchived: Boolean = false): Int {
        if (ids.isEmpty()) return 0
        var changed = 0
        val updated = listMessages().map { message ->
            if (message.id in ids && (message.blocked || message.archived)) {
                changed++
                message.copy(
                    blocked = false,
                    archived = false,
                    autoArchiveFrozen = message.autoArchiveFrozen || (freezeAutoArchived && message.archived) || message.blocked,
                    blockOverride = message.blockOverride || message.blocked
                )
            } else {
                message
            }
        }
        if (changed > 0) saveMessages(updated)
        return changed
    }

    fun updateDeliveryStatus(id: Long, status: String): Boolean {
        var changed = false
        var statusMessage: SmsMessageRecord? = null
        val updated = listMessages().map { message ->
            if (message.id == id) {
                val systemMessageId = message.systemMessageId ?: findSystemSmsProviderId(message)
                val updatedMessage = message.copy(
                    systemMessageId = systemMessageId,
                    deliveryStatus = status
                )
                if (updatedMessage != message) {
                    changed = true
                }
                statusMessage = updatedMessage
                updatedMessage
            } else {
                message
            }
        }
        if (changed) saveMessages(updated)
        statusMessage?.let { updateSystemSmsProviderStatus(it, status) }
        return changed
    }

    fun applyRulesToMessages(rules: List<Rule>, defaultRegion: String): Int {
        val engine = FilterEngine()
        var changed = 0
        val updated = listMessages().map { message ->
            if (message.outgoing) {
                if (message.blocked) {
                    changed++
                    message.copy(blocked = false, archived = false, blockOverride = false)
                } else {
                    message
                }
            } else {
                val shouldBlock = engine.shouldBlock(
                    sender = message.sender,
                    body = message.body,
                    rules = rules,
                    defaultRegion = defaultRegion
                )
                val shouldArchive = if (shouldBlock) false else message.archived
                if (message.blocked != shouldBlock || message.archived != shouldArchive) {
                    changed++
                    message.copy(
                        blocked = shouldBlock,
                        archived = shouldArchive,
                        blockOverride = if (shouldBlock) false else message.blockOverride
                    )
                } else {
                    message
                }
            }
        }
        if (changed > 0) saveMessages(updated)
        return changed
    }

    fun reblockOverriddenMessages(ids: Set<Long>, rules: List<Rule>, defaultRegion: String): Int {
        if (ids.isEmpty()) return 0
        val engine = FilterEngine()
        var changed = 0
        val updated = listMessages().map { message ->
            if (message.id in ids && !message.outgoing && message.blockOverride) {
                val shouldBlock = engine.shouldBlock(
                    sender = message.sender,
                    body = message.body,
                    rules = rules,
                    defaultRegion = defaultRegion
                )
                if (shouldBlock) {
                    changed++
                    message.copy(
                        blocked = true,
                        archived = false,
                        autoArchiveFrozen = false,
                        blockOverride = false
                    )
                } else {
                    message
                }
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
                    message.copy(blocked = false, archived = false, blockOverride = false)
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
        return blockedAutoDeleteCandidateIds(days, now).size
    }

    fun deleteBlockedOlderThan(days: Int, now: Long = System.currentTimeMillis()): Int {
        if (days <= 0) return 0
        val candidateIds = blockedAutoDeleteCandidateIds(days, now).toSet()
        if (candidateIds.isEmpty()) return 0
        val before = listMessages()
        val updated = before.filterNot { it.id in candidateIds }
        if (updated.size != before.size) saveMessages(updated)
        return before.size - updated.size
    }

    fun blockedAutoDeleteCandidateIds(days: Int, now: Long = System.currentTimeMillis()): List<Long> {
        if (days <= 0) return emptyList()
        val cutoffDate = Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .minusDays(days.toLong())
        return listMessages()
            .filter { message -> message.isBlockedAutoDeleteCandidate(cutoffDate) }
            .map { it.id }
    }

    fun blockedAutoDeleteDeferralCandidateIds(ids: Set<Long>, days: Int, now: Long = System.currentTimeMillis()): List<Long> {
        if (ids.isEmpty() || days <= 0) return emptyList()
        val cutoffDate = Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .minusDays(days.toLong())
        return listMessages()
            .filter { message -> message.id in ids && message.isBlockedAutoDeleteDeferralCandidate(cutoffDate) }
            .map { it.id }
    }

    private fun SmsMessageRecord.isBlockedAutoDeleteCandidate(cutoffDate: LocalDate): Boolean {
        if (!blocked || archived) return false
        val messageDate = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return !messageDate.isAfter(cutoffDate)
    }

    private fun SmsMessageRecord.isBlockedAutoDeleteDeferralCandidate(cutoffDate: LocalDate): Boolean {
        if (outgoing || (!blocked && !blockOverride)) return false
        val messageDate = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return !messageDate.isAfter(cutoffDate)
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
        val existingProviderIds = existing.values.mapNotNull { it.systemMessageId }.toMutableSet()
        val deletedProviderIds = deletedSystemMessageIds()
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
                var merged = 0

                while (cursor.moveToNext() && existing.size < 1000) {
                    val id = cursor.getLong(idIndex)
                    if (id in deletedProviderIds || id in existingProviderIds) continue

                    val type = cursor.getInt(typeIndex)
                    val outgoing = isOutgoingSystemSmsType(type)
                    val deliveryStatus = deliveryStatusForSystemType(type)

                    val current = existing[id]
                    if (current != null) {
                        if (current.systemMessageId == null || current.deliveryStatus == null) {
                            existing[id] = current.copy(
                                systemMessageId = current.systemMessageId ?: id,
                                deliveryStatus = current.deliveryStatus ?: deliveryStatus
                            )
                            existingProviderIds += id
                            merged++
                        }
                        continue
                    }

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
                        systemMessageId = id,
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        blocked = false,
                        outgoing = outgoing,
                        archived = false,
                        deliveryStatus = deliveryStatus,
                        simSubscriptionId = simFields.subscriptionId,
                        simSlotIndex = simFields.slotIndex,
                        simDisplayName = simFields.displayName,
                        simCarrierName = simFields.carrierName
                    )
                    val duplicate = existing.values.firstOrNull { it.isLikelyDuplicateOf(importedMessage) }
                    if (duplicate != null) {
                        if (duplicate.systemMessageId == null || duplicate.deliveryStatus == null) {
                            existing[duplicate.id] = duplicate.copy(
                                systemMessageId = duplicate.systemMessageId ?: id,
                                deliveryStatus = duplicate.deliveryStatus ?: deliveryStatus
                            )
                            existingProviderIds += id
                            merged++
                        }
                        continue
                    }
                    existing[id] = importedMessage
                    existingProviderIds += id
                    imported++
                }
                if (imported > 0 || merged > 0) {
                    saveMessages(existing.values.sortedByDescending { it.timestamp }.take(1000))
                }
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
        return when {
            providerId == null -> message
            adoptProviderId -> message.copy(id = providerId, systemMessageId = providerId)
            else -> message.copy(systemMessageId = providerId)
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
                systemTypeForMessage(message, outgoing)
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
            .put("systemMessageId", systemMessageId ?: JSONObject.NULL)
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
            .put("blockOverride", blockOverride)
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

    private fun JSONObject.optNullableLong(name: String): Long? {
        return if (has(name) && !isNull(name)) optLong(name) else null
    }

    private fun markSystemMessagesDeleted(messages: List<SmsMessageRecord>) {
        val ids = messages.mapNotNull { message ->
            message.systemMessageId ?: findSystemSmsProviderId(message)
        }.map { it.toString() }
        if (ids.isEmpty()) return

        val existing = prefs.getStringSet(DELETED_SYSTEM_MESSAGE_IDS_KEY, emptySet()).orEmpty()
        prefs.edit()
            .putStringSet(DELETED_SYSTEM_MESSAGE_IDS_KEY, existing + ids)
            .apply()
    }

    private fun deletedSystemMessageIds(): Set<Long> {
        return prefs.getStringSet(DELETED_SYSTEM_MESSAGE_IDS_KEY, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }

    private fun findSystemSmsProviderId(message: SmsMessageRecord): Long? {
        if (Telephony.Sms.getDefaultSmsPackage(context) != context.packageName) return null

        val address = if (message.outgoing) message.sender.removePrefix("To: ").trim() else message.sender
        val minDate = message.timestamp - DUPLICATE_WINDOW_MILLIS
        val maxDate = message.timestamp + DUPLICATE_WINDOW_MILLIS
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.TYPE
        )
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ? AND ${Telephony.Sms.DATE} BETWEEN ? AND ?",
                arrayOf(address, message.body, minDate.toString(), maxDate.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (cursor.moveToNext()) {
                    val type = cursor.getInt(typeIndex)
                    if (isOutgoingSystemSmsType(type) == message.outgoing) {
                        return@use cursor.getLong(idIndex)
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun updateSystemSmsProviderStatus(message: SmsMessageRecord, status: String) {
        if (Telephony.Sms.getDefaultSmsPackage(context) != context.packageName) return
        if (!message.outgoing) return

        val providerId = message.systemMessageId ?: findSystemSmsProviderId(message) ?: return
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, systemTypeForDeliveryStatus(status))
        }
        runCatching {
            context.contentResolver.update(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, providerId),
                values,
                null,
                null
            )
        }
    }

    private fun systemTypeForMessage(message: SmsMessageRecord, outgoing: Boolean): Int {
        return if (!outgoing) {
            Telephony.Sms.MESSAGE_TYPE_INBOX
        } else {
            systemTypeForDeliveryStatus(message.deliveryStatus)
        }
    }

    private fun systemTypeForDeliveryStatus(status: String?): Int {
        return when (status) {
            SmsStatusReceiver.DELIVERY_STATUS_SEND_FAILED -> Telephony.Sms.MESSAGE_TYPE_FAILED
            else -> Telephony.Sms.MESSAGE_TYPE_SENT
        }
    }

    private fun isOutgoingSystemSmsType(type: Int): Boolean {
        return type == Telephony.Sms.MESSAGE_TYPE_SENT ||
            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
            type == Telephony.Sms.MESSAGE_TYPE_QUEUED ||
            type == Telephony.Sms.MESSAGE_TYPE_FAILED
    }

    private fun deliveryStatusForSystemType(type: Int): String? {
        return when (type) {
            Telephony.Sms.MESSAGE_TYPE_SENT -> SmsStatusReceiver.DELIVERY_STATUS_SENT
            Telephony.Sms.MESSAGE_TYPE_OUTBOX,
            Telephony.Sms.MESSAGE_TYPE_QUEUED -> SmsStatusReceiver.DELIVERY_STATUS_QUEUED
            Telephony.Sms.MESSAGE_TYPE_FAILED -> SmsStatusReceiver.DELIVERY_STATUS_SEND_FAILED
            else -> null
        }
    }

    companion object {
        const val ACTION_MESSAGES_UPDATED = "ski.wischnew.shield.MESSAGES_UPDATED"
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val DUPLICATE_WINDOW_MILLIS = 10_000L
        private const val DELETED_SYSTEM_MESSAGE_IDS_KEY = "deleted_system_message_ids"

        fun notifyMessagesUpdated(context: Context) {
            context.sendBroadcast(Intent(ACTION_MESSAGES_UPDATED).setPackage(context.packageName))
        }

        fun messageFromJson(obj: JSONObject, blocked: Boolean? = null, archived: Boolean? = null): SmsMessageRecord {
            return SmsMessageRecord(
                id = obj.optLong("id", System.currentTimeMillis()),
                systemMessageId = obj.optNullableLong("systemMessageId"),
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
                autoArchiveFrozen = obj.optBoolean("autoArchiveFrozen", false),
                blockOverride = obj.optBoolean("blockOverride", false)
            )
        }
    }
}

private fun JSONObject.optNullableInt(name: String): Int? {
    return if (has(name) && !isNull(name)) optInt(name) else null
}

private fun JSONObject.optNullableLong(name: String): Long? {
    return if (has(name) && !isNull(name)) optLong(name) else null
}

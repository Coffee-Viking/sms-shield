package ski.wischnew.shield.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ski.wischnew.shield.rules.FilterEngine
import ski.wischnew.shield.rules.RuleStore
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        ) {
            return
        }

        val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (smsMessages.isEmpty()) return

        val sender = smsMessages.firstOrNull()?.displayOriginatingAddress.orEmpty()
        val body = smsMessages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val simFields = SimRepository.fieldsFromIntent(context, intent)

        val ruleStore = RuleStore(context)
        val engine = FilterEngine()
        val shouldBlock = engine.shouldBlock(
            sender = sender,
            body = body,
            rules = ruleStore.getRules(),
            defaultRegion = Locale.getDefault().country.ifBlank { "US" }
        )

        val message = SmsMessageRecord(
            sender = sender,
            body = body,
            timestamp = smsMessages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis(),
            blocked = shouldBlock,
            simSubscriptionId = simFields.subscriptionId,
            simSlotIndex = simFields.slotIndex,
            simDisplayName = simFields.displayName,
            simCarrierName = simFields.carrierName
        )
        val added = InboxStore(context).addMessage(message)
        if (added) {
            InboxStore.notifyMessagesUpdated(context)
        }

        if (shouldBlock) {
            abortBroadcast()
            return
        }

        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            SmsNotifications.showIncomingMessage(context, message)
        }
    }
}

package ski.wischnew.shield.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(SmsSender.EXTRA_MESSAGE_ID, -1L)
        if (messageId <= 0L) return

        val status = when (intent.action) {
            SmsSender.ACTION_SMS_SENT -> if (resultCode == Activity.RESULT_OK) {
                DELIVERY_STATUS_SENT
            } else {
                DELIVERY_STATUS_SEND_FAILED
            }
            SmsSender.ACTION_SMS_DELIVERED -> if (resultCode == Activity.RESULT_OK) {
                DELIVERY_STATUS_DELIVERED
            } else {
                DELIVERY_STATUS_DELIVERY_FAILED
            }
            else -> return
        }

        InboxStore(context).updateDeliveryStatus(messageId, status)
        context.sendBroadcast(
            Intent(ACTION_STATUS_UPDATED)
                .setPackage(context.packageName)
                .putExtra(SmsSender.EXTRA_MESSAGE_ID, messageId)
        )
    }

    companion object {
        const val ACTION_STATUS_UPDATED = "ski.wischnew.shield.SMS_STATUS_UPDATED"
        const val DELIVERY_STATUS_SENT = "Sent"
        const val DELIVERY_STATUS_QUEUED = "Queued"
        const val DELIVERY_STATUS_DELIVERED = "Delivered"
        const val DELIVERY_STATUS_SEND_FAILED = "Send failed"
        const val DELIVERY_STATUS_DELIVERY_FAILED = "Delivery failed"
    }
}

package ski.wischnew.shield.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager

class SmsSender {
    fun send(
        context: Context,
        recipient: String,
        body: String,
        requestDeliveryReport: Boolean,
        messageId: Long,
        subscriptionId: Int?
    ) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val requestCode = messageId.hashCode()
        val sentIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(ACTION_SMS_SENT)
                .setPackage(context.packageName)
                .putExtra(EXTRA_MESSAGE_ID, messageId),
            flags
        )
        val deliveryIntent = if (requestDeliveryReport) {
            PendingIntent.getBroadcast(
                context,
                requestCode + 1,
                Intent(ACTION_SMS_DELIVERED)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_MESSAGE_ID, messageId),
                flags
            )
        } else {
            null
        }

        val smsManager = if (subscriptionId != null) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getDefault()
        }
        val parts = smsManager.divideMessage(body)
        if (parts.size <= 1) {
            smsManager.sendTextMessage(recipient, null, body, sentIntent, deliveryIntent)
        } else {
            smsManager.sendMultipartTextMessage(
                recipient,
                null,
                parts,
                ArrayList(parts.map { sentIntent }),
                ArrayList(parts.map { deliveryIntent })
            )
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "ski.wischnew.shield.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "ski.wischnew.shield.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "message_id"
    }
}

package ski.wischnew.shield.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ski.wischnew.shield.MainActivity
import ski.wischnew.shield.contacts.ContactLookup

object SmsNotifications {
    private const val CHANNEL_ID = "sms_shield_incoming"
    private const val CHANNEL_NAME = "Incoming SMS"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for received incoming SMS"
        }

        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun showIncomingMessage(context: Context, message: SmsMessageRecord) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_MESSAGE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_MESSAGE_ID, message.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            message.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sender = ContactLookup.resolveSender(context, message.sender).primary
        val otpCode = OtpDetector.findOtp(message.body)
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(sender)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        if (otpCode != null) {
            notificationBuilder.addAction(
                android.R.drawable.ic_menu_save,
                "Copy OTP",
                OtpCopyReceiver.pendingIntent(context, otpCode, message.id)
            )
        }

        NotificationManagerCompat.from(context).notify(message.id.hashCode(), notificationBuilder.build())
    }
}

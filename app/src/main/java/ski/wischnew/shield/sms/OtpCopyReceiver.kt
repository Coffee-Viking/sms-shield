package ski.wischnew.shield.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

class OtpCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        if (code.isBlank()) return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OTP", code))
        Toast.makeText(context, "OTP copied", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_CODE = "ski.wischnew.shield.extra.OTP_CODE"

        fun pendingIntent(context: Context, code: String, messageId: Long): PendingIntent {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val intent = Intent(context, OtpCopyReceiver::class.java).apply {
                putExtra(EXTRA_CODE, code)
            }
            return PendingIntent.getBroadcast(context, messageId.hashCode() xor OTP_REQUEST_OFFSET, intent, flags)
        }

        private const val OTP_REQUEST_OFFSET = 0x4F5450
    }
}

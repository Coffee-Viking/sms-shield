package ski.wischnew.shield.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Placeholder service so the app satisfies default SMS role requirements.
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

package ski.wischnew.shield.settings

import android.content.Context
import android.text.format.DateFormat
import org.json.JSONObject

enum class ThemeMode {
    LIGHT,
    DARK,
    OLED
}

enum class SimSendMode {
    ASK,
    REPLY_WHERE_RECEIVED,
    ALWAYS
}

class AppSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("sms_shield_settings", Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode {
        return runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name) }
            .getOrDefault(ThemeMode.DARK)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun getAccentColor(): Int {
        if (prefs.contains(ACCENT_COLOR_ARGB_KEY)) {
            return sanitizeAccent(prefs.getInt(ACCENT_COLOR_ARGB_KEY, DEFAULT_ACCENT_COLOR))
        }

        val migrated = if (prefs.contains(LEGACY_ACCENT_COLOR_KEY)) {
            migrateLegacyAccentColor(prefs.getLong(LEGACY_ACCENT_COLOR_KEY, DEFAULT_ACCENT_COLOR.toUnsignedLong()))
        } else {
            DEFAULT_ACCENT_COLOR
        }
        setAccentColor(migrated)
        return migrated
    }

    fun setAccentColor(color: Int) {
        prefs.edit()
            .putInt(ACCENT_COLOR_ARGB_KEY, sanitizeAccent(color))
            .remove(LEGACY_ACCENT_COLOR_KEY)
            .apply()
    }

    fun getDeliveryReportsEnabled(): Boolean {
        return prefs.getBoolean("delivery_reports_enabled", false)
    }

    fun setDeliveryReportsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("delivery_reports_enabled", enabled).apply()
    }

    fun getUse24HourTime(): Boolean {
        return if (prefs.contains(USE_24_HOUR_TIME_KEY)) {
            prefs.getBoolean(USE_24_HOUR_TIME_KEY, false)
        } else {
            DateFormat.is24HourFormat(appContext)
        }
    }

    fun setUse24HourTime(enabled: Boolean) {
        prefs.edit().putBoolean(USE_24_HOUR_TIME_KEY, enabled).apply()
    }

    fun getBatteryPromptAcknowledged(): Boolean {
        return prefs.getBoolean("battery_prompt_acknowledged", false)
    }

    fun setBatteryPromptAcknowledged(acknowledged: Boolean) {
        prefs.edit().putBoolean("battery_prompt_acknowledged", acknowledged).apply()
    }

    fun getSimSendMode(): SimSendMode {
        return runCatching {
            SimSendMode.valueOf(prefs.getString("sim_send_mode", SimSendMode.ASK.name) ?: SimSendMode.ASK.name)
        }.getOrDefault(SimSendMode.ASK)
    }

    fun setSimSendMode(mode: SimSendMode) {
        prefs.edit().putString("sim_send_mode", mode.name).apply()
    }

    fun getDefaultSimSubscriptionId(): Int? {
        return prefs.getInt("default_sim_subscription_id", INVALID_SUBSCRIPTION_ID).takeIf { it != INVALID_SUBSCRIPTION_ID }
    }

    fun setDefaultSimSubscriptionId(subscriptionId: Int?) {
        prefs.edit().apply {
            if (subscriptionId == null) {
                remove("default_sim_subscription_id")
            } else {
                putInt("default_sim_subscription_id", subscriptionId)
            }
        }.apply()
    }

    fun getKnownSimSignature(): String {
        return prefs.getString("known_sim_signature", "").orEmpty()
    }

    fun setKnownSimSignature(signature: String) {
        prefs.edit().putString("known_sim_signature", signature).apply()
    }

    fun getAutoArchiveDays(): Int? {
        return prefs.getInt("auto_archive_days", 0).takeIf { it > 0 }
    }

    fun setAutoArchiveDays(days: Int?) {
        prefs.edit().putInt("auto_archive_days", days?.coerceAtLeast(0) ?: 0).apply()
    }

    fun getAutoDeleteBlockedDays(): Int? {
        return prefs.getInt("auto_delete_blocked_days", 0).takeIf { it > 0 }
    }

    fun setAutoDeleteBlockedDays(days: Int?) {
        prefs.edit().putInt("auto_delete_blocked_days", days?.coerceAtLeast(0) ?: 0).apply()
    }

    fun getWarnBeforeBlockedAutoDelete(): Boolean {
        return prefs.getBoolean("warn_before_blocked_auto_delete", true)
    }

    fun setWarnBeforeBlockedAutoDelete(warn: Boolean) {
        prefs.edit().putBoolean("warn_before_blocked_auto_delete", warn).apply()
    }

    fun getBlockedAutoDeleteSnoozedUntil(): Long {
        return prefs.getLong(BLOCKED_AUTO_DELETE_SNOOZED_UNTIL_KEY, 0L)
    }

    fun setBlockedAutoDeleteSnoozedUntil(timestamp: Long) {
        prefs.edit().putLong(BLOCKED_AUTO_DELETE_SNOOZED_UNTIL_KEY, timestamp.coerceAtLeast(0L)).apply()
    }

    fun clearBlockedAutoDeleteSnooze() {
        prefs.edit().remove(BLOCKED_AUTO_DELETE_SNOOZED_UNTIL_KEY).apply()
    }

    fun getChatModeEnabled(): Boolean {
        return prefs.getBoolean("chat_mode_enabled", true)
    }

    fun setChatModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("chat_mode_enabled", enabled).apply()
    }

    fun getConversationSplitHours(): Int? {
        return prefs.getInt("conversation_split_hours", DEFAULT_CONVERSATION_SPLIT_HOURS).takeIf { it > 0 }
    }

    fun setConversationSplitHours(hours: Int?) {
        prefs.edit().putInt("conversation_split_hours", hours?.coerceAtLeast(0) ?: 0).apply()
    }

    fun exportSettings(): JSONObject {
        return JSONObject()
            .put("themeMode", getThemeMode().name)
            .put("accentColor", getAccentColor())
            .put("deliveryReportsEnabled", getDeliveryReportsEnabled())
            .put("use24HourTime", getUse24HourTime())
            .put("simSendMode", getSimSendMode().name)
            .put("defaultSimSubscriptionId", getDefaultSimSubscriptionId() ?: JSONObject.NULL)
            .put("autoArchiveDays", getAutoArchiveDays() ?: JSONObject.NULL)
            .put("autoDeleteBlockedDays", getAutoDeleteBlockedDays() ?: JSONObject.NULL)
            .put("warnBeforeBlockedAutoDelete", getWarnBeforeBlockedAutoDelete())
            .put("chatModeEnabled", getChatModeEnabled())
            .put("conversationSplitHours", getConversationSplitHours() ?: JSONObject.NULL)
    }

    fun importSettings(obj: JSONObject) {
        val editor = prefs.edit()
        obj.optString("themeMode", "").takeIf { it.isNotBlank() }?.let { value ->
            runCatching { ThemeMode.valueOf(value) }.getOrNull()?.let { editor.putString("theme_mode", it.name) }
        }
        if (obj.has("accentColor") && !obj.isNull("accentColor")) {
            editor.putInt(ACCENT_COLOR_ARGB_KEY, sanitizeAccent(obj.optInt("accentColor", DEFAULT_ACCENT_COLOR)))
            editor.remove(LEGACY_ACCENT_COLOR_KEY)
        }
        if (obj.has("deliveryReportsEnabled")) {
            editor.putBoolean("delivery_reports_enabled", obj.optBoolean("deliveryReportsEnabled", false))
        }
        if (obj.has("use24HourTime")) {
            editor.putBoolean(USE_24_HOUR_TIME_KEY, obj.optBoolean("use24HourTime", false))
        }
        obj.optString("simSendMode", "").takeIf { it.isNotBlank() }?.let { value ->
            runCatching { SimSendMode.valueOf(value) }.getOrNull()?.let { editor.putString("sim_send_mode", it.name) }
        }
        if (obj.has("defaultSimSubscriptionId")) {
            if (obj.isNull("defaultSimSubscriptionId")) {
                editor.remove("default_sim_subscription_id")
            } else {
                editor.putInt("default_sim_subscription_id", obj.optInt("defaultSimSubscriptionId", INVALID_SUBSCRIPTION_ID))
            }
        }
        if (obj.has("autoArchiveDays")) {
            editor.putInt("auto_archive_days", if (obj.isNull("autoArchiveDays")) 0 else obj.optInt("autoArchiveDays", 0).coerceAtLeast(0))
        }
        if (obj.has("autoDeleteBlockedDays")) {
            editor.putInt("auto_delete_blocked_days", if (obj.isNull("autoDeleteBlockedDays")) 0 else obj.optInt("autoDeleteBlockedDays", 0).coerceAtLeast(0))
        }
        if (obj.has("warnBeforeBlockedAutoDelete")) {
            editor.putBoolean("warn_before_blocked_auto_delete", obj.optBoolean("warnBeforeBlockedAutoDelete", true))
        }
        if (obj.has("chatModeEnabled")) {
            editor.putBoolean("chat_mode_enabled", obj.optBoolean("chatModeEnabled", true))
        }
        if (obj.has("conversationSplitHours")) {
            editor.putInt("conversation_split_hours", if (obj.isNull("conversationSplitHours")) 0 else obj.optInt("conversationSplitHours", DEFAULT_CONVERSATION_SPLIT_HOURS).coerceAtLeast(0))
        }
        editor.apply()
    }

    private fun migrateLegacyAccentColor(value: Long): Int {
        val directArgb = value.toInt()
        if (directArgb in KNOWN_ACCENTS) return directArgb

        val packedArgb = (value ushr 32).toInt()
        if (packedArgb in KNOWN_ACCENTS) return packedArgb

        return DEFAULT_ACCENT_COLOR
    }

    private fun sanitizeAccent(color: Int): Int {
        return if (color in KNOWN_ACCENTS) color else DEFAULT_ACCENT_COLOR
    }

    private fun Int.toUnsignedLong(): Long = toLong() and 0xFFFFFFFFL

    companion object {
        private const val LEGACY_ACCENT_COLOR_KEY = "accent_color"
        private const val ACCENT_COLOR_ARGB_KEY = "accent_color_argb"
        private const val USE_24_HOUR_TIME_KEY = "use_24_hour_time"
        private const val BLOCKED_AUTO_DELETE_SNOOZED_UNTIL_KEY = "blocked_auto_delete_snoozed_until"
        private const val INVALID_SUBSCRIPTION_ID = -1
        private const val DEFAULT_CONVERSATION_SPLIT_HOURS = 24
        private val DEFAULT_ACCENT_COLOR = 0xFF179BFF.toInt()
        private val KNOWN_ACCENTS = setOf(
            0xFF179BFF.toInt(),
            0xFF00C853.toInt(),
            0xFFFF9100.toInt(),
            0xFFFF5252.toInt(),
            0xFF8A95A3.toInt()
        )
    }
}

package ski.wischnew.shield.sms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

object SimRepository {
    private const val SMS_SUBSCRIPTION_COLUMN = "sub_id"

    fun activeSims(context: Context): List<SimInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        if (isAirplaneModeOn(context)) {
            return emptyList()
        }
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return try {
            @Suppress("MissingPermission")
            subscriptionManager.activeSubscriptionInfoList.orEmpty()
                .sortedWith(compareBy({ it.simSlotIndex }, { it.subscriptionId }))
                .map { info ->
                    val slotIndex = info.simSlotIndex.coerceAtLeast(0)
                    SimInfo(
                        subscriptionId = info.subscriptionId,
                        slotIndex = slotIndex,
                        displayName = info.displayName?.toString().orEmpty().ifBlank { "SIM ${slotIndex + 1}" },
                        carrierName = info.carrierName?.toString()?.ifBlank { null }
                    )
                }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        }.getOrDefault(false)
    }

    fun signature(activeSims: List<SimInfo>): String {
        return "v2:" + activeSims
            .sortedWith(compareBy({ it.slotIndex }, { it.subscriptionId }))
            .joinToString(separator = "|") { sim ->
                "${sim.subscriptionId}:${sim.slotIndex}"
            }
    }

    fun infoFromSubscriptionId(subscriptionId: Int?, activeSims: List<SimInfo>): SimInfo? {
        if (subscriptionId == null || subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return null
        return activeSims.firstOrNull { it.subscriptionId == subscriptionId }
    }

    fun subscriptionIdFromIntent(intent: Intent): Int? {
        val keys = listOf(
            "subscription",
            "subscription_id",
            "simSubscriptionId",
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "android.telephony.extra.SUBSCRIPTION_ID"
        )
        return keys.firstNotNullOfOrNull { key ->
            if (!intent.hasExtra(key)) {
                null
            } else {
                intent.getIntExtra(key, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                    .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            }
        }
    }

    fun slotIndexFromIntent(intent: Intent): Int? {
        val keys = listOf(
            "slot",
            "slot_id",
            "simSlot",
            "android.telephony.extra.SLOT_INDEX",
            "android.telephony.extra.SLOT_ID"
        )
        return keys.firstNotNullOfOrNull { key ->
            if (!intent.hasExtra(key)) null else intent.getIntExtra(key, -1).takeIf { it >= 0 }
        }
    }

    fun copyFieldsFrom(sim: SimInfo?): SimFields {
        return SimFields(
            subscriptionId = sim?.subscriptionId,
            slotIndex = sim?.slotIndex,
            displayName = sim?.displayName,
            carrierName = sim?.carrierName
        )
    }

    fun fieldsFromIntent(context: Context, intent: Intent): SimFields {
        val active = activeSims(context)
        val subscriptionId = subscriptionIdFromIntent(intent)
        val slotIndex = slotIndexFromIntent(intent)
        val activeInfo = infoFromSubscriptionId(subscriptionId, active)
            ?: slotIndex?.let { slot -> active.firstOrNull { it.slotIndex == slot } }

        return if (activeInfo != null) {
            copyFieldsFrom(activeInfo)
        } else {
            SimFields(subscriptionId = subscriptionId, slotIndex = slotIndex)
        }
    }

    fun fieldsFromSmsProvider(subscriptionId: Int?, activeSims: List<SimInfo>): SimFields {
        val activeInfo = infoFromSubscriptionId(subscriptionId, activeSims)
        return if (activeInfo != null) {
            copyFieldsFrom(activeInfo)
        } else {
            SimFields(subscriptionId = subscriptionId)
        }
    }

    fun smsSubscriptionColumn(): String = SMS_SUBSCRIPTION_COLUMN
}

data class SimFields(
    val subscriptionId: Int? = null,
    val slotIndex: Int? = null,
    val displayName: String? = null,
    val carrierName: String? = null
)

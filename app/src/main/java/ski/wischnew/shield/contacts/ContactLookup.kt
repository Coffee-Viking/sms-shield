package ski.wischnew.shield.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactDisplay(
    val primary: String,
    val secondary: String? = null
)

object ContactLookup {
    private const val OUTGOING_PREFIX = "To:"
    private val cache = mutableMapOf<String, ContactDisplay>()

    fun resolveSender(context: Context, sender: String): ContactDisplay {
        val original = sender.ifBlank { return ContactDisplay("Unknown") }
        val isOutgoing = original.startsWith(OUTGOING_PREFIX)
        val address = if (isOutgoing) {
            original.removePrefix(OUTGOING_PREFIX).trim()
        } else {
            original.trim()
        }

        if (address.isBlank()) return ContactDisplay(original)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactDisplay(original)
        }

        val cacheKey = "${if (isOutgoing) "out" else "in"}:$address"
        synchronized(cache) {
            cache[cacheKey]?.let { return it }
        }

        val name = findDisplayName(context, address)
        val display = if (name.isNullOrBlank()) {
            ContactDisplay(original)
        } else {
            ContactDisplay(
                primary = if (isOutgoing) "$OUTGOING_PREFIX $name" else name,
                secondary = address
            )
        }
        synchronized(cache) {
            cache[cacheKey] = display
        }
        return display
    }

    private fun findDisplayName(context: Context, number: String): String? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex < 0) null else cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

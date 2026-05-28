package ski.wischnew.shield

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ski.wischnew.shield.contacts.ContactDisplay
import ski.wischnew.shield.contacts.ContactLookup
import ski.wischnew.shield.rules.Rule
import ski.wischnew.shield.rules.RuleAction
import ski.wischnew.shield.rules.RuleStore
import ski.wischnew.shield.rules.RuleType
import ski.wischnew.shield.settings.AppSettingsStore
import ski.wischnew.shield.settings.SimSendMode
import ski.wischnew.shield.settings.ThemeMode
import ski.wischnew.shield.sms.InboxStore
import ski.wischnew.shield.sms.MessageImportResult
import ski.wischnew.shield.sms.OtpDetector
import ski.wischnew.shield.sms.SimInfo
import ski.wischnew.shield.sms.SimRepository
import ski.wischnew.shield.sms.SmsMessageRecord
import ski.wischnew.shield.sms.SmsSender
import ski.wischnew.shield.sms.SmsNotifications
import ski.wischnew.shield.sms.SmsStatusReceiver
import ski.wischnew.shield.ui.theme.SmsShieldTheme
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val requestRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    private val requestSmsPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        InboxStore.notifyMessagesUpdated(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SmsNotifications.ensureChannel(this)
        maybeRequestDefaultSmsRole()
        val settingsStore = AppSettingsStore(this)
        setContent {
            var themeMode by remember { mutableStateOf(settingsStore.getThemeMode()) }
            var accentColor by remember { mutableStateOf(Color(settingsStore.getAccentColor())) }
            var deliveryReportsEnabled by remember { mutableStateOf(settingsStore.getDeliveryReportsEnabled()) }
            var use24HourTime by remember { mutableStateOf(settingsStore.getUse24HourTime()) }
            var simSendMode by remember { mutableStateOf(settingsStore.getSimSendMode()) }
            var defaultSimSubscriptionId by remember { mutableStateOf(settingsStore.getDefaultSimSubscriptionId()) }
            var autoArchiveDays by remember { mutableStateOf(settingsStore.getAutoArchiveDays()) }
            var autoDeleteBlockedDays by remember { mutableStateOf(settingsStore.getAutoDeleteBlockedDays()) }
            var warnBeforeBlockedAutoDelete by remember { mutableStateOf(settingsStore.getWarnBeforeBlockedAutoDelete()) }
            var conversationSplitHours by remember { mutableStateOf(settingsStore.getConversationSplitHours()) }
            SmsShieldTheme(themeMode = themeMode, accentColor = accentColor) {
                SmsShieldApp(
                    ruleStore = RuleStore(this),
                    inboxStore = InboxStore(this),
                    themeMode = themeMode,
                    accentColor = accentColor,
                    deliveryReportsEnabled = deliveryReportsEnabled,
                    use24HourTime = use24HourTime,
                    simSendMode = simSendMode,
                    defaultSimSubscriptionId = defaultSimSubscriptionId,
                    autoArchiveDays = autoArchiveDays,
                    autoDeleteBlockedDays = autoDeleteBlockedDays,
                    warnBeforeBlockedAutoDelete = warnBeforeBlockedAutoDelete,
                    conversationSplitHours = conversationSplitHours,
                    onThemeModeChange = {
                        themeMode = it
                        settingsStore.setThemeMode(it)
                    },
                    onAccentColorChange = {
                        accentColor = it
                        settingsStore.setAccentColor(it.toArgb())
                    },
                    onDeliveryReportsChange = {
                        deliveryReportsEnabled = it
                        settingsStore.setDeliveryReportsEnabled(it)
                    },
                    onUse24HourTimeChange = {
                        use24HourTime = it
                        settingsStore.setUse24HourTime(it)
                    },
                    onSimSendModeChange = {
                        simSendMode = it
                        settingsStore.setSimSendMode(it)
                    },
                    onDefaultSimSubscriptionIdChange = {
                        defaultSimSubscriptionId = it
                        settingsStore.setDefaultSimSubscriptionId(it)
                    },
                    onAutoArchiveDaysChange = {
                        autoArchiveDays = it
                        settingsStore.setAutoArchiveDays(it)
                    },
                    onAutoDeleteBlockedDaysChange = {
                        autoDeleteBlockedDays = it
                        settingsStore.setAutoDeleteBlockedDays(it)
                    },
                    onWarnBeforeBlockedAutoDeleteChange = {
                        warnBeforeBlockedAutoDelete = it
                        settingsStore.setWarnBeforeBlockedAutoDelete(it)
                    },
                    onConversationSplitHoursChange = {
                        conversationSplitHours = it
                        settingsStore.setConversationSplitHours(it)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
            if (isDefaultSmsApp()) {
                maybeRequestSmsPermissions()
                lifecycleScope.launch(Dispatchers.IO) {
                    InboxStore(this@MainActivity).importFromDeviceInbox()
                    InboxStore.notifyMessagesUpdated(this@MainActivity)
                }
            }
        }

    private fun maybeRequestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                requestRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
        } else if (Telephony.Sms.getDefaultSmsPackage(this) != packageName) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            }
            requestRoleLauncher.launch(intent)
        }
    }

    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(this) == packageName
        }
    }

    private fun maybeRequestSmsPermissions() {
        val permissions = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestSmsPermissionsLauncher.launch(missing.toTypedArray())
        }
    }
}

private enum class Screen {
    MAIN,
    BLOCKED,
    ARCHIVE,
    BLOCK_ALLOW,
    SETTINGS
}

private fun Screen.supportsSearch(): Boolean {
    return this == Screen.MAIN || this == Screen.ARCHIVE
}

private enum class BulkMessageAction {
    ARCHIVE,
    FREEZE,
    RETURN_TO_INBOX,
    DELETE
}

private data class CountryOption(val code: String, val name: String, val callingCode: String)

private data class BackupSelection(
    val settings: Boolean = true,
    val rules: Boolean = true,
    val inbox: Boolean = true,
    val blocked: Boolean = true,
    val archive: Boolean = true
) {
    val allSelected: Boolean
        get() = settings && rules && inbox && blocked && archive

    val anySelected: Boolean
        get() = settings || rules || inbox || blocked || archive

    fun flippedAll(): BackupSelection {
        val next = !allSelected
        return BackupSelection(
            settings = next,
            rules = next,
            inbox = next,
            blocked = next,
            archive = next
        )
    }
}

private data class RuleConflict(
    val imported: Rule,
    val existing: Rule
)

private data class PendingMessageRuleConflict(
    val proposed: Rule,
    val existing: Rule
)

private data class PendingEditorRuleConflict(
    val proposed: Rule,
    val existing: Rule,
    val wasEditing: Boolean
)

private data class ParsedBackup(
    val schemaVersion: Int,
    val appVersionCode: Long?,
    val appVersionName: String?,
    val settings: JSONObject?,
    val rules: List<Rule>,
    val messages: List<SmsMessageRecord>
)

private data class PendingBackupImport(
    val backup: ParsedBackup,
    val conflicts: List<RuleConflict>
)

private data class BackupImportResult(
    val settingsImported: Boolean,
    val rulesImported: Int,
    val ruleDuplicates: Int,
    val ruleConflictsSkipped: Int,
    val ruleConflictsReplaced: Int,
    val messageResult: MessageImportResult
)

private data class ConversationThread(
    val key: String,
    val messages: List<SmsMessageRecord>
) {
    val latest: SmsMessageRecord
        get() = messages.maxBy { it.timestamp }

    val sortedMessages: List<SmsMessageRecord>
        get() = messages.sortedBy { it.timestamp }

    val ids: Set<Long>
        get() = messages.map { it.id }.toSet()
}

private data class UiColors(
    val background: Color,
    val topBar: Color,
    val panel: Color,
    val muted: Color,
    val divider: Color
)

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
private const val BACKUP_SCHEMA_VERSION = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsShieldApp(
    ruleStore: RuleStore,
    inboxStore: InboxStore,
    themeMode: ThemeMode,
    accentColor: Color,
    deliveryReportsEnabled: Boolean,
    use24HourTime: Boolean,
    simSendMode: SimSendMode,
    defaultSimSubscriptionId: Int?,
    autoArchiveDays: Int?,
    autoDeleteBlockedDays: Int?,
    warnBeforeBlockedAutoDelete: Boolean,
    conversationSplitHours: Int?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentColorChange: (Color) -> Unit,
    onDeliveryReportsChange: (Boolean) -> Unit,
    onUse24HourTimeChange: (Boolean) -> Unit,
    onSimSendModeChange: (SimSendMode) -> Unit,
    onDefaultSimSubscriptionIdChange: (Int?) -> Unit,
    onAutoArchiveDaysChange: (Int?) -> Unit,
    onAutoDeleteBlockedDaysChange: (Int?) -> Unit,
    onWarnBeforeBlockedAutoDeleteChange: (Boolean) -> Unit,
    onConversationSplitHoursChange: (Int?) -> Unit
) {
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var showComposeDialog by remember { mutableStateOf(false) }
    var inboxVersion by remember { mutableIntStateOf(0) }
    var composeError by remember { mutableStateOf<String?>(null) }
    var composeInitialRecipient by remember { mutableStateOf("") }
    var composeInitialBody by remember { mutableStateOf("") }
    var composeSourceMessage by remember { mutableStateOf<SmsMessageRecord?>(null) }
    var selectedMessage by remember { mutableStateOf<SmsMessageRecord?>(null) }
    var selectedConversationKey by remember { mutableStateOf<String?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var restoreSearchAfterDetail by remember { mutableStateOf(false) }
    var pendingRetroactiveBlockRule by remember { mutableStateOf<Rule?>(null) }
    var messages by remember { mutableStateOf<List<SmsMessageRecord>>(emptyList()) }
    var messagesLoaded by remember { mutableStateOf(false) }
    var mainSelectionActive by remember { mutableStateOf(false) }
    var showBatteryOptimizationPrompt by remember { mutableStateOf(false) }
    var showSimChangedPrompt by remember { mutableStateOf(false) }
    var showBlockedAutoDeletePrompt by remember { mutableStateOf(false) }
    var blockedAutoDeleteCandidateCount by remember { mutableIntStateOf(0) }
    var blockedAutoDeletePromptShown by remember { mutableStateOf(false) }
    var pendingMessageRuleConflict by remember { mutableStateOf<PendingMessageRuleConflict?>(null) }
    var backupSelection by remember { mutableStateOf(BackupSelection()) }
    var backupNotice by remember { mutableStateOf<String?>(null) }
    var backupDialogNotice by remember { mutableStateOf<String?>(null) }
    var pendingBackupImport by remember { mutableStateOf<PendingBackupImport?>(null) }
    var pendingNewerBackupImport by remember { mutableStateOf<ParsedBackup?>(null) }
    var cleanupApplyVersion by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val appSettingsStore = remember { AppSettingsStore(context) }
    var activeSims by remember { mutableStateOf(SimRepository.activeSims(context)) }
    val packageInfo = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val appVersionName = remember(packageInfo) {
        packageInfo?.versionName.orEmpty()
    }
    val appVersionCode = remember(packageInfo) {
        packageInfo?.let { info ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } ?: 0L
    }
    val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val selectedMessageTitle = remember(selectedMessage?.id, selectedMessage?.sender, contactsGranted) {
        selectedMessage?.let { ContactLookup.resolveSender(context, it.sender).primary }
    }
    val selectedConversation = remember(selectedConversationKey, messages, conversationSplitHours, contactsGranted) {
        selectedConversationKey?.let { key ->
            conversationThreads(context, messages.filter { !it.blocked && !it.archived }, conversationSplitHours)
                .firstOrNull { it.key == key }
        }
    }
    val selectedConversationTitle = remember(selectedConversation?.key, selectedConversation?.latest?.sender, contactsGranted) {
        selectedConversation?.let { conversationTitle(context, it) }
    }
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val colors = UiColors(
        background = MaterialTheme.colorScheme.background,
        topBar = MaterialTheme.colorScheme.surface,
        panel = MaterialTheme.colorScheme.surfaceContainerHighest,
        muted = MaterialTheme.colorScheme.onSurfaceVariant,
        divider = MaterialTheme.colorScheme.outlineVariant
    )
    val inboxListState = rememberLazyListState()
    val blockedListState = rememberLazyListState()
    val archiveListState = rememberLazyListState()
    val refreshSettingsState: () -> Unit = {
        onThemeModeChange(appSettingsStore.getThemeMode())
        onAccentColorChange(Color(appSettingsStore.getAccentColor()))
        onDeliveryReportsChange(appSettingsStore.getDeliveryReportsEnabled())
        onUse24HourTimeChange(appSettingsStore.getUse24HourTime())
        onSimSendModeChange(appSettingsStore.getSimSendMode())
        onDefaultSimSubscriptionIdChange(appSettingsStore.getDefaultSimSubscriptionId())
        onAutoArchiveDaysChange(appSettingsStore.getAutoArchiveDays())
        onAutoDeleteBlockedDaysChange(appSettingsStore.getAutoDeleteBlockedDays())
        onWarnBeforeBlockedAutoDeleteChange(appSettingsStore.getWarnBeforeBlockedAutoDelete())
        onConversationSplitHoursChange(appSettingsStore.getConversationSplitHours())
    }
    val completeBackupImport: (ParsedBackup) -> Unit = { backup ->
        scope.launch {
            val conflicts = withContext(Dispatchers.IO) {
                findBackupRuleConflicts(ruleStore, backup.rules)
            }
            if (conflicts.isNotEmpty()) {
                pendingBackupImport = PendingBackupImport(backup, conflicts)
            } else {
                val result = withContext(Dispatchers.IO) {
                    applyBackupImport(
                        settingsStore = appSettingsStore,
                        ruleStore = ruleStore,
                        inboxStore = inboxStore,
                        backup = backup,
                        replaceConflicts = false
                    )
                }
                refreshSettingsState()
                inboxVersion++
                backupNotice = result.summaryText()
                backupDialogNotice = backupNotice
            }
        }
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val selection = backupSelection
            scope.launch {
                backupNotice = withContext(Dispatchers.IO) {
                    runCatching {
                        val backup = buildBackupJson(
                            settingsStore = appSettingsStore,
                            ruleStore = ruleStore,
                            inboxStore = inboxStore,
                            selection = selection,
                            appVersionCode = appVersionCode,
                            appVersionName = appVersionName
                        )
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(backup.toString(2).toByteArray())
                        } ?: error("Could not open export file.")
                        "Export saved."
                    }.getOrElse { "Export failed." }
                }
            }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val selection = backupSelection
            scope.launch {
                val parsed = withContext(Dispatchers.IO) {
                    runCatching {
                        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Could not open import file.")
                        parseBackupJson(JSONObject(raw), selection)
                    }
                }
                parsed.fold(
                    onSuccess = { backup ->
                        if (backup.needsNewerSettingsWarning(appVersionCode)) {
                            pendingNewerBackupImport = backup
                        } else {
                            completeBackupImport(backup)
                        }
                    },
                    onFailure = {
                        backupNotice = "Import failed. Check that the file is an SMS Shield JSON backup."
                        backupDialogNotice = backupNotice
                    }
                )
            }
        }
    }
    val refreshActiveSims: (Boolean) -> Unit = { promptOnChange ->
        val refreshed = SimRepository.activeSims(context)
        val signature = SimRepository.signature(refreshed)
        val previousSignature = appSettingsStore.getKnownSimSignature()
        activeSims = refreshed
        if (signature.isNotBlank()) {
            if (promptOnChange && previousSignature.isNotBlank() && previousSignature != signature) {
                showSimChangedPrompt = true
            }
            appSettingsStore.setKnownSimSignature(signature)
        }
    }
    val openMessageDetail: (SmsMessageRecord) -> Unit = { message ->
        restoreSearchAfterDetail = screen.supportsSearch() && searchActive && searchQuery.isNotBlank()
        if (restoreSearchAfterDetail) {
            searchActive = false
        }
        selectedConversationKey = null
        selectedMessage = message
    }
    val openConversationDetail: (ConversationThread) -> Unit = { conversation ->
        restoreSearchAfterDetail = screen.supportsSearch() && searchActive && searchQuery.isNotBlank()
        if (restoreSearchAfterDetail) {
            searchActive = false
        }
        selectedConversationKey = conversation.key
        selectedMessage = conversation.latest
    }
    val closeMessageDetail: () -> Unit = {
        selectedMessage = null
        selectedConversationKey = null
        if (restoreSearchAfterDetail && screen.supportsSearch()) {
            searchActive = true
        }
        restoreSearchAfterDetail = false
    }
    val applyRuleToSelectedMessage: (Rule) -> Unit = { rule ->
        selectedMessage?.let { message ->
            val blocked = rule.action == RuleAction.BLOCK
            inboxStore.updateBlockedState(message.id, blocked)
            selectedMessage = message.copy(blocked = blocked, archived = false)
            selectedConversationKey = null
            restoreSearchAfterDetail = false
            screen = if (blocked) Screen.BLOCKED else Screen.MAIN
            inboxVersion++
            if (blocked) {
                pendingRetroactiveBlockRule = rule
            }
        }
    }
    val addRuleFromMessage: (RuleAction, RuleType, String) -> Unit = { action, type, text ->
        val rule = Rule(
            type = type,
            pattern = text,
            action = action
        )
        val duplicate = ruleStore.findDuplicate(rule)
        val conflict = ruleStore.findConflict(rule)
        when {
            duplicate != null -> applyRuleToSelectedMessage(duplicate)
            conflict != null -> pendingMessageRuleConflict = PendingMessageRuleConflict(rule, conflict)
            else -> {
                ruleStore.addRule(rule)
                applyRuleToSelectedMessage(rule)
            }
        }
    }
    val archiveSelectedMessage: () -> Unit = {
        selectedMessage?.let { message ->
            inboxStore.updateArchivedState(message.id, true)
            selectedMessage = null
            selectedConversationKey = null
            restoreSearchAfterDetail = false
            screen = Screen.MAIN
            inboxVersion++
        }
    }
    val returnSelectedMessageToInbox: () -> Unit = {
        selectedMessage?.let { message ->
            inboxStore.returnMessagesToInbox(setOf(message.id))
            selectedMessage = null
            selectedConversationKey = null
            restoreSearchAfterDetail = false
            screen = Screen.MAIN
            inboxVersion++
        }
    }
    val archiveMessages: (Set<Long>) -> Unit = { ids ->
        inboxStore.updateArchivedState(ids, true)
        inboxVersion++
    }
    val setMessagesFrozen: (Set<Long>, Boolean) -> Unit = { ids, frozen ->
        inboxStore.updateAutoArchiveFrozen(ids, frozen)
        inboxVersion++
    }
    val returnMessagesToInbox: (Set<Long>) -> Unit = { ids ->
        inboxStore.returnMessagesToInbox(ids)
        inboxVersion++
    }
    val deleteMessages: (Set<Long>) -> Unit = { ids ->
        inboxStore.deleteMessages(ids)
        inboxVersion++
    }
    val handleBackNavigation: () -> Unit = {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            selectedMessage != null || selectedConversationKey != null -> closeMessageDetail()
            searchActive -> {
                searchActive = false
                searchQuery = ""
                restoreSearchAfterDetail = false
            }
            screen != Screen.MAIN -> scope.launch { drawerState.open() }
            else -> scope.launch { drawerState.open() }
        }
    }

    BackHandler(
        enabled = !showComposeDialog &&
            pendingRetroactiveBlockRule == null &&
            (drawerState.isOpen || searchActive || selectedMessage != null || selectedConversationKey != null || screen != Screen.MAIN),
        onBack = handleBackNavigation
    )

    LaunchedEffect(inboxVersion) {
        val loadedMessages = withContext(Dispatchers.IO) {
            if (!messagesLoaded) {
                inboxStore.importFromDeviceInbox()
            }
            inboxStore.listMessages()
        }
        messages = loadedMessages
        messagesLoaded = true
    }

    LaunchedEffect(autoDeleteBlockedDays, warnBeforeBlockedAutoDelete) {
        blockedAutoDeletePromptShown = false
    }

    LaunchedEffect(messagesLoaded, messages, autoArchiveDays, autoDeleteBlockedDays, warnBeforeBlockedAutoDelete, cleanupApplyVersion) {
        if (!messagesLoaded) return@LaunchedEffect
        val archived = withContext(Dispatchers.IO) {
            autoArchiveDays?.let { inboxStore.autoArchiveOlderThan(it) } ?: 0
        }
        val deleted = withContext(Dispatchers.IO) {
            if (autoDeleteBlockedDays != null && !warnBeforeBlockedAutoDelete) {
                inboxStore.deleteBlockedOlderThan(autoDeleteBlockedDays)
            } else {
                0
            }
        }
        if (autoDeleteBlockedDays != null && warnBeforeBlockedAutoDelete && !blockedAutoDeletePromptShown) {
            val count = withContext(Dispatchers.IO) {
                inboxStore.countBlockedOlderThan(autoDeleteBlockedDays)
            }
            if (count > 0) {
                blockedAutoDeleteCandidateCount = count
                showBlockedAutoDeletePrompt = true
                blockedAutoDeletePromptShown = true
            }
        }
        if (archived > 0 || deleted > 0) {
            inboxVersion++
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !appSettingsStore.getBatteryPromptAcknowledged()) {
            val powerManager = context.getSystemService(PowerManager::class.java)
            val exempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            if (exempt) {
                appSettingsStore.setBatteryPromptAcknowledged(true)
            } else {
                showBatteryOptimizationPrompt = true
            }
        }
        refreshActiveSims(false)
    }

    DisposableEffect(Unit) {
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() {
                refreshActiveSims(true)
            }
        }
        runCatching { subscriptionManager?.addOnSubscriptionsChangedListener(listener) }
        onDispose {
            runCatching { subscriptionManager?.removeOnSubscriptionsChangedListener(listener) }
        }
    }

    DisposableEffect(Unit) {
        val statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshActiveSims(false)
                inboxVersion++
            }
        }
        val filter = IntentFilter().apply {
            addAction(SmsStatusReceiver.ACTION_STATUS_UPDATED)
            addAction(InboxStore.ACTION_MESSAGES_UPDATED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(statusReceiver, filter)
        }
        onDispose {
            runCatching { context.unregisterReceiver(statusReceiver) }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = colors.panel, drawerContentColor = MaterialTheme.colorScheme.onBackground) {
                DrawerHeader {
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Inbox", selected = screen == Screen.MAIN) {
                    screen = Screen.MAIN
                    selectedMessage = null
                    selectedConversationKey = null
                    restoreSearchAfterDetail = false
                    mainSelectionActive = false
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Blocked Messages", selected = screen == Screen.BLOCKED) {
                    screen = Screen.BLOCKED
                    selectedMessage = null
                    selectedConversationKey = null
                    restoreSearchAfterDetail = false
                    mainSelectionActive = false
                    searchActive = false
                    searchQuery = ""
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Archive", selected = screen == Screen.ARCHIVE) {
                    screen = Screen.ARCHIVE
                    selectedMessage = null
                    selectedConversationKey = null
                    restoreSearchAfterDetail = false
                    mainSelectionActive = false
                    searchActive = false
                    searchQuery = ""
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Block & Allow List", selected = screen == Screen.BLOCK_ALLOW) {
                    screen = Screen.BLOCK_ALLOW
                    selectedMessage = null
                    selectedConversationKey = null
                    restoreSearchAfterDetail = false
                    mainSelectionActive = false
                    searchActive = false
                    searchQuery = ""
                    scope.launch { drawerState.close() }
                }
                DrawerItem("Settings", selected = screen == Screen.SETTINGS) {
                    screen = Screen.SETTINGS
                    selectedMessage = null
                    selectedConversationKey = null
                    restoreSearchAfterDetail = false
                    mainSelectionActive = false
                    searchActive = false
                    searchQuery = ""
                    scope.launch { drawerState.close() }
                }
                Spacer(Modifier.weight(1f))
                DrawerFooter(versionName = appVersionName)
            }
        }
    ) {
        Scaffold(
            containerColor = colors.background,
            floatingActionButton = {
                if (screen == Screen.MAIN && selectedMessage == null && selectedConversationKey == null && !mainSelectionActive) {
                    FloatingActionButton(
                        onClick = {
                            composeInitialRecipient = ""
                            composeInitialBody = ""
                            composeSourceMessage = null
                            showComposeDialog = true
                        },
                        containerColor = accentColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Compose")
                    }
                }
            },
            topBar = {
                AppTopBar(
                    title = when (screen) {
                        Screen.MAIN -> when {
                            selectedConversation != null -> selectedConversationTitle ?: "Conversation"
                            selectedMessage != null -> selectedMessageTitle ?: "Message"
                            else -> "Inbox"
                        }
                        Screen.BLOCKED -> if (selectedMessage == null) "Blocked Messages" else selectedMessageTitle ?: "Message"
                        Screen.ARCHIVE -> if (selectedMessage == null) "Archive" else selectedMessageTitle ?: "Message"
                        Screen.BLOCK_ALLOW -> "Block & Allow List"
                        Screen.SETTINGS -> "Settings"
                    },
                    showBack = screen != Screen.MAIN || selectedMessage != null || selectedConversationKey != null,
                    onNavigation = handleBackNavigation,
                    colors = colors,
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    showSearch = screen.supportsSearch() && selectedMessage == null && selectedConversationKey == null,
                    onSearchClick = { searchActive = true },
                    onSearchQueryChange = { searchQuery = it },
                    onSearchClose = {
                        searchActive = false
                        searchQuery = ""
                        restoreSearchAfterDetail = false
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    Screen.MAIN -> {
                        if (selectedConversation != null && selectedMessage != null) {
                            MessageDetailView(
                                message = selectedMessage!!,
                                threadMessages = selectedConversation.sortedMessages,
                                colors = colors,
                                activeSims = activeSims,
                                use24HourTime = use24HourTime,
                                onSelectThreadMessage = { selectedMessage = it },
                                onReply = {
                                    composeSourceMessage = selectedMessage
                                    composeInitialRecipient = selectedMessage?.sender.orEmpty().removePrefix("To: ").trim()
                                    composeInitialBody = ""
                                    showComposeDialog = true
                                },
                                onForward = {
                                    composeSourceMessage = null
                                    composeInitialRecipient = ""
                                    composeInitialBody = selectedMessage?.body.orEmpty()
                                    showComposeDialog = true
                                },
                                onDelete = {
                                    selectedMessage?.let { message ->
                                        inboxStore.deleteMessage(message.id)
                                        inboxVersion++
                                        closeMessageDetail()
                                    }
                                },
                                onArchive = archiveSelectedMessage,
                                onReturnToInbox = returnSelectedMessageToInbox,
                                onAddRule = addRuleFromMessage
                            )
                        } else if (selectedMessage != null) {
                            MessageDetailView(
                                message = selectedMessage!!,
                                colors = colors,
                                activeSims = activeSims,
                                use24HourTime = use24HourTime,
                                onReply = {
                                    composeSourceMessage = selectedMessage
                                    composeInitialRecipient = selectedMessage?.sender.orEmpty().removePrefix("To: ").trim()
                                    composeInitialBody = ""
                                    showComposeDialog = true
                                },
                                onForward = {
                                    composeSourceMessage = null
                                    composeInitialRecipient = ""
                                    composeInitialBody = selectedMessage?.body.orEmpty()
                                    showComposeDialog = true
                                },
                                onDelete = {
                                    selectedMessage?.let { message ->
                                        inboxStore.deleteMessage(message.id)
                                        inboxVersion++
                                        closeMessageDetail()
                                    }
                                },
                                onArchive = archiveSelectedMessage,
                                onReturnToInbox = returnSelectedMessageToInbox,
                                onAddRule = addRuleFromMessage
                            )
                        } else {
                            MainListView(
                                messages = messages,
                                loading = !messagesLoaded,
                                colors = colors,
                                searchQuery = searchQuery,
                                accentColor = accentColor,
                                activeSims = activeSims,
                                conversationSplitHours = conversationSplitHours,
                                listState = inboxListState,
                                use24HourTime = use24HourTime,
                                onArchiveMessages = archiveMessages,
                                onSetMessagesFrozen = setMessagesFrozen,
                                onDeleteMessages = deleteMessages,
                                onSelectionActiveChange = { mainSelectionActive = it },
                                onConversationClick = openConversationDetail
                            )
                        }
                    }
                    Screen.BLOCKED -> {
                        if (selectedMessage != null) {
                            MessageDetailView(
                                message = selectedMessage!!,
                                colors = colors,
                                activeSims = activeSims,
                                use24HourTime = use24HourTime,
                                onReply = {
                                    composeSourceMessage = selectedMessage
                                    composeInitialRecipient = selectedMessage?.sender.orEmpty().removePrefix("To: ").trim()
                                    composeInitialBody = ""
                                    showComposeDialog = true
                                },
                                onForward = {
                                    composeSourceMessage = null
                                    composeInitialRecipient = ""
                                    composeInitialBody = selectedMessage?.body.orEmpty()
                                    showComposeDialog = true
                                },
                                onDelete = {
                                    selectedMessage?.let { message ->
                                        inboxStore.deleteMessage(message.id)
                                        inboxVersion++
                                        closeMessageDetail()
                                    }
                                },
                                onArchive = archiveSelectedMessage,
                                onReturnToInbox = returnSelectedMessageToInbox,
                                onAddRule = addRuleFromMessage
                            )
                        } else {
                            BlockedMessagesView(
                                messages = messages,
                                loading = !messagesLoaded,
                                colors = colors,
                                accentColor = accentColor,
                                activeSims = activeSims,
                                listState = blockedListState,
                                onReturnMessagesToInbox = returnMessagesToInbox,
                                onDeleteMessages = deleteMessages,
                                onMessageClick = openMessageDetail
                            )
                        }
                    }
                    Screen.ARCHIVE -> {
                        if (selectedMessage != null) {
                            MessageDetailView(
                                message = selectedMessage!!,
                                colors = colors,
                                activeSims = activeSims,
                                use24HourTime = use24HourTime,
                                onReply = {
                                    composeSourceMessage = selectedMessage
                                    composeInitialRecipient = selectedMessage?.sender.orEmpty().removePrefix("To: ").trim()
                                    composeInitialBody = ""
                                    showComposeDialog = true
                                },
                                onForward = {
                                    composeSourceMessage = null
                                    composeInitialRecipient = ""
                                    composeInitialBody = selectedMessage?.body.orEmpty()
                                    showComposeDialog = true
                                },
                                onDelete = {
                                    selectedMessage?.let { message ->
                                        inboxStore.deleteMessage(message.id)
                                        inboxVersion++
                                        closeMessageDetail()
                                    }
                                },
                                onArchive = archiveSelectedMessage,
                                onReturnToInbox = returnSelectedMessageToInbox,
                                onAddRule = addRuleFromMessage
                            )
                        } else {
                            ArchiveMessagesView(
                                messages = messages,
                                loading = !messagesLoaded,
                                colors = colors,
                                accentColor = accentColor,
                                activeSims = activeSims,
                                searchQuery = searchQuery,
                                listState = archiveListState,
                                onReturnMessagesToInbox = returnMessagesToInbox,
                                onDeleteMessages = deleteMessages,
                                onMessageClick = openMessageDetail
                            )
                        }
                    }
                    Screen.BLOCK_ALLOW -> BlockAllowView(
                        ruleStore = ruleStore,
                        inboxStore = inboxStore,
                        accentColor = accentColor,
                        colors = colors,
                        onMessagesChanged = { inboxVersion++ }
                    )
                    Screen.SETTINGS -> SettingsView(
                        themeMode = themeMode,
                        accentColor = accentColor,
                        deliveryReportsEnabled = deliveryReportsEnabled,
                        use24HourTime = use24HourTime,
                        simSendMode = simSendMode,
                        defaultSimSubscriptionId = defaultSimSubscriptionId,
                        activeSims = activeSims,
                        autoArchiveDays = autoArchiveDays,
                        autoDeleteBlockedDays = autoDeleteBlockedDays,
                        warnBeforeBlockedAutoDelete = warnBeforeBlockedAutoDelete,
                        conversationSplitHours = conversationSplitHours,
                        onThemeModeChange = onThemeModeChange,
                        onAccentColorChange = onAccentColorChange,
                        onDeliveryReportsChange = onDeliveryReportsChange,
                        onUse24HourTimeChange = onUse24HourTimeChange,
                        onSimSendModeChange = onSimSendModeChange,
                        onDefaultSimSubscriptionIdChange = onDefaultSimSubscriptionIdChange,
                        onAutoArchiveDaysChange = {
                            onAutoArchiveDaysChange(it)
                            cleanupApplyVersion++
                        },
                        onAutoDeleteBlockedDaysChange = {
                            onAutoDeleteBlockedDaysChange(it)
                            cleanupApplyVersion++
                        },
                        onWarnBeforeBlockedAutoDeleteChange = onWarnBeforeBlockedAutoDeleteChange,
                        onConversationSplitHoursChange = onConversationSplitHoursChange,
                        backupSelection = backupSelection,
                        backupNotice = backupNotice,
                        onBackupSelectionChange = {
                            backupSelection = it
                            backupNotice = null
                        },
                        onExportBackup = {
                            if (backupSelection.anySelected) {
                                backupNotice = null
                                exportBackupLauncher.launch("sms-shield-backup.json")
                            }
                        },
                        onImportBackup = {
                            if (backupSelection.anySelected) {
                                backupNotice = null
                                importBackupLauncher.launch(arrayOf("application/json", "text/json", "text/*", "*/*"))
                            }
                        },
                        colors = colors
                    )
                }
            }
        }
    }

    if (showComposeDialog) {
        ComposeMessageDialog(
            initialRecipient = composeInitialRecipient,
            initialBody = composeInitialBody,
            error = composeError,
            activeSims = activeSims,
            simSendMode = simSendMode,
            defaultSimSubscriptionId = defaultSimSubscriptionId,
            sourceMessage = composeSourceMessage,
            onDismiss = {
                showComposeDialog = false
                composeError = null
                composeInitialRecipient = ""
                composeInitialBody = ""
                composeSourceMessage = null
            },
            onSend = { recipient, body, selectedSim ->
                try {
                    val simFields = SimRepository.copyFieldsFrom(selectedSim)
                    val sentMessage = SmsMessageRecord(
                        sender = "To: $recipient",
                        body = body,
                        blocked = false,
                        outgoing = true,
                        deliveryStatus = SmsStatusReceiver.DELIVERY_STATUS_SENT,
                        simSubscriptionId = simFields.subscriptionId,
                        simSlotIndex = simFields.slotIndex,
                        simDisplayName = simFields.displayName,
                        simCarrierName = simFields.carrierName
                    )
                    SmsSender().send(
                        context = context,
                        recipient = recipient,
                        body = body,
                        requestDeliveryReport = deliveryReportsEnabled,
                        messageId = sentMessage.id,
                        subscriptionId = selectedSim?.subscriptionId
                    )
                    inboxStore.addSentMessage(sentMessage)
                    inboxVersion++
                    composeError = null
                    showComposeDialog = false
                    composeInitialRecipient = ""
                    composeInitialBody = ""
                    composeSourceMessage = null
                } catch (ex: SecurityException) {
                    composeError = "SMS permission is not available yet."
                } catch (ex: IllegalArgumentException) {
                    composeError = "Check the phone number and message."
                } catch (ex: Exception) {
                    composeError = "Could not send message."
                }
            }
        )
    }

    if (pendingRetroactiveBlockRule != null) {
        val rule = pendingRetroactiveBlockRule!!
        AlertDialog(
            onDismissRequest = { pendingRetroactiveBlockRule = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Search Existing Messages?") },
            text = {
                Text(
                    "Search prior messages for \"${rule.pattern}\" and move matches to Blocked Messages? Allow-list rules are still respected."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        inboxStore.applyRulesToMessages(
                            rules = ruleStore.getRules(),
                            defaultRegion = Locale.getDefault().country.ifBlank { "US" }
                        )
                        selectedMessage = selectedMessage?.copy(blocked = true, archived = false)
                        inboxVersion++
                        pendingRetroactiveBlockRule = null
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Search & Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRetroactiveBlockRule = null }) {
                    Text("Not Now")
                }
            }
        )
    }

    pendingMessageRuleConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { pendingMessageRuleConflict = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Contradicting Rule") },
            text = {
                Text(
                    "This ${conflict.proposed.action.label()} rule conflicts with an existing ${conflict.existing.action.label()} rule for \"${ruleDisplay(conflict.existing)}\". Which rule should SMS Shield keep?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        ruleStore.deleteRule(conflict.existing.id)
                        ruleStore.addRuleIfMissing(conflict.proposed)
                        applyRuleToSelectedMessage(conflict.proposed)
                        pendingMessageRuleConflict = null
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Use New Rule")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMessageRuleConflict = null }) {
                    Text("Keep Existing")
                }
            }
        )
    }

    pendingNewerBackupImport?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingNewerBackupImport = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Backup From Newer App") },
            text = {
                val source = backup.appVersionName?.takeIf { it.isNotBlank() } ?: "a newer version"
                Text(
                    "This JSON was exported from SMS Shield $source. Update SMS Shield first to import settings. You can still import compatible rules and messages without settings."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingNewerBackupImport = null
                        completeBackupImport(backup.copy(settings = null))
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Import Without Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingNewerBackupImport = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingBackupImport?.let { pending ->
        val first = pending.conflicts.first()
        AlertDialog(
            onDismissRequest = { pendingBackupImport = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Rule Conflicts Found") },
            text = {
                Text(
                    "${pending.conflicts.size} imported rule${if (pending.conflicts.size == 1) "" else "s"} contradict existing rules. Example: imported ${first.imported.action.label()} \"${ruleDisplay(first.imported)}\" conflicts with current ${first.existing.action.label()} \"${ruleDisplay(first.existing)}\"."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                applyBackupImport(
                                    settingsStore = appSettingsStore,
                                    ruleStore = ruleStore,
                                    inboxStore = inboxStore,
                                    backup = pending.backup,
                                    replaceConflicts = true
                                )
                            }
                            refreshSettingsState()
                            inboxVersion++
                            backupNotice = result.summaryText()
                            backupDialogNotice = backupNotice
                            pendingBackupImport = null
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Use Import")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                applyBackupImport(
                                    settingsStore = appSettingsStore,
                                    ruleStore = ruleStore,
                                    inboxStore = inboxStore,
                                    backup = pending.backup,
                                    replaceConflicts = false
                                )
                            }
                            refreshSettingsState()
                            inboxVersion++
                            backupNotice = result.summaryText()
                            backupDialogNotice = backupNotice
                            pendingBackupImport = null
                        }
                    }
                ) {
                    Text("Keep Current")
                }
            }
        )
    }

    backupDialogNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { backupDialogNotice = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Import Complete") },
            text = { Text(notice) },
            confirmButton = {
                Button(onClick = { backupDialogNotice = null }, shape = RoundedCornerShape(14.dp)) {
                    Text("OK")
                }
            }
        )
    }

    if (showBatteryOptimizationPrompt) {
        AlertDialog(
            onDismissRequest = {
                appSettingsStore.setBatteryPromptAcknowledged(true)
                showBatteryOptimizationPrompt = false
            },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Keep SMS Shield active?") },
            text = {
                Text("Disable battery optimization for more reliable SMS filtering, notifications, and delivery status updates.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        appSettingsStore.setBatteryPromptAcknowledged(true)
                        showBatteryOptimizationPrompt = false
                        val packageUri = Uri.parse("package:${context.packageName}")
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
                        runCatching {
                            batteryOptimizationLauncher.launch(intent)
                        }.onFailure {
                            batteryOptimizationLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Disable Optimization")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        appSettingsStore.setBatteryPromptAcknowledged(true)
                        showBatteryOptimizationPrompt = false
                    }
                ) {
                    Text("Not Now")
                }
            }
        )
    }

    if (showSimChangedPrompt) {
        AlertDialog(
            onDismissRequest = { showSimChangedPrompt = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("New SIM detected") },
            text = { Text("Looks like you activated a new SIM. Do you want to check the default SIM settings?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSimChangedPrompt = false
                        selectedMessage = null
                        restoreSearchAfterDetail = false
                        searchActive = false
                        searchQuery = ""
                        screen = Screen.SETTINGS
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSimChangedPrompt = false }) {
                    Text("Not Now")
                }
            }
        )
    }

    if (showBlockedAutoDeletePrompt && autoDeleteBlockedDays != null) {
        AlertDialog(
            onDismissRequest = { showBlockedAutoDeletePrompt = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete old blocked messages?") },
            text = {
                Text(
                    "$blockedAutoDeleteCandidateCount blocked message${if (blockedAutoDeleteCandidateCount == 1) "" else "s"} older than $autoDeleteBlockedDays day${if (autoDeleteBlockedDays == 1) "" else "s"} can be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        inboxStore.deleteBlockedOlderThan(autoDeleteBlockedDays)
                        showBlockedAutoDeletePrompt = false
                        inboxVersion++
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showBlockedAutoDeletePrompt = false
                            screen = Screen.BLOCKED
                        }
                    ) {
                        Text("Review")
                    }
                    TextButton(onClick = { showBlockedAutoDeletePrompt = false }) {
                        Text("Not Now")
                    }
                }
            }
        )
    }
}

@Composable
private fun DrawerHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close menu")
        }
    }
}

@Composable
private fun DrawerItem(label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

@Composable
private fun DrawerFooter(versionName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier
                .size(73.dp)
                .clip(RoundedCornerShape(18.dp))
        )
        Text(
            text = if (versionName.isBlank()) "SMS Shield - GPL 3.0" else "SMS Shield $versionName - GPL 3.0",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AppTopBar(
    title: String,
    showBack: Boolean,
    onNavigation: () -> Unit,
    colors: UiColors,
    searchActive: Boolean,
    searchQuery: String,
    showSearch: Boolean,
    onSearchClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit
) {
    Surface(color = colors.topBar, tonalElevation = 0.dp, modifier = Modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onNavigation) {
                    Icon(
                        imageVector = if (showBack || searchActive) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                        contentDescription = if (showBack || searchActive) "Back" else "Menu"
                    )
                }
                if (searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search messages") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (searchActive) {
                IconButton(onClick = onSearchClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close search")
                }
            } else if (showSearch) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
        }
    }
}

@Composable
private fun MainListView(
    messages: List<SmsMessageRecord>,
    loading: Boolean,
    colors: UiColors,
    searchQuery: String,
    accentColor: Color,
    activeSims: List<SimInfo>,
    conversationSplitHours: Int?,
    listState: LazyListState,
    use24HourTime: Boolean,
    onArchiveMessages: (Set<Long>) -> Unit,
    onSetMessagesFrozen: (Set<Long>, Boolean) -> Unit,
    onDeleteMessages: (Set<Long>) -> Unit,
    onSelectionActiveChange: (Boolean) -> Unit,
    onConversationClick: (ConversationThread) -> Unit
) {
    val context = LocalContext.current
    val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingBulkAction by remember { mutableStateOf<BulkMessageAction?>(null) }
    LaunchedEffect(selectedIds) {
        onSelectionActiveChange(selectedIds.isNotEmpty())
    }
    val inboxMessages = remember(messages) {
        messages.filter { !it.blocked && !it.archived }
    }
    LaunchedEffect(inboxMessages) {
        val availableIds = inboxMessages.map { it.id }.toSet()
        selectedIds = selectedIds.filter { it in availableIds }.toSet()
    }
    val conversations = remember(inboxMessages, conversationSplitHours, contactsGranted) {
        conversationThreads(context, inboxMessages, conversationSplitHours)
    }
    val filteredConversations = remember(conversations, searchQuery, contactsGranted, activeSims) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            conversations
        } else {
            conversations.filter { conversation ->
                conversation.messages.any { message ->
                    val status = messageStatus(message)
                    val displaySender = conversationTitle(context, conversation)
                    val simLabel = messageSimOverview(message, activeSims).orEmpty()
                    message.sender.contains(query, ignoreCase = true) ||
                        displaySender.contains(query, ignoreCase = true) ||
                        message.body.contains(query, ignoreCase = true) ||
                        status.contains(query, ignoreCase = true) ||
                        simLabel.contains(query, ignoreCase = true)
                }
            }
        }
    }
    val selectedMessages = remember(filteredConversations, selectedIds) {
        filteredConversations.flatMap { it.messages }.filter { it.id in selectedIds }
    }
    val visibleIds = remember(filteredConversations) { filteredConversations.flatMap { it.messages }.map { it.id }.toSet() }
    val allVisibleSelected = visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)
    val freezeActive = selectedMessages.isNotEmpty() && selectedMessages.all { it.autoArchiveFrozen }
    if (loading) {
        EmptyState("Loading messages", "Preparing your message list.", colors)
        return
    }
    if (messages.isEmpty()) {
        EmptyState("No messages yet", "Incoming SMS will appear here after SMS Shield is set as default.", colors)
        return
    }
    if (inboxMessages.isEmpty()) {
        EmptyState("No inbox messages", "Received and sent messages will appear here. Blocked and archived messages are available from the menu.", colors)
        return
    }
    if (filteredConversations.isEmpty()) {
        EmptyState("No matching messages", "Try another sender, keyword, or status.", colors)
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp,
                top = 10.dp,
                end = 14.dp,
                bottom = if (selectedIds.isEmpty()) 10.dp else 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredConversations, key = { it.key }) { conversation ->
                val latest = conversation.latest
                val threadIds = conversation.ids
                val conversationSelected = threadIds.isNotEmpty() && selectedIds.containsAll(threadIds)
                val senderDisplay = remember(conversation.key, latest.sender, contactsGranted) {
                    conversationTitle(context, conversation)
                }
                MessageRow(
                    sender = senderDisplay,
                    body = latest.body,
                    status = conversationStatus(conversation),
                    timestamp = latest.timestamp,
                    simLabel = conversationSecondaryLine(conversation, activeSims),
                    blocked = latest.blocked,
                    ageAccentWithTimestamp = true,
                    sameDayShowsTime = true,
                    use24HourTime = use24HourTime,
                    selected = conversationSelected,
                    colors = colors,
                    onAvatarClick = {
                        selectedIds = if (conversationSelected) {
                            selectedIds - threadIds
                        } else {
                            selectedIds + threadIds
                        }
                    },
                    onClick = {
                        if (selectedIds.isEmpty()) {
                            onConversationClick(conversation)
                        } else {
                            selectedIds = if (conversationSelected) {
                                selectedIds - threadIds
                            } else {
                                selectedIds + threadIds
                            }
                        }
                    }
                )
            }
        }

        if (selectedIds.isNotEmpty()) {
            SelectionActionOverlay(
                selectedCount = selectedIds.size,
                accentColor = accentColor,
                allSelected = allVisibleSelected,
                freezeActive = freezeActive,
                onSelectAll = { selectedIds = if (allVisibleSelected) emptySet() else visibleIds },
                onArchive = { pendingBulkAction = BulkMessageAction.ARCHIVE },
                onFreezeToggle = {
                    onSetMessagesFrozen(selectedIds, !freezeActive)
                },
                onDelete = { pendingBulkAction = BulkMessageAction.DELETE },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    pendingBulkAction?.let { action ->
        val selectedCount = selectedIds.size
        val frozenSelectedCount = selectedMessages.count { it.autoArchiveFrozen }
        val archivingFrozen = action == BulkMessageAction.ARCHIVE && frozenSelectedCount > 0
        AlertDialog(
            onDismissRequest = { pendingBulkAction = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    when {
                        archivingFrozen -> "Archive skipped messages?"
                        action == BulkMessageAction.ARCHIVE -> "Archive messages?"
                        else -> "Delete messages?"
                    }
                )
            },
            text = {
                Text(
                    when {
                        archivingFrozen -> {
                            "$frozenSelectedCount selected message${if (frozenSelectedCount == 1) " is" else "s are"} marked Skip archiving. Archive anyway?"
                        }
                        action == BulkMessageAction.ARCHIVE -> {
                            "Move $selectedCount selected message${if (selectedCount == 1) "" else "s"} to Archive?"
                        }
                        else -> {
                            "Delete $selectedCount selected message${if (selectedCount == 1) "" else "s"} from SMS Shield?"
                        }
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedIds
                        if (action == BulkMessageAction.ARCHIVE) {
                            onArchiveMessages(ids)
                        } else {
                            onDeleteMessages(ids)
                        }
                        selectedIds = emptySet()
                        pendingBulkAction = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = if (action == BulkMessageAction.DELETE) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = accentColor)
                    }
                ) {
                    Text(if (archivingFrozen) "Archive Anyway" else if (action == BulkMessageAction.ARCHIVE) "Archive" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BlockedMessagesView(
    messages: List<SmsMessageRecord>,
    loading: Boolean,
    colors: UiColors,
    accentColor: Color,
    activeSims: List<SimInfo>,
    listState: LazyListState,
    onReturnMessagesToInbox: (Set<Long>) -> Unit,
    onDeleteMessages: (Set<Long>) -> Unit,
    onMessageClick: (SmsMessageRecord) -> Unit
) {
    val blockedMessages = remember(messages) {
        messages.filter { it.blocked && !it.archived }
    }
    FolderMessageListView(
        messages = blockedMessages,
        loading = loading,
        loadingTitle = "Loading blocked messages",
        loadingBody = "Preparing your blocked list.",
        emptyTitle = "No blocked messages",
        emptyBody = "Messages caught by block rules will appear here silently.",
        colors = colors,
        accentColor = accentColor,
        activeSims = activeSims,
        listState = listState,
        showArchiveAgeDividers = false,
        statusForMessage = { "Blocked" },
        blockedForRow = { true },
        onReturnMessagesToInbox = onReturnMessagesToInbox,
        onDeleteMessages = onDeleteMessages,
        onMessageClick = onMessageClick
    )
}

@Composable
private fun ArchiveMessagesView(
    messages: List<SmsMessageRecord>,
    loading: Boolean,
    colors: UiColors,
    accentColor: Color,
    activeSims: List<SimInfo>,
    searchQuery: String,
    listState: LazyListState,
    onReturnMessagesToInbox: (Set<Long>) -> Unit,
    onDeleteMessages: (Set<Long>) -> Unit,
    onMessageClick: (SmsMessageRecord) -> Unit
) {
    val archivedMessages = remember(messages) {
        messages.filter { it.archived }
    }
    FolderMessageListView(
        messages = archivedMessages,
        loading = loading,
        loadingTitle = "Loading archive",
        loadingBody = "Preparing archived messages.",
        emptyTitle = "No archived messages",
        emptyBody = "Move messages here when you want them out of the inbox.",
        colors = colors,
        accentColor = accentColor,
        activeSims = activeSims,
        searchQuery = searchQuery,
        listState = listState,
        showArchiveAgeDividers = true,
        statusForMessage = { messageStatus(it) },
        blockedForRow = { it.blocked },
        onReturnMessagesToInbox = onReturnMessagesToInbox,
        onDeleteMessages = onDeleteMessages,
        onMessageClick = onMessageClick
    )
}

@Composable
private fun FolderMessageListView(
    messages: List<SmsMessageRecord>,
    loading: Boolean,
    loadingTitle: String,
    loadingBody: String,
    emptyTitle: String,
    emptyBody: String,
    colors: UiColors,
    accentColor: Color,
    activeSims: List<SimInfo>,
    searchQuery: String = "",
    listState: LazyListState,
    showArchiveAgeDividers: Boolean,
    statusForMessage: (SmsMessageRecord) -> String,
    blockedForRow: (SmsMessageRecord) -> Boolean,
    onReturnMessagesToInbox: (Set<Long>) -> Unit,
    onDeleteMessages: (Set<Long>) -> Unit,
    onMessageClick: (SmsMessageRecord) -> Unit
) {
    val context = LocalContext.current
    val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingBulkAction by remember { mutableStateOf<BulkMessageAction?>(null) }
    val visibleMessages = remember(messages, searchQuery, contactsGranted, activeSims) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            messages
        } else {
            messages.filter { message ->
                val sender = ContactLookup.resolveSender(context, message.sender).primary
                val status = statusForMessage(message)
                val simLabel = messageSecondaryLine(message, activeSims).orEmpty()
                message.sender.contains(query, ignoreCase = true) ||
                    sender.contains(query, ignoreCase = true) ||
                    message.body.contains(query, ignoreCase = true) ||
                    status.contains(query, ignoreCase = true) ||
                    simLabel.contains(query, ignoreCase = true)
            }
        }
    }
    val visibleIds = remember(visibleMessages) { visibleMessages.map { it.id }.toSet() }
    val allVisibleSelected = visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)
    val toggleSelection: (Long) -> Unit = { id ->
        selectedIds = if (id in selectedIds) {
            selectedIds - id
        } else {
            selectedIds + id
        }
    }
    LaunchedEffect(messages) {
        val availableIds = messages.map { it.id }.toSet()
        selectedIds = selectedIds.filter { it in availableIds }.toSet()
    }
    if (loading) {
        EmptyState(loadingTitle, loadingBody, colors)
        return
    }
    if (messages.isEmpty()) {
        EmptyState(emptyTitle, emptyBody, colors)
        return
    }
    if (visibleMessages.isEmpty()) {
        EmptyState("No matching messages", "Try another sender, keyword, or status.", colors)
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp,
                top = 10.dp,
                end = 14.dp,
                bottom = if (selectedIds.isEmpty()) 10.dp else 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            var lastArchiveBucket = 0
            visibleMessages.forEach { msg ->
                val archiveBucket = if (showArchiveAgeDividers) archiveAgeBucket(msg.timestamp) else 0
                if (archiveBucket != 0 && archiveBucket != lastArchiveBucket) {
                    item(key = "archive-divider-$archiveBucket-${msg.id}") {
                        ArchiveAgeDivider(archiveBucket, colors)
                    }
                }
                lastArchiveBucket = archiveBucket
                item(key = msg.id) {
                val senderDisplay = remember(msg.id, msg.sender, contactsGranted) {
                    ContactLookup.resolveSender(context, msg.sender)
                }
                MessageRow(
                    sender = senderDisplay.primary,
                    body = msg.body,
                    status = statusForMessage(msg),
                    timestamp = msg.timestamp,
                    simLabel = messageSecondaryLine(msg, activeSims),
                    blocked = blockedForRow(msg),
                    ageAccentWithTimestamp = showArchiveAgeDividers,
                    selected = msg.id in selectedIds,
                    colors = colors,
                    onAvatarClick = { toggleSelection(msg.id) },
                    onClick = {
                        if (selectedIds.isEmpty()) {
                            onMessageClick(msg)
                        } else {
                            toggleSelection(msg.id)
                        }
                    }
                )
                }
            }
        }

        if (selectedIds.isNotEmpty()) {
            ReturnSelectionActionOverlay(
                selectedCount = selectedIds.size,
                accentColor = accentColor,
                allSelected = allVisibleSelected,
                onSelectAll = { selectedIds = if (allVisibleSelected) emptySet() else visibleIds },
                onReturnToInbox = { pendingBulkAction = BulkMessageAction.RETURN_TO_INBOX },
                onDelete = { pendingBulkAction = BulkMessageAction.DELETE },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    pendingBulkAction?.let { action ->
        val selectedCount = selectedIds.size
        AlertDialog(
            onDismissRequest = { pendingBulkAction = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(if (action == BulkMessageAction.RETURN_TO_INBOX) "Return messages to inbox?" else "Delete messages?")
            },
            text = {
                Text(
                    if (action == BulkMessageAction.RETURN_TO_INBOX) {
                        "Move $selectedCount selected message${if (selectedCount == 1) "" else "s"} back to inbox?"
                    } else {
                        "Delete $selectedCount selected message${if (selectedCount == 1) "" else "s"} from SMS Shield?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedIds
                        if (action == BulkMessageAction.RETURN_TO_INBOX) {
                            onReturnMessagesToInbox(ids)
                        } else {
                            onDeleteMessages(ids)
                        }
                        selectedIds = emptySet()
                        pendingBulkAction = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = if (action == BulkMessageAction.DELETE) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = accentColor)
                    }
                ) {
                    Text(if (action == BulkMessageAction.RETURN_TO_INBOX) "Return" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReturnSelectionActionOverlay(
    selectedCount: Int,
    accentColor: Color,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onReturnToInbox: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(selectedCount.toString(), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    onClick = onSelectAll,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (allSelected) accentColor else accentColor.copy(alpha = 0.16f))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_select_all_24),
                        contentDescription = "Select all",
                        tint = if (allSelected) MaterialTheme.colorScheme.onPrimary else accentColor
                    )
                }
                IconButton(
                    onClick = onReturnToInbox,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_unarchive_24),
                        contentDescription = "Return selected to inbox",
                        tint = accentColor
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SelectionActionOverlay(
    selectedCount: Int,
    accentColor: Color,
    allSelected: Boolean,
    freezeActive: Boolean,
    onSelectAll: () -> Unit,
    onArchive: () -> Unit,
    onFreezeToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(selectedCount.toString(), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    onClick = onSelectAll,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (allSelected) accentColor else accentColor.copy(alpha = 0.16f))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_select_all_24),
                        contentDescription = "Select all",
                        tint = if (allSelected) MaterialTheme.colorScheme.onPrimary else accentColor
                    )
                }
                IconButton(
                    onClick = onFreezeToggle,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (freezeActive) accentColor else accentColor.copy(alpha = 0.16f))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_freeze_option_01),
                        contentDescription = if (freezeActive) "Allow selected to archive automatically" else "Skip archiving for selected",
                        tint = if (freezeActive) MaterialTheme.colorScheme.onPrimary else accentColor
                    )
                }
                IconButton(
                    onClick = onArchive,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_archive_24),
                        contentDescription = "Archive selected",
                        tint = accentColor
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MessageRow(
    sender: String,
    body: String,
    status: String,
    timestamp: Long,
    simLabel: String? = null,
    blocked: Boolean,
    ageAccentWithTimestamp: Boolean = false,
    sameDayShowsTime: Boolean = false,
    use24HourTime: Boolean = false,
    selected: Boolean = false,
    colors: UiColors,
    onAvatarClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val currentWeek = isTimestampInCurrentWeek(timestamp)
    val agedColor = colors.muted.copy(alpha = 0.58f)
    val rowAccent = when {
        blocked -> MaterialTheme.colorScheme.error
        ageAccentWithTimestamp && !currentWeek -> agedColor
        else -> MaterialTheme.colorScheme.primary
    }
    val dateColor = when {
        currentWeek -> MaterialTheme.colorScheme.primary
        ageAccentWithTimestamp -> agedColor
        else -> colors.muted
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = colors.panel,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(
                seed = sender,
                color = rowAccent,
                selected = selected,
                onClick = onAvatarClick
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(sender, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (simLabel != null) {
                    Text(
                        simLabel,
                        color = colors.muted,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(body, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    compactDateLabel(timestamp, sameDayShowsTime, use24HourTime),
                    color = dateColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (currentWeek) FontWeight.SemiBold else FontWeight.Normal
                )
                StatusPill(status, blocked, colorOverride = rowAccent)
            }
        }
    }
}

@Composable
private fun ArchiveAgeDivider(days: Int, colors: UiColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.divider)
        )
        Text("Older than $days days", color = colors.muted, style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.divider)
        )
    }
}

@Composable
private fun Avatar(seed: String, color: Color, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(avatarLabel(seed), color = color, fontWeight = FontWeight.Bold)
        }
    }
}

private fun avatarLabel(seed: String): String {
    val cleaned = seed.removePrefix("To:").trim()
    val hasDigit = cleaned.any { it.isDigit() }
    val hasLetter = cleaned.any { it.isLetter() }
    if (hasDigit && !hasLetter) return "#"
    return cleaned.firstOrNull { !it.isWhitespace() }?.uppercase() ?: "?"
}

@Composable
private fun StatusPill(text: String, blocked: Boolean, colorOverride: Color? = null) {
    val color = colorOverride ?: if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun messageStatus(message: SmsMessageRecord): String {
    return when {
        message.archived -> "Archived"
        message.blocked -> "Blocked"
        message.outgoing -> message.deliveryStatus ?: "Sent"
        else -> "Received"
    }
}

private fun conversationThreads(context: Context, messages: List<SmsMessageRecord>, splitAfterHours: Int?): List<ConversationThread> {
    val splitMillis = splitAfterHours?.takeIf { it > 0 }?.toLong()?.times(MILLIS_PER_HOUR)
    return messages
        .groupBy { conversationAddressKey(context, it) }
        .flatMap { (addressKey, groupedMessages) ->
            val sorted = groupedMessages.sortedBy { it.timestamp }
            buildList {
                var current = mutableListOf<SmsMessageRecord>()
                sorted.forEach { message ->
                    val previous = current.lastOrNull()
                    val shouldSplit = previous != null &&
                        splitMillis != null &&
                        message.timestamp - previous.timestamp > splitMillis
                    if (shouldSplit && current.isNotEmpty()) {
                        val firstTimestamp = current.first().timestamp
                        add(ConversationThread("$addressKey:$firstTimestamp", current.toList()))
                        current = mutableListOf()
                    }
                    current.add(message)
                }
                if (current.isNotEmpty()) {
                    val firstTimestamp = current.first().timestamp
                    add(ConversationThread("$addressKey:$firstTimestamp", current.toList()))
                }
            }
        }
        .sortedByDescending { it.latest.timestamp }
}

private fun conversationAddressKey(context: Context, message: SmsMessageRecord): String {
    val address = conversationAddress(message)
    if (!shouldGroupAddress(context, address)) {
        return "single:${message.id}"
    }
    val digits = address.filter { it.isDigit() }
    return if (digits.isNotBlank()) {
        "number:$digits"
    } else {
        "sender:${address.lowercase(Locale.ROOT)}"
    }
}

private fun conversationAddress(message: SmsMessageRecord): String {
    return message.sender.removePrefix("To:").trim().ifBlank { message.sender.trim() }
}

private fun shouldGroupAddress(context: Context, address: String): Boolean {
    return isRealPhoneNumber(address) || ContactLookup.resolveSender(context, address).secondary != null
}

private fun isRealPhoneNumber(address: String): Boolean {
    val normalized = address.filter { it.isDigit() || it == '+' }
    val digits = normalized.filter { it.isDigit() }
    if (digits.length < 7) return false

    return try {
        val region = Locale.getDefault().country.ifBlank { "US" }
        val parsed = PhoneNumberUtil.getInstance().parse(normalized, region)
        PhoneNumberUtil.getInstance().isPossibleNumber(parsed) || PhoneNumberUtil.getInstance().isValidNumber(parsed)
    } catch (_: Exception) {
        digits.length >= 7 && (normalized.startsWith("+") || digits.length >= 8)
    }
}

private fun conversationStatus(conversation: ConversationThread): String {
    return if (conversation.messages.size == 1) {
        messageStatus(conversation.latest)
    } else {
        "${conversation.messages.size} msgs"
    }
}

private fun conversationTitle(context: Context, conversation: ConversationThread): String {
    val displaySender = conversation.messages.firstOrNull { !it.outgoing } ?: conversation.latest
    val display = ContactLookup.resolveSender(context, conversationAddress(displaySender)).primary.removePrefix("To:").trim()
    val hasIncoming = conversation.messages.any { !it.outgoing }
    val hasOutgoing = conversation.messages.any { it.outgoing }
    return if (conversation.messages.size > 1 || hasIncoming && hasOutgoing) {
        "Chat with $display"
    } else if (hasOutgoing) {
        "To: $display"
    } else {
        display
    }
}

private fun conversationSecondaryLine(conversation: ConversationThread, activeSims: List<SimInfo>): String? {
    return listOfNotNull(
        messageSimOverview(conversation.latest, activeSims),
        if (conversation.messages.any { it.autoArchiveFrozen }) "Skip archiving" else null
    ).takeIf { it.isNotEmpty() }?.joinToString(" | ")
}

private fun compactDateLabel(timestamp: Long, sameDayShowsTime: Boolean, use24HourTime: Boolean): String {
    return SimpleDateFormat(
        when {
            sameDayShowsTime && isTimestampToday(timestamp) -> if (use24HourTime) "HH:mm" else "h:mm a"
            isTimestampInCurrentWeek(timestamp) -> "EEE"
            else -> "d MMM"
        },
        Locale.getDefault()
    ).format(Date(timestamp))
}

private fun fullDateTimeLabel(timestamp: Long, use24HourTime: Boolean): String {
    return SimpleDateFormat(
        if (use24HourTime) "EEE, d MMM yyyy, HH:mm" else "EEE, d MMM yyyy, h:mm a",
        Locale.getDefault()
    ).format(Date(timestamp))
}

private fun isTimestampInCurrentWeek(timestamp: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.WEEK_OF_YEAR) == then.get(Calendar.WEEK_OF_YEAR)
}

private fun isTimestampToday(timestamp: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

private fun archiveAgeBucket(timestamp: Long, now: Long = System.currentTimeMillis()): Int {
    val ageDays = (now - timestamp).coerceAtLeast(0L) / MILLIS_PER_DAY
    return when {
        ageDays >= 90 -> 90
        ageDays >= 30 -> 30
        ageDays >= 7 -> 7
        else -> 0
    }
}

private fun messageSimOverview(message: SmsMessageRecord, activeSims: List<SimInfo>): String? {
    if (!message.hasStoredSimInfo()) return null
    val direction = if (message.outgoing) "Sent via" else "Received on"
    val active = activeSimForMessage(message, activeSims)
    return if (active != null) {
        "$direction ${active.shortLabel}"
    } else if (activeSims.isEmpty()) {
        "$direction ${message.simSlotIndex?.let { "SIM ${it + 1}" } ?: "SIM"}"
    } else {
        "$direction old SIM"
    }
}

private fun messageSecondaryLine(message: SmsMessageRecord, activeSims: List<SimInfo>): String? {
    return listOfNotNull(
        messageSimOverview(message, activeSims),
        if (message.autoArchiveFrozen) "Skip archiving" else null
    ).takeIf { it.isNotEmpty() }?.joinToString(" | ")
}

private fun messageSimDetail(message: SmsMessageRecord, activeSims: List<SimInfo>): String? {
    if (!message.hasStoredSimInfo()) return null
    val direction = if (message.outgoing) "Sent via" else "Received on"
    val active = activeSimForMessage(message, activeSims)
    if (active != null) {
        return "$direction ${active.detailLabel}"
    }
    if (activeSims.isEmpty()) {
        val slot = message.simSlotIndex?.let { "SIM ${it + 1}" } ?: "SIM"
        val historicalName = listOf(message.simDisplayName, message.simCarrierName)
            .map { it.orEmpty().trim() }
            .firstOrNull { it.isNotBlank() && !it.equals(slot, ignoreCase = true) }
        return listOfNotNull("$direction $slot", historicalName).joinToString(", ")
    }

    val slot = message.simSlotIndex?.let { "old SIM ${it + 1}" } ?: "old SIM"
    val historicalName = listOf(message.simDisplayName, message.simCarrierName)
        .map { it.orEmpty().trim() }
        .firstOrNull { it.isNotBlank() && !it.equals(slot, ignoreCase = true) }
    return listOfNotNull("$direction $slot", historicalName, "no longer active").joinToString(", ")
}

private fun activeSimForMessage(message: SmsMessageRecord, activeSims: List<SimInfo>): SimInfo? {
    if (message.simSubscriptionId != null) {
        return activeSims.firstOrNull { it.subscriptionId == message.simSubscriptionId }
    }
    return message.simSlotIndex?.let { slot ->
        activeSims.firstOrNull { it.slotIndex == slot }
    }
}

private fun SmsMessageRecord.hasStoredSimInfo(): Boolean {
    return simSubscriptionId != null || simSlotIndex != null || !simDisplayName.isNullOrBlank() || !simCarrierName.isNullOrBlank()
}

@Composable
private fun ConversationThreadSection(
    messages: List<SmsMessageRecord>,
    activeMessageId: Long,
    colors: UiColors,
    activeSims: List<SimInfo>,
    onSelectMessage: (SmsMessageRecord) -> Unit
) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(24.dp), color = colors.panel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Conversation", fontWeight = FontWeight.Bold)
            messages.forEach { message ->
                val selected = message.id == activeMessageId
                val sender = if (message.outgoing) {
                    "You"
                } else {
                    ContactLookup.resolveSender(context, message.sender).primary
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectMessage(message) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                    border = if (selected) ButtonDefaults.outlinedButtonBorder else null
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(sender, fontWeight = FontWeight.SemiBold)
                            Text(messageStatus(message), color = colors.muted, style = MaterialTheme.typography.labelMedium)
                        }
                        messageSimOverview(message, activeSims)?.let {
                            Text(it, color = colors.muted, style = MaterialTheme.typography.labelMedium)
                        }
                        SelectionContainer {
                            Text(message.body, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageDetailView(
    message: SmsMessageRecord,
    threadMessages: List<SmsMessageRecord> = emptyList(),
    colors: UiColors,
    activeSims: List<SimInfo>,
    use24HourTime: Boolean,
    onSelectThreadMessage: (SmsMessageRecord) -> Unit = {},
    onReply: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onReturnToInbox: () -> Unit,
    onAddRule: (RuleAction, RuleType, String) -> Unit
) {
    val senderDisplay = rememberContactDisplay(message)
    val senderNumber = remember(message.sender) { phoneAddressForRule(message.sender) }
    val simDetail = remember(message, activeSims) { messageSimDetail(message, activeSims) }
    var showDeleteConfirm by remember(message.id) { mutableStateOf(false) }
    var showArchiveConfirm by remember(message.id) { mutableStateOf(false) }
    var bodyValue by remember(message.id) { mutableStateOf(TextFieldValue(message.body)) }
    var addedNotice by remember(message.id) { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val otpCode = remember(message.body) { OtpDetector.findOtp(message.body) }
    val selection = bodyValue.selection
    val selectedText = remember(bodyValue) {
        val start = minOf(selection.start, selection.end).coerceIn(0, bodyValue.text.length)
        val end = maxOf(selection.start, selection.end).coerceIn(0, bodyValue.text.length)
        bodyValue.text.substring(start, end).trim()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (threadMessages.size > 1) {
            item {
                ConversationThreadSection(
                    messages = threadMessages,
                    activeMessageId = message.id,
                    colors = colors,
                    activeSims = activeSims,
                    onSelectMessage = onSelectThreadMessage
                )
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = colors.panel) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            senderDisplay.primary,
                            if (message.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(senderDisplay.primary, fontWeight = FontWeight.Bold)
                            Text(
                                senderDisplay.secondary?.let { address ->
                                    if (message.outgoing) "Sent message to $address" else "Received from $address"
                                } ?: if (message.outgoing) "Sent message" else if (message.blocked) "Blocked message" else "Received message",
                                color = colors.muted
                            )
                            if (simDetail != null) {
                                Text(simDetail, color = colors.muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        StatusPill(
                            messageStatus(message),
                            message.blocked
                        )
                    }
                    if (senderNumber != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onAddRule(RuleAction.BLOCK, RuleType.NUMBER, senderNumber)
                                    addedNotice = "Added number to block list."
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Block Number", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = {
                                    onAddRule(RuleAction.ALLOW, RuleType.NUMBER, senderNumber)
                                    addedNotice = "Added number to allow list."
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Allow Number", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Text(
                        fullDateTimeLabel(message.timestamp, use24HourTime),
                        color = colors.muted,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = bodyValue,
                        onValueChange = { bodyValue = it },
                        readOnly = true,
                        label = { Text("Message") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (otpCode != null) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(otpCode))
                                addedNotice = "Copied OTP to clipboard."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Copy OTP: $otpCode", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        "Highlight text to add to block/allow list",
                        color = colors.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (selectedText.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onAddRule(RuleAction.BLOCK, RuleType.KEYWORD, selectedText)
                                    addedNotice = "Added selected text to block list."
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Block Selected", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = {
                                    onAddRule(RuleAction.ALLOW, RuleType.KEYWORD, selectedText)
                                    addedNotice = "Added selected text to allow list."
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Allow Selected", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (addedNotice != null) {
                        Text(addedNotice.orEmpty(), color = MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onReply, shape = RoundedCornerShape(14.dp)) {
                            Text("Reply")
                        }
                        Button(onClick = onForward, shape = RoundedCornerShape(14.dp)) {
                            Text("Forward")
                        }
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (!message.archived) {
                        Button(
                            onClick = {
                                if (message.autoArchiveFrozen) {
                                    showArchiveConfirm = true
                                } else {
                                    onArchive()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Move to Archive")
                        }
                    } else {
                        Button(
                            onClick = onReturnToInbox,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Return to Inbox")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete Message?") },
            text = { Text("This removes the message from SMS Shield.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Archive skipped message?") },
            text = { Text("This message is marked Skip archiving. Archive anyway?") },
            confirmButton = {
                Button(
                    onClick = {
                        showArchiveConfirm = false
                        onArchive()
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Archive Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun rememberContactDisplay(message: SmsMessageRecord): ContactDisplay {
    val context = LocalContext.current
    val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    return remember(message.id, message.sender, contactsGranted) {
        ContactLookup.resolveSender(context, message.sender)
    }
}

private fun phoneAddressForRule(sender: String): String? {
    val address = sender.removePrefix("To:").trim()
    return address.takeIf { candidate -> candidate.any { it.isDigit() } }
}

private fun shouldShowSendSimPicker(
    activeSims: List<SimInfo>,
    simSendMode: SimSendMode,
    defaultSimSubscriptionId: Int?,
    sourceMessage: SmsMessageRecord?
): Boolean {
    if (activeSims.size <= 1) return false
    return when (simSendMode) {
        SimSendMode.ASK -> true
        SimSendMode.REPLY_WHERE_RECEIVED -> sourceMessage == null || activeSimForMessage(sourceMessage, activeSims) == null
        SimSendMode.ALWAYS -> activeSims.none { it.subscriptionId == defaultSimSubscriptionId }
    }
}

private fun initialSendSim(
    activeSims: List<SimInfo>,
    simSendMode: SimSendMode,
    defaultSimSubscriptionId: Int?,
    sourceMessage: SmsMessageRecord?
): SimInfo? {
    if (activeSims.isEmpty()) return null
    if (activeSims.size == 1) return activeSims.first()
    return when (simSendMode) {
        SimSendMode.ASK -> activeSims.first()
        SimSendMode.REPLY_WHERE_RECEIVED -> sourceMessage?.let { activeSimForMessage(it, activeSims) } ?: activeSims.first()
        SimSendMode.ALWAYS -> activeSims.firstOrNull { it.subscriptionId == defaultSimSubscriptionId } ?: activeSims.first()
    }
}

@Composable
private fun BlockAllowView(
    ruleStore: RuleStore,
    inboxStore: InboxStore,
    accentColor: Color,
    colors: UiColors,
    onMessagesChanged: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var rules by remember { mutableStateOf(ruleStore.getRules()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<Rule?>(null) }
    var deletedBlockRule by remember { mutableStateOf<Rule?>(null) }
    var pendingRuleConflict by remember { mutableStateOf<PendingEditorRuleConflict?>(null) }
    var duplicateRuleNotice by remember { mutableStateOf<String?>(null) }
    val action = if (tab == 0) RuleAction.BLOCK else RuleAction.ALLOW
    val current = rules.filter { it.action == action }
    val commitRule: (Rule, Boolean) -> Unit = { rule, wasEditing ->
        if (wasEditing) {
            ruleStore.updateRule(rule)
            editingRule = null
        } else {
            ruleStore.addRuleIfMissing(rule)
            showAddDialog = false
        }
        rules = ruleStore.getRules()
    }
    val saveRuleWithChecks: (Rule, Boolean) -> Unit = { rule, wasEditing ->
        val ignoreId = if (wasEditing) rule.id else null
        val duplicate = ruleStore.findDuplicate(rule, ignoreId = ignoreId)
        val conflict = ruleStore.findConflict(rule, ignoreId = ignoreId)
        when {
            duplicate != null -> {
                duplicateRuleNotice = "That rule already exists."
                showAddDialog = false
                editingRule = null
            }
            conflict != null -> {
                pendingRuleConflict = PendingEditorRuleConflict(rule, conflict, wasEditing)
            }
            else -> commitRule(rule, wasEditing)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SegmentedTabs(
            selected = tab,
            labels = listOf("Block", "Allow"),
            accentColor = accentColor,
            colors = colors,
            onSelected = { tab = it }
        )

        if (current.isEmpty()) {
            EmptyState(
                title = if (tab == 0) "No blocked items" else "No allowed items",
                body = if (tab == 0) {
                    "Add keywords, numbers, or countries to stop matching SMS."
                } else {
                    "Add keywords, numbers, or countries that should bypass block rules."
                },
                colors = colors,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(current, key = { it.id }) { rule ->
                    RuleRow(
                        rule = rule,
                        colors = colors,
                        onEdit = { editingRule = rule },
                        onEnabledChange = { enabled ->
                            ruleStore.updateRule(rule.copy(enabled = enabled))
                            rules = ruleStore.getRules()
                        },
                        onDelete = {
                            ruleStore.deleteRule(rule.id)
                            rules = ruleStore.getRules()
                            if (rule.action == RuleAction.BLOCK) {
                                deletedBlockRule = rule
                            }
                        }
                    )
                }
            }
        }

        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (tab == 0) "Add To Block List" else "Add To Allow List")
        }
    }

    if (showAddDialog) {
        RuleEditorDialog(
            action = action,
            existingRule = null,
            onDismiss = { showAddDialog = false },
            onSave = { type, pattern, partial, enabled ->
                saveRuleWithChecks(
                    Rule(
                        type = type,
                        pattern = pattern,
                        action = action,
                        partialNumber = type == RuleType.NUMBER && partial,
                        enabled = enabled
                    ),
                    false
                )
            }
        )
    }

    editingRule?.let { rule ->
        RuleEditorDialog(
            action = rule.action,
            existingRule = rule,
            onDismiss = { editingRule = null },
            onSave = { type, pattern, partial, enabled ->
                saveRuleWithChecks(
                    rule.copy(
                        type = type,
                        pattern = pattern,
                        partialNumber = type == RuleType.NUMBER && partial,
                        enabled = enabled
                    ),
                    true
                )
            }
        )
    }

    pendingRuleConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { pendingRuleConflict = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Contradicting Rule") },
            text = {
                Text(
                    "This ${conflict.proposed.action.label()} rule conflicts with an existing ${conflict.existing.action.label()} rule for \"${ruleDisplay(conflict.existing)}\". Which rule should SMS Shield keep?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        ruleStore.deleteRule(conflict.existing.id)
                        commitRule(conflict.proposed, conflict.wasEditing)
                        pendingRuleConflict = null
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Use New Rule")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingRuleConflict = null
                        showAddDialog = false
                        editingRule = null
                    }
                ) {
                    Text("Keep Existing")
                }
            }
        )
    }

    duplicateRuleNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { duplicateRuleNotice = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Duplicate Rule") },
            text = { Text(notice) },
            confirmButton = {
                Button(onClick = { duplicateRuleNotice = null }, shape = RoundedCornerShape(14.dp)) {
                    Text("OK")
                }
            }
        )
    }

    deletedBlockRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deletedBlockRule = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Move blocked messages back to inbox?") },
            text = {
                Text(
                    "The block rule \"${rule.pattern}\" was deleted. Re-check existing blocked messages and move anything no longer blocked back to inbox?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        inboxStore.moveNoLongerBlockedToInbox(
                            rules = ruleStore.getRules(),
                            defaultRegion = Locale.getDefault().country.ifBlank { "US" }
                        )
                        onMessagesChanged()
                        deletedBlockRule = null
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Move Back")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletedBlockRule = null }) {
                    Text("Leave Blocked")
                }
            }
        )
    }
}

@Composable
private fun SegmentedTabs(
    selected: Int,
    labels: List<String>,
    accentColor: Color,
    colors: UiColors,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.panel)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selected == index
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelected(index) },
                color = if (isSelected) accentColor else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else colors.muted,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RuleRow(
    rule: Rule,
    colors: UiColors,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val subtitle = when (rule.type) {
        RuleType.KEYWORD -> "Keyword match"
        RuleType.NUMBER -> if (rule.partialNumber) "Number contains" else "Number exact or wildcard"
        RuleType.COUNTRY -> "Country"
    }
    val displayPattern = if (rule.type == RuleType.COUNTRY) countryLabelForCode(rule.pattern) else rule.pattern
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.panel
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (rule.enabled) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                colors.muted.copy(alpha = 0.12f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = if (rule.enabled) MaterialTheme.colorScheme.primary else colors.muted
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        displayPattern,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 4,
                        overflow = TextOverflow.Clip,
                        color = if (rule.enabled) MaterialTheme.colorScheme.onBackground else colors.muted
                    )
                    Text(if (rule.enabled) subtitle else "$subtitle - disabled", color = colors.muted)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = rule.enabled, onCheckedChange = onEnabledChange)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = colors.muted)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.muted)
                }
            }
        }
    }
}

@Composable
private fun RuleEditorDialog(
    action: RuleAction,
    existingRule: Rule?,
    onDismiss: () -> Unit,
    onSave: (RuleType, String, Boolean, Boolean) -> Unit
) {
    var type by remember(existingRule) { mutableStateOf(existingRule?.type ?: RuleType.KEYWORD) }
    var pattern by remember(existingRule) {
        mutableStateOf(
            if (existingRule?.type == RuleType.COUNTRY) {
                countryLabelForCode(existingRule.pattern)
            } else {
                existingRule?.pattern.orEmpty()
            }
        )
    }
    var partial by remember(existingRule) { mutableStateOf(existingRule?.partialNumber ?: false) }
    var enabled by remember(existingRule) { mutableStateOf(existingRule?.enabled ?: true) }
    var selectedCountry by remember { mutableStateOf<CountryOption?>(null) }
    val countries = remember { countryOptions() }
    val countryMatches = remember(pattern, type) {
        if (type != RuleType.COUNTRY || pattern.isBlank()) {
            emptyList()
        } else {
            val query = pattern.trim().lowercase(Locale.ROOT)
            countries
                .filter {
                    it.name.lowercase(Locale.ROOT).contains(query) ||
                        it.code.lowercase(Locale.ROOT).startsWith(query) ||
                        it.callingCode.startsWith(query.removePrefix("+"))
                }
                .take(6)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                when {
                    existingRule != null && action == RuleAction.BLOCK -> "Edit Block Rule"
                    existingRule != null -> "Edit Allow Rule"
                    action == RuleAction.BLOCK -> "Add Block Rule"
                    else -> "Add Allow Rule"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SegmentedRuleType(selected = type, onSelected = { type = it })
                OutlinedTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        selectedCountry = null
                    },
                    label = { Text(if (type == RuleType.COUNTRY) "Country" else "Pattern") },
                    placeholder = {
                        Text(
                            when (type) {
                                RuleType.KEYWORD -> "Example: verification or *promo*"
                                RuleType.NUMBER -> "Example: +86*, 10010, 555"
                                RuleType.COUNTRY -> "Start typing country"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == RuleType.COUNTRY && countryMatches.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            countryMatches.forEach { country ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCountry = country
                                            pattern = country.displayLabel()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(country.name)
                                    Text("+${country.callingCode}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (type == RuleType.NUMBER) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Partial number match", fontWeight = FontWeight.Medium)
                            Text("Match if sender contains pattern", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = partial, onCheckedChange = { partial = it })
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Enabled", fontWeight = FontWeight.Medium)
                        Text("Use this rule while filtering", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedPattern = if (type == RuleType.COUNTRY) {
                        selectedCountry?.code ?: countryCodeForInput(pattern) ?: pattern.trim().uppercase(Locale.ROOT)
                    } else {
                        pattern.trim()
                    }
                    onSave(type, normalizedPattern, partial, enabled)
                },
                enabled = pattern.isNotBlank(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (existingRule == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SegmentedRuleType(selected: RuleType, onSelected: (RuleType) -> Unit) {
    val values = listOf(RuleType.KEYWORD, RuleType.NUMBER, RuleType.COUNTRY)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            val active = value == selected
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onSelected(value) },
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(999.dp),
                border = if (active) null else ButtonDefaults.outlinedButtonBorder
            ) {
                Text(
                    text = value.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun countryOptions(): List<CountryOption> {
    val phoneUtil = PhoneNumberUtil.getInstance()
    return Locale.getISOCountries()
        .map { code ->
            val locale = Locale("", code)
            CountryOption(
                code = code,
                name = locale.displayCountry,
                callingCode = phoneUtil.getCountryCodeForRegion(code).toString()
            )
        }
        .filter { it.name.isNotBlank() }
        .sortedBy { it.name }
}

private fun CountryOption.displayLabel(): String = "$name (+$callingCode)"

private fun countryLabelForCode(code: String): String {
    val normalized = code.trim().uppercase(Locale.ROOT)
    return countryOptions().firstOrNull { it.code == normalized }?.displayLabel() ?: normalized
}

private fun buildBackupJson(
    settingsStore: AppSettingsStore,
    ruleStore: RuleStore,
    inboxStore: InboxStore,
    selection: BackupSelection,
    appVersionCode: Long,
    appVersionName: String
): JSONObject {
    val root = JSONObject()
        .put("app", "SMS Shield")
        .put("schemaVersion", BACKUP_SCHEMA_VERSION)
        .put("appVersionCode", appVersionCode)
        .put("appVersionName", appVersionName)
        .put("createdAt", System.currentTimeMillis())

    if (selection.settings) {
        root.put("settings", settingsStore.exportSettings())
    }
    if (selection.rules) {
        root.put("rules", JSONArray().also { arr ->
            ruleStore.getRules().forEach { arr.put(it.toJson()) }
        })
    }

    val messageGroups = JSONObject()
    if (selection.inbox) {
        messageGroups.put("inbox", inboxStore.exportMessages { !it.blocked && !it.archived })
    }
    if (selection.blocked) {
        messageGroups.put("blocked", inboxStore.exportMessages { it.blocked && !it.archived })
    }
    if (selection.archive) {
        messageGroups.put("archive", inboxStore.exportMessages { it.archived })
    }
    if (messageGroups.length() > 0) {
        root.put("messages", messageGroups)
    }

    return root
}

private fun parseBackupJson(root: JSONObject, selection: BackupSelection): ParsedBackup {
    val messages = root.optJSONObject("messages") ?: JSONObject()
    return ParsedBackup(
        schemaVersion = root.optInt("schemaVersion", 1),
        appVersionCode = root.optLongOrNull("appVersionCode"),
        appVersionName = root.optString("appVersionName", "").takeIf { it.isNotBlank() },
        settings = if (selection.settings) root.optJSONObject("settings") else null,
        rules = if (selection.rules) {
            root.optJSONArray("rules")?.toRules().orEmpty()
        } else {
            emptyList()
        },
        messages = buildList {
            if (selection.inbox) {
                addAll(messages.optJSONArray("inbox").toMessages(blocked = false, archived = false))
            }
            if (selection.blocked) {
                addAll(messages.optJSONArray("blocked").toMessages(blocked = true, archived = false))
            }
            if (selection.archive) {
                addAll(messages.optJSONArray("archive").toMessages(blocked = null, archived = true))
            }
        }
    )
}

private fun ParsedBackup.needsNewerSettingsWarning(currentAppVersionCode: Long): Boolean {
    val newerApp = appVersionCode?.let { currentAppVersionCode > 0 && it > currentAppVersionCode } == true
    return settings != null && (schemaVersion > BACKUP_SCHEMA_VERSION || newerApp)
}

private fun findBackupRuleConflicts(ruleStore: RuleStore, rules: List<Rule>): List<RuleConflict> {
    return rules.mapNotNull { imported ->
        if (ruleStore.findDuplicate(imported) != null) {
            null
        } else {
            ruleStore.findConflict(imported)?.let { RuleConflict(imported, it) }
        }
    }
}

private fun applyBackupImport(
    settingsStore: AppSettingsStore,
    ruleStore: RuleStore,
    inboxStore: InboxStore,
    backup: ParsedBackup,
    replaceConflicts: Boolean
): BackupImportResult {
    var settingsImported = false
    var rulesImported = 0
    var ruleDuplicates = 0
    var ruleConflictsSkipped = 0
    var ruleConflictsReplaced = 0

    backup.settings?.let {
        settingsStore.importSettings(it)
        settingsImported = true
    }

    backup.rules.forEach { rule ->
        if (ruleStore.findDuplicate(rule) != null) {
            ruleDuplicates++
            return@forEach
        }

        val conflicts = ruleStore.conflictingRules(rule)
        if (conflicts.isNotEmpty()) {
            if (replaceConflicts) {
                ruleStore.deleteRules(conflicts.map { it.id }.toSet())
                ruleConflictsReplaced += conflicts.size
            } else {
                ruleConflictsSkipped++
                return@forEach
            }
        }

        if (ruleStore.addRuleIfMissing(rule)) {
            rulesImported++
        } else {
            ruleDuplicates++
        }
    }

    val messageResult = inboxStore.importMessages(backup.messages)

    return BackupImportResult(
        settingsImported = settingsImported,
        rulesImported = rulesImported,
        ruleDuplicates = ruleDuplicates,
        ruleConflictsSkipped = ruleConflictsSkipped,
        ruleConflictsReplaced = ruleConflictsReplaced,
        messageResult = messageResult
    )
}

private fun BackupImportResult.summaryText(): String {
    val parts = buildList {
        if (settingsImported) add("settings imported")
        if (rulesImported > 0) add("$rulesImported rule${if (rulesImported == 1) "" else "s"} imported")
        if (messageResult.imported > 0) add("${messageResult.imported} message${if (messageResult.imported == 1) "" else "s"} imported")
        val duplicates = ruleDuplicates + messageResult.duplicates
        if (duplicates > 0) add("$duplicates duplicate${if (duplicates == 1) "" else "s"} not imported")
        if (ruleConflictsSkipped > 0) add("$ruleConflictsSkipped conflicting rule${if (ruleConflictsSkipped == 1) "" else "s"} skipped")
        if (ruleConflictsReplaced > 0) add("$ruleConflictsReplaced current rule${if (ruleConflictsReplaced == 1) "" else "s"} replaced")
    }
    return if (parts.isEmpty()) "Nothing new was imported." else parts.joinToString(". ").replaceFirstChar { it.uppercase() } + "."
}

private fun Rule.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("pattern", pattern)
        .put("action", action.name)
        .put("partialNumber", partialNumber)
        .put("enabled", enabled)
}

private fun JSONArray?.toRules(): List<Rule> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            val type = runCatching { RuleType.valueOf(obj.optString("type")) }.getOrNull() ?: continue
            val action = runCatching { RuleAction.valueOf(obj.optString("action", RuleAction.BLOCK.name)) }
                .getOrDefault(RuleAction.BLOCK)
            val pattern = obj.optString("pattern").trim()
            if (pattern.isBlank()) continue
            add(
                Rule(
                    id = obj.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
                    type = type,
                    pattern = pattern,
                    action = action,
                    partialNumber = obj.optBoolean("partialNumber", false),
                    enabled = obj.optBoolean("enabled", true)
                )
            )
        }
    }
}

private fun JSONArray?.toMessages(blocked: Boolean?, archived: Boolean?): List<SmsMessageRecord> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            val sender = obj.optString("sender")
            val body = obj.optString("body")
            if (sender.isBlank() && body.isBlank()) continue
            add(InboxStore.messageFromJson(obj, blocked = blocked, archived = archived))
        }
    }
}

private fun JSONObject.optLongOrNull(name: String): Long? {
    return if (has(name) && !isNull(name)) optLong(name) else null
}

private fun ruleDisplay(rule: Rule): String {
    return if (rule.type == RuleType.COUNTRY) countryLabelForCode(rule.pattern) else rule.pattern
}

private fun RuleAction.label(): String {
    return name.lowercase(Locale.ROOT)
}

private fun countryCodeForInput(input: String): String? {
    val trimmed = input.trim()
    val cleaned = trimmed.removeSuffix(")").substringAfterLast("(", missingDelimiterValue = trimmed)
    if (cleaned.length == 2) return cleaned.uppercase(Locale.ROOT)
    val callingCode = cleaned.removePrefix("+")
    return countryOptions().firstOrNull {
        it.name.equals(trimmed, ignoreCase = true) ||
            it.displayLabel().equals(trimmed, ignoreCase = true) ||
            it.code.equals(trimmed, ignoreCase = true) ||
            it.callingCode == callingCode
    }?.code
}

private fun readPhoneNumberFromContactUri(context: android.content.Context, uri: Uri): String? {
    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        if (numberIndex < 0) null else cursor.getString(numberIndex)
    }
}

@Composable
private fun ComposeMessageDialog(
    initialRecipient: String,
    initialBody: String,
    error: String?,
    activeSims: List<SimInfo>,
    simSendMode: SimSendMode,
    defaultSimSubscriptionId: Int?,
    sourceMessage: SmsMessageRecord?,
    onDismiss: () -> Unit,
    onSend: (String, String, SimInfo?) -> Unit
) {
    val context = LocalContext.current
    var recipient by remember(initialRecipient) { mutableStateOf(initialRecipient) }
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    var contactError by remember { mutableStateOf<String?>(null) }
    val showSimPicker = shouldShowSendSimPicker(activeSims, simSendMode, defaultSimSubscriptionId, sourceMessage)
    val initialSim = remember(activeSims, simSendMode, defaultSimSubscriptionId, sourceMessage) {
        initialSendSim(activeSims, simSendMode, defaultSimSubscriptionId, sourceMessage)
    }
    var selectedSubscriptionId by remember(showSimPicker, initialSim?.subscriptionId, initialRecipient, initialBody) {
        mutableStateOf(initialSim?.subscriptionId)
    }
    val selectedSim = activeSims.firstOrNull { it.subscriptionId == selectedSubscriptionId } ?: initialSim
    val contactPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val number = readPhoneNumberFromContactUri(context, uri)
                if (number != null) {
                    recipient = number
                    contactError = null
                } else {
                    contactError = "No phone number found for that contact."
                }
            }
        }
    }
    val contactPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            contactPickerLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        } else {
            contactError = "Contacts permission was denied."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Compose Message") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = {
                        recipient = it
                        contactError = null
                    },
                    label = { Text("Recipient") },
                    placeholder = { Text("Tap icon or enter number") },
                    leadingIcon = {
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                    contactPickerLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                                } else {
                                    contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Pick contact",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showSimPicker) {
                    Text("Send with", fontWeight = FontWeight.SemiBold)
                    activeSims.forEach { sim ->
                        SimSendChoice(
                            sim = sim,
                            selected = selectedSubscriptionId == sim.subscriptionId,
                            onClick = { selectedSubscriptionId = sim.subscriptionId }
                        )
                    }
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                if (contactError != null) {
                    Text(contactError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(recipient.trim(), body.trim(), selectedSim) },
                enabled = recipient.isNotBlank() && body.isNotBlank(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SimSendChoice(sim: SimInfo, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_sim_card_24),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(sim.shortLabel, fontWeight = FontWeight.SemiBold)
                sim.descriptiveName?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SettingsView(
    themeMode: ThemeMode,
    accentColor: Color,
    deliveryReportsEnabled: Boolean,
    use24HourTime: Boolean,
    simSendMode: SimSendMode,
    defaultSimSubscriptionId: Int?,
    activeSims: List<SimInfo>,
    autoArchiveDays: Int?,
    autoDeleteBlockedDays: Int?,
    warnBeforeBlockedAutoDelete: Boolean,
    conversationSplitHours: Int?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentColorChange: (Color) -> Unit,
    onDeliveryReportsChange: (Boolean) -> Unit,
    onUse24HourTimeChange: (Boolean) -> Unit,
    onSimSendModeChange: (SimSendMode) -> Unit,
    onDefaultSimSubscriptionIdChange: (Int?) -> Unit,
    onAutoArchiveDaysChange: (Int?) -> Unit,
    onAutoDeleteBlockedDaysChange: (Int?) -> Unit,
    onWarnBeforeBlockedAutoDeleteChange: (Boolean) -> Unit,
    onConversationSplitHoursChange: (Int?) -> Unit,
    backupSelection: BackupSelection,
    backupNotice: String?,
    onBackupSelectionChange: (BackupSelection) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    colors: UiColors
) {
    val accents = listOf(
        Color(0xFF179BFF),
        Color(0xFF00C853),
        Color(0xFFFF9100),
        Color(0xFFFF5252),
        Color(0xFF8A95A3)
    )
    var cleanupNotice by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsGroup(title = "Appearance", colors = colors) {
                ThemeSelector(themeMode, onThemeModeChange, colors)
                AccentSelector(accents, accentColor, onAccentColorChange, colors)
                TimeFormatSelector(use24HourTime, onUse24HourTimeChange, colors)
            }
        }
        item {
            SimSettingsGroup(
                activeSims = activeSims,
                simSendMode = simSendMode,
                defaultSimSubscriptionId = defaultSimSubscriptionId,
                onSimSendModeChange = onSimSendModeChange,
                onDefaultSimSubscriptionIdChange = onDefaultSimSubscriptionIdChange,
                colors = colors
            )
        }
        item {
            SettingsGroup(title = "Conversations", colors = colors) {
                CleanupSettingRow(
                    title = "Split conversations",
                    subtitle = "Start a new chat after this many inactive hours",
                    value = conversationSplitHours,
                    placeholder = "No split",
                    unitLabel = "hour",
                    inputLabel = "Hours",
                    colors = colors,
                    onToggle = {
                        cleanupNotice = if (it == null) "Conversation splitting disabled" else "Conversation grouping updated"
                        onConversationSplitHoursChange(it)
                    },
                    onMissingValue = {
                        cleanupNotice = "Enter number of hours first"
                    }
                )
            }
        }
        item {
            SettingsGroup(title = "Cleanup", colors = colors) {
                CleanupSettingRow(
                    title = "Auto-archive",
                    subtitle = "Archive inbox messages older than this many days",
                    value = autoArchiveDays,
                    placeholder = "Off",
                    colors = colors,
                    onToggle = {
                        cleanupNotice = if (it == null) "Cleanup disabled" else "Processing inbox now"
                        onAutoArchiveDaysChange(it)
                    },
                    onMissingValue = {
                        cleanupNotice = "Enter number of days first"
                    }
                )
                CleanupSettingRow(
                    title = "Delete blocked",
                    subtitle = "Delete blocked messages older than this many days",
                    value = autoDeleteBlockedDays,
                    placeholder = "Off",
                    colors = colors,
                    onToggle = {
                        cleanupNotice = if (it == null) "Cleanup disabled" else "Processing blocked messages"
                        onAutoDeleteBlockedDaysChange(it)
                    },
                    onMissingValue = {
                        cleanupNotice = "Enter number of days first"
                    }
                )
                SettingsSwitchRow(
                    title = "Warn before deleting blocked messages",
                    subtitle = "Ask before automatic blocked cleanup runs",
                    checked = warnBeforeBlockedAutoDelete,
                    colors = colors,
                    onCheckedChange = onWarnBeforeBlockedAutoDeleteChange
                )
            }
        }
        item {
            SettingsGroup(title = "Notification", colors = colors) {
                SettingsSwitchRow(
                    title = "SMS delivery reports",
                    subtitle = "Request a delivery report for\neach SMS you send",
                    checked = deliveryReportsEnabled,
                    colors = colors,
                    onCheckedChange = onDeliveryReportsChange
                )
            }
        }
        item {
            SettingsGroup(title = "Import / Export", colors = colors) {
                ImportExportSettings(
                    selection = backupSelection,
                    notice = backupNotice,
                    colors = colors,
                    onSelectionChange = onBackupSelectionChange,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup
                )
            }
        }
    }

    cleanupNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { cleanupNotice = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text(notice) },
            text = {
                Text(
                    when (notice) {
                        "Processing inbox now" -> "SMS Shield is checking matching messages now."
                        "Processing blocked messages" -> "SMS Shield is checking blocked messages now."
                        "Enter number of days first" -> "Type a day count, then turn the switch on."
                        "Enter number of hours first" -> "Type an hour count, then turn the switch on."
                        "Conversation grouping updated" -> "SMS Shield will split chats after the selected inactivity window."
                        "Conversation splitting disabled" -> "SMS Shield will keep messages with the same contact in one chat."
                        else -> "The setting has been updated."
                    }
                )
            },
            confirmButton = {
                Button(onClick = { cleanupNotice = null }, shape = RoundedCornerShape(14.dp)) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun ImportExportSettings(
    selection: BackupSelection,
    notice: String?,
    colors: UiColors,
    onSelectionChange: (BackupSelection) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Backup Contents", fontWeight = FontWeight.SemiBold)
                Text("Choose what to include or import", color = colors.muted)
            }
            TextButton(onClick = { onSelectionChange(selection.flippedAll()) }) {
                Text(if (selection.allSelected) "Clear All" else "All")
            }
        }
        BackupSwatchRow(
            label = "Settings",
            checked = selection.settings,
            colors = colors,
            onClick = { onSelectionChange(selection.copy(settings = !selection.settings)) }
        )
        BackupSwatchRow(
            label = "Block & allow list",
            checked = selection.rules,
            colors = colors,
            onClick = { onSelectionChange(selection.copy(rules = !selection.rules)) }
        )
        BackupSwatchRow(
            label = "Inbox",
            checked = selection.inbox,
            colors = colors,
            onClick = { onSelectionChange(selection.copy(inbox = !selection.inbox)) }
        )
        BackupSwatchRow(
            label = "Blocked messages",
            checked = selection.blocked,
            colors = colors,
            onClick = { onSelectionChange(selection.copy(blocked = !selection.blocked)) }
        )
        BackupSwatchRow(
            label = "Archive",
            checked = selection.archive,
            colors = colors,
            onClick = { onSelectionChange(selection.copy(archive = !selection.archive)) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onExportBackup,
                enabled = selection.anySelected,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Export JSON")
            }
            Button(
                onClick = onImportBackup,
                enabled = selection.anySelected,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Import JSON")
            }
        }
        if (!notice.isNullOrBlank()) {
            Text(notice, color = colors.muted)
        }
    }
}

@Composable
private fun BackupSwatchRow(
    label: String,
    checked: Boolean,
    colors: UiColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (checked) MaterialTheme.colorScheme.primary else colors.divider,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsGroup(title: String, colors: UiColors, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(shape = RoundedCornerShape(20.dp), color = colors.panel) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SimSettingsGroup(
    activeSims: List<SimInfo>,
    simSendMode: SimSendMode,
    defaultSimSubscriptionId: Int?,
    onSimSendModeChange: (SimSendMode) -> Unit,
    onDefaultSimSubscriptionIdChange: (Int?) -> Unit,
    colors: UiColors
) {
    if (activeSims.size <= 1) return

    SettingsGroup(title = "SIM", colors = colors) {
        SimSettingsOption(
            title = "Ask when sending",
            subtitle = "Choose a SIM\nin the send dialog",
            selected = simSendMode == SimSendMode.ASK,
            colors = colors,
            onClick = {
                onSimSendModeChange(SimSendMode.ASK)
            }
        )
        SimSettingsOption(
            title = "Reply where received",
            subtitle = "Replies use the receiving SIM",
            selected = simSendMode == SimSendMode.REPLY_WHERE_RECEIVED,
            colors = colors,
            onClick = {
                onSimSendModeChange(SimSendMode.REPLY_WHERE_RECEIVED)
            }
        )
        activeSims.forEach { sim ->
            SimSettingsOption(
                title = "Always ${sim.shortLabel}",
                subtitle = sim.descriptiveName ?: "Use ${sim.shortLabel} for outgoing SMS",
                selected = simSendMode == SimSendMode.ALWAYS && defaultSimSubscriptionId == sim.subscriptionId,
                colors = colors,
                onClick = {
                    onDefaultSimSubscriptionIdChange(sim.subscriptionId)
                    onSimSendModeChange(SimSendMode.ALWAYS)
                }
            )
        }
    }
}

@Composable
private fun SimSettingsOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    colors: UiColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_sim_card_24),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else colors.muted
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.muted, maxLines = 2)
        }
        if (selected) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ThemeSelector(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit, colors: UiColors) {
    Column(modifier = Modifier.padding(14.dp)) {
        Text("Theme", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        SegmentedTabs(
            selected = when (themeMode) {
                ThemeMode.LIGHT -> 0
                ThemeMode.DARK -> 1
                ThemeMode.OLED -> 2
            },
            labels = listOf("Light", "Dark", "OLED"),
            accentColor = MaterialTheme.colorScheme.primary,
            colors = colors,
            onSelected = {
                onThemeModeChange(
                    when (it) {
                        0 -> ThemeMode.LIGHT
                        1 -> ThemeMode.DARK
                        else -> ThemeMode.OLED
                    }
                )
            }
        )
    }
}

@Composable
private fun TimeFormatSelector(use24HourTime: Boolean, onUse24HourTimeChange: (Boolean) -> Unit, colors: UiColors) {
    Column(modifier = Modifier.padding(14.dp)) {
        Text("Time Format", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        SegmentedTabs(
            selected = if (use24HourTime) 1 else 0,
            labels = listOf("AM/PM", "24h"),
            accentColor = MaterialTheme.colorScheme.primary,
            colors = colors,
            onSelected = { onUse24HourTimeChange(it == 1) }
        )
    }
}

@Composable
private fun AccentSelector(accents: List<Color>, selected: Color, onAccentColorChange: (Color) -> Unit, colors: UiColors) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text("Accent Color", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            accents.forEach { color ->
                val isSelected = color == selected
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(width = if (isSelected) 3.dp else 1.dp, color = if (isSelected) MaterialTheme.colorScheme.primary else colors.divider, shape = CircleShape)
                        .border(width = if (isSelected) 1.dp else 0.dp, color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent, shape = CircleShape)
                        .clickable { onAccentColorChange(color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, colors: UiColors, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Text(subtitle, color = colors.muted)
        }
    }
}

@Composable
private fun CleanupSettingRow(
    title: String,
    subtitle: String,
    value: Int?,
    placeholder: String,
    unitLabel: String = "day",
    inputLabel: String = "Days",
    colors: UiColors,
    onToggle: (Int?) -> Unit,
    onMissingValue: () -> Unit
) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    var enabled by remember(value) { mutableStateOf(value != null) }
    val draftValue = text.toIntOrNull()?.takeIf { it > 0 }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    if (enabled && draftValue != null) "Active: $draftValue $unitLabel${if (draftValue == 1) "" else "s"}" else "Off",
                    color = colors.muted
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked && draftValue == null) {
                        enabled = false
                        onMissingValue()
                    } else {
                        enabled = checked
                        onToggle(if (checked) draftValue else null)
                    }
                }
            )
        }
        Text(subtitle, color = colors.muted)
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(4)
                text = digits
                if (digits.toIntOrNull()?.takeIf { it > 0 } != value) {
                    enabled = false
                }
            },
            label = { Text(inputLabel) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    colors: UiColors,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EmptyState(title: String, body: String, colors: UiColors, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = colors.panel) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(body, color = colors.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

package com.omix.alleq

import com.omix.alleq.R

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Usb
import com.omix.alleq.ui.PresetSlots
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.omix.alleq.service.AudioProcessorService
import com.omix.alleq.shizuku.ShizukuManager
import com.omix.alleq.ui.ExpandableExpressiveCard
import com.omix.alleq.ui.ExpressiveSlider
import com.omix.alleq.ui.ExpressiveSwitch
import com.omix.alleq.ui.SettingsSheet
import com.omix.alleq.ui.TutorialDialog
import com.omix.alleq.ui.WaveformEqualizerCard
import com.omix.alleq.ui.theme.AllEqTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.math.log10
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var service: AudioProcessorService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? AudioProcessorService.LocalBinder)?.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
        }
    }

    private var shizukuRunning by mutableStateOf(ShizukuManager.isRunning())
    private var shizukuHasPermission by mutableStateOf(ShizukuManager.hasPermission())
    private var isBatteryOptimizationIgnored by mutableStateOf(false)

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        shizukuHasPermission = ShizukuManager.hasPermission()
    }
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        shizukuRunning = ShizukuManager.isRunning()
        shizukuHasPermission = ShizukuManager.hasPermission()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        shizukuRunning = false
        shizukuHasPermission = false
    }

    override fun onResume() {
        super.onResume()
        shizukuRunning = ShizukuManager.isRunning()
        shizukuHasPermission = ShizukuManager.hasPermission()
        isBatteryOptimizationIgnored = ShizukuManager.isBatteryOptimizationIgnored(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isBatteryOptimizationIgnored = ShizukuManager.isBatteryOptimizationIgnored(this)

        val serviceIntent = Intent(this, AudioProcessorService::class.java).apply {
            action = AudioProcessorService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {
        }

        val prefs = getSharedPreferences("alleq_prefs", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        setContent {
            AllEqTheme {
                MainScreen(
                    isFirstRunInitial = isFirstRun,
                    shizukuRunning = shizukuRunning,
                    shizukuHasPermission = shizukuHasPermission,
                    isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
                    onDismissTutorial = {
                        prefs.edit().putBoolean("is_first_run", false).apply()
                    },
                    onRequestShizuku = {
                        ShizukuManager.requestPermission()
                    },
                    onApplyWhitelist = {
                        lifecycleScope.launch {
                            var shizukuSuccess = false
                            if (ShizukuManager.hasPermission()) {
                                val res = withContext(Dispatchers.IO) {
                                    ShizukuManager.applyOriginOsWhitelist(this@MainActivity)
                                }
                                shizukuSuccess = res.isSuccess
                            }

                            val isIgnored = ShizukuManager.isBatteryOptimizationIgnored(this@MainActivity)
                            if (!isIgnored) {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                    startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            } else if (shizukuSuccess || isIgnored) {
                                Toast.makeText(this@MainActivity, getString(R.string.toast_protection_activated), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onToggleMaster = { service?.toggleEnabled() },
                    onSetTemporaryBypass = { bypass -> service?.setTemporaryBypass(bypass) },
                    onBandChanged = { index, gain -> service?.updateBand(index, gain) },
                    onChannelPreAmpChanged = { leftDb, rightDb -> service?.setChannelPreAmpDb(leftDb, rightDb) },
                    onBassBoostChanged = { percent -> service?.setBassBoost(percent) },
                    onPresetSlotSelected = { index -> service?.selectPresetSlot(index) },
                    onCopyDiagnostics = {
                        val report = service?.getDiagnosticsReport() ?: """
                            === BETTER ANDROID SPEAKER ENHANCER SYSTEM DIAGNOSTIC REPORT ===
                            Service Offline.
                            Device: ${Build.MANUFACTURER} ${Build.MODEL}
                            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                            Shizuku: Running=${ShizukuManager.isRunning()}, Granted=${ShizukuManager.hasPermission()}
                        """.trimIndent()

                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Better Android Speaker Enhancer Diagnostic Report", report)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, getString(R.string.toast_report_copied), Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {
        }
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        service = null
        super.onDestroy()
    }
}

@Composable
fun MainScreen(
    isFirstRunInitial: Boolean,
    shizukuRunning: Boolean,
    shizukuHasPermission: Boolean,
    isBatteryOptimizationIgnored: Boolean = false,
    onDismissTutorial: () -> Unit,
    onRequestShizuku: () -> Unit,
    onApplyWhitelist: () -> Unit,
    onToggleMaster: () -> Unit,
    onSetTemporaryBypass: (Boolean) -> Unit,
    onBandChanged: (Int, Float) -> Unit,
    onChannelPreAmpChanged: (Float, Float) -> Unit,
    onBassBoostChanged: (Float) -> Unit,
    onPresetSlotSelected: (Int) -> Unit,
    onCopyDiagnostics: () -> Unit
) {
    val isEnabled by AudioProcessorService.isEnabled.collectAsState()
    val bandGains by AudioProcessorService.bandGains.collectAsState()
    val preAmpDb by AudioProcessorService.preAmpDb.collectAsState()
    val bassBoostPercent by AudioProcessorService.bassBoostPercent.collectAsState()
    val leftChannelVolume by AudioProcessorService.leftChannelVolume.collectAsState()
    val rightChannelVolume by AudioProcessorService.rightChannelVolume.collectAsState()
    val currentAudioDevice by AudioProcessorService.currentAudioDevice.collectAsState()
    val currentAudioDeviceType by AudioProcessorService.currentAudioDeviceType.collectAsState()
    val currentDevicePresetSlots by AudioProcessorService.currentDevicePresetSlots.collectAsState()
    val activePresetName by AudioProcessorService.activePresetName.collectAsState()
    val isTemporaryBypass by AudioProcessorService.isTemporaryBypass.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(isFirstRunInitial) }
    var whitelistApplied by remember(isBatteryOptimizationIgnored) { mutableStateOf(isBatteryOptimizationIgnored) }
    var isBassBoostExpanded by remember { mutableStateOf(true) }
    var isPreampBalanceExpanded by remember { mutableStateOf(true) }

    var lastActiveBassBoostPercent by remember { mutableFloatStateOf(if (bassBoostPercent > 0f) bassBoostPercent else 35f) }

    LaunchedEffect(bassBoostPercent) {
        if (bassBoostPercent > 0f) {
            lastActiveBassBoostPercent = bassBoostPercent
        }
    }

    val leftChannelPercent = effectiveChannelVolumePercent(preAmpDb, leftChannelVolume)
    val rightChannelPercent = effectiveChannelVolumePercent(preAmpDb, rightChannelVolume)

    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = showSettingsSheet) {
        showSettingsSheet = false
    }

    BackHandler(enabled = showTutorial) {
        showTutorial = false
        onDismissTutorial()
    }

    if (showSettingsSheet) {
        SettingsSheet(
            onDismiss = { showSettingsSheet = false },
            shizukuRunning = shizukuRunning,
            shizukuHasPermission = shizukuHasPermission,
            whitelistApplied = whitelistApplied,
            onRequestShizuku = onRequestShizuku,
            onApplyWhitelist = {
                onApplyWhitelist()
                whitelistApplied = true
            },
            onCopyDiagnostics = onCopyDiagnostics,
            onOpenTutorial = {
                showSettingsSheet = false
                showTutorial = true
            }
        )
    }

    if (showTutorial) {
        TutorialDialog(
            shizukuRunning = shizukuRunning,
            shizukuHasPermission = shizukuHasPermission,
            whitelistApplied = whitelistApplied,
            onRequestPermission = onRequestShizuku,
            onApplyWhitelist = {
                onApplyWhitelist()
                whitelistApplied = true
            },
            onDismiss = {
                showTutorial = false
                onDismissTutorial()
            }
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 160.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Better Android Speaker Enhancer",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ACTIVE OUTPUT DEVICE BANNER
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (currentAudioDeviceType) {
                                        "BLUETOOTH" -> Icons.Default.Bluetooth
                                        "WIRED" -> Icons.Default.Headphones
                                        "USB" -> Icons.Default.Usb
                                        else -> Icons.Default.Speaker
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ACTIVE OUTPUT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "EQ Profile Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = getLocalizedAudioDevice(currentAudioDevice),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                PresetSlots(
                    slots = currentDevicePresetSlots,
                    activePreset = activePresetName,
                    onSlotSelected = onPresetSlotSelected,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                val bypassBgColor by animateColorAsState(
                    targetValue = if (isTemporaryBypass) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    label = "bypassBg"
                )
                val bypassBorderColor by animateColorAsState(
                    targetValue = if (isTemporaryBypass) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                    label = "bypassBorder"
                )
                val bypassTextColor by animateColorAsState(
                    targetValue = if (isTemporaryBypass) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "bypassText"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(bypassBgColor)
                        .border(
                            width = 1.dp,
                            color = bypassBorderColor,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSetTemporaryBypass(true)
                                    tryAwaitRelease()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSetTemporaryBypass(false)
                                }
                            )
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isTemporaryBypass) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = "A/B",
                            tint = bypassTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTemporaryBypass) androidx.compose.ui.res.stringResource(R.string.bypass_active) else androidx.compose.ui.res.stringResource(R.string.hold_to_bypass),
                            fontFamily = FontFamily.Default,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = bypassTextColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                WaveformEqualizerCard(
                    bandGains = bandGains,
                    onBandGainChanged = onBandChanged,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                ExpandableExpressiveCard(
                    title = "BASS BOOST",
                    icon = Icons.Default.GraphicEq,
                    expanded = isBassBoostExpanded,
                    onExpandedChange = { isBassBoostExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                    subtitle = "${bassBoostPercent.roundToInt()}%",
                    trailingContent = {
                        ExpressiveSwitch(
                            checked = bassBoostPercent > 0f,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val restore = if (lastActiveBassBoostPercent > 0f) lastActiveBassBoostPercent else 35f
                                    onBassBoostChanged(restore)
                                } else {
                                    onBassBoostChanged(0f)
                                }
                            }
                        )
                    }
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExpressiveSlider(
                        value = bassBoostPercent,
                        onValueChange = { newValue ->
                            val percent = newValue.roundToInt().toFloat()
                            onBassBoostChanged(percent)
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                ExpandableExpressiveCard(
                    title = "PREAMP BALANCE",
                    icon = Icons.Default.Tune,
                    expanded = isPreampBalanceExpanded,
                    onExpandedChange = { isPreampBalanceExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                    subtitle = "L ${leftChannelPercent.roundToInt()}% • R ${rightChannelPercent.roundToInt()}%"
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LEFT CHANNEL",
                                    fontFamily = FontFamily.Default,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${leftChannelPercent.roundToInt()}%",
                                    fontFamily = FontFamily.Default,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            ExpressiveSlider(
                                value = leftChannelPercent,
                                onValueChange = { newValue ->
                                    onChannelPreAmpChanged(channelVolumeDb(newValue), channelVolumeDb(rightChannelPercent))
                                },
                                valueRange = 0f..200f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RIGHT CHANNEL",
                                    fontFamily = FontFamily.Default,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${rightChannelPercent.roundToInt()}%",
                                    fontFamily = FontFamily.Default,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            ExpressiveSlider(
                                value = rightChannelPercent,
                                onValueChange = { newValue ->
                                    onChannelPreAmpChanged(channelVolumeDb(leftChannelPercent), channelVolumeDb(newValue))
                                },
                                valueRange = 0f..200f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box {
                    SmallFloatingActionButton(
                        onClick = { showSettingsSheet = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .size(10.dp)
                            .background(
                                if (shizukuHasPermission) MaterialTheme.colorScheme.primary else Color(0xFFFF9800),
                                CircleShape
                            )
                            .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                    )
                }

                ExtendedFloatingActionButton(
                    onClick = onToggleMaster,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            text = androidx.compose.ui.res.stringResource(
                                if (isEnabled) R.string.service_stop else R.string.service_start
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun getLocalizedAudioDevice(raw: String): String {
    val lower = raw.lowercase()
    return when {
        lower.contains("speaker") -> androidx.compose.ui.res.stringResource(R.string.device_speaker)
        lower.contains("bluetooth") -> androidx.compose.ui.res.stringResource(R.string.device_bluetooth)
        lower.contains("wired") || lower.contains("headphone") -> androidx.compose.ui.res.stringResource(R.string.device_wired)
        lower.contains("usb") -> androidx.compose.ui.res.stringResource(R.string.device_usb)
        else -> raw.lowercase()
    }
}

private fun effectiveChannelVolumePercent(preAmpDb: Float, channelVolume: Float): Float {
    if (channelVolume <= 0.001f) return 0f
    val channelDb = preAmpDb + 20f * log10(channelVolume)
    return (100f * Math.pow(10.0, channelDb.toDouble() / 20.0).toFloat()).coerceIn(0f, 200f)
}

private fun channelVolumeDb(percent: Float): Float {
    if (percent <= 0f) return -48f
    return (20f * log10(percent / 100f)).coerceIn(-48f, 6.02f)
}

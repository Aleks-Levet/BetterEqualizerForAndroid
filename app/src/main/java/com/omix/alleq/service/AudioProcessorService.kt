package com.omix.alleq.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.media.audiofx.AudioEffect
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.omix.alleq.MainActivity
import com.omix.alleq.R
import com.omix.alleq.audio.DynamicsEngine
import com.omix.alleq.logger.AppLogger
import com.omix.alleq.model.EqualizerDefaults
import com.omix.alleq.model.EqualizerPresetSlot
import com.omix.alleq.model.VolumeStepPoint
import com.omix.alleq.telemetry.TelemetryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AudioProcessorService : Service() {

    private val tag = "AudioProcessorService"
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AudioProcessorService = this@AudioProcessorService
    }

    private lateinit var audioManager: AudioManager
    private val engines = ConcurrentHashMap<Int, DynamicsEngine>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
    private var modeChangedListener: AudioManager.OnModeChangedListener? = null
    private var isSpeakerOutput: Boolean = true
    private var resurrectJob: Job? = null
    private var lastResurrectTime: Long = 0L
    private var saveSettingsJob: Job? = null

    companion object {
        const val CHANNEL_ID = "alleq_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.omix.alleq.ACTION_START"
        const val ACTION_STOP = "com.omix.alleq.ACTION_STOP"
        const val ACTION_TOGGLE = "com.omix.alleq.ACTION_TOGGLE"

        private val _isEnabled = MutableStateFlow(true)
        val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

        private val _bandGains = MutableStateFlow(List(EqualizerDefaults.BANDS.size) { 0f })
        val bandGains: StateFlow<List<Float>> = _bandGains.asStateFlow()

        private val _preAmpDb = MutableStateFlow(0f)
        val preAmpDb: StateFlow<Float> = _preAmpDb.asStateFlow()

        private val _bassBoostPercent = MutableStateFlow(0f)
        val bassBoostPercent: StateFlow<Float> = _bassBoostPercent.asStateFlow()

        private val _limiterEnabled = MutableStateFlow(false)
        val limiterEnabled: StateFlow<Boolean> = _limiterEnabled.asStateFlow()

        private val _activePresetName = MutableStateFlow("")
        val activePresetName: StateFlow<String> = _activePresetName.asStateFlow()

        private val _activeSessionsCount = MutableStateFlow(0)
        val activeSessionsCount: StateFlow<Int> = _activeSessionsCount.asStateFlow()

        private val _currentAudioDevice = MutableStateFlow("Speaker")
        val currentAudioDevice: StateFlow<String> = _currentAudioDevice.asStateFlow()

        private val _currentAudioDeviceKey = MutableStateFlow("speaker")
        val currentAudioDeviceKey: StateFlow<String> = _currentAudioDeviceKey.asStateFlow()

        private val _currentAudioDeviceType = MutableStateFlow("SPEAKER")
        val currentAudioDeviceType: StateFlow<String> = _currentAudioDeviceType.asStateFlow()

        private val _currentDevicePresetSlots = MutableStateFlow<List<EqualizerPresetSlot?>>(List(EqualizerDefaults.PRESET_SLOT_COUNT) { null })
        val currentDevicePresetSlots: StateFlow<List<EqualizerPresetSlot?>> = _currentDevicePresetSlots.asStateFlow()

        private val _isTemporaryBypass = MutableStateFlow(false)
        val isTemporaryBypass: StateFlow<Boolean> = _isTemporaryBypass.asStateFlow()

        private val _leftChannelVolume = MutableStateFlow(1.0f)
        val leftChannelVolume: StateFlow<Float> = _leftChannelVolume.asStateFlow()

        private val _rightChannelVolume = MutableStateFlow(1.0f)
        val rightChannelVolume: StateFlow<Float> = _rightChannelVolume.asStateFlow()

        private val _isVolumeAwareEnabled = MutableStateFlow(false)
        val isVolumeAwareEnabled: StateFlow<Boolean> = _isVolumeAwareEnabled.asStateFlow()

        private val _currentVolumeStep = MutableStateFlow(0)
        val currentVolumeStep: StateFlow<Int> = _currentVolumeStep.asStateFlow()

        private val _maxVolumeStep = MutableStateFlow(15)
        val maxVolumeStep: StateFlow<Int> = _maxVolumeStep.asStateFlow()

        private val _volumeCurvePoints = MutableStateFlow<List<VolumeStepPoint>>(emptyList())
        val volumeCurvePoints: StateFlow<List<VolumeStepPoint>> = _volumeCurvePoints.asStateFlow()

        private val _lastAdjustedParameterId = MutableStateFlow("band_0")
        val lastAdjustedParameterId: StateFlow<String> = _lastAdjustedParameterId.asStateFlow()
    }

    private var deviceCallback: AudioDeviceCallback? = null
    private var volumeReceiver: BroadcastReceiver? = null
    private var audioSessionReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        loadSavedSettings()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        initAudioEngines()
        registerPlaybackCallback()
        registerAudioSessionReceiver()
        registerDeviceCallback()
        registerVolumeReceiver()
        registerModeListener()
        updateAudioDeviceDescription()
        AppLogger.log(tag, "AudioProcessorService started successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                toggleEnabled()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        updateNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, AudioProcessorService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stateText = if (_isEnabled.value) getString(R.string.notif_enabled, currentPresetDisplayName()) else getString(R.string.notif_disabled)
        val toggleActionText = if (_isEnabled.value) getString(R.string.notif_action_disable) else getString(R.string.notif_action_enable)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Better Android Speaker Enhancer • $stateText")
            .setContentText(getString(R.string.notif_active_sessions, _activeSessionsCount.value, _currentAudioDevice.value))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_play, toggleActionText, togglePendingIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun updateAudioDeviceDescription() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val activeDevice = devices.firstOrNull { dev ->
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_USB_DEVICE
        } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        isSpeakerOutput = activeDevice == null || activeDevice.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

        val (deviceKey, deviceType, deviceName) = when {
            activeDevice == null || activeDevice.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                Triple("speaker", "SPEAKER", getString(R.string.device_speaker))
            }
            activeDevice.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || activeDevice.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                Triple("wired", "WIRED", getString(R.string.device_wired))
            }
            activeDevice.type == AudioDeviceInfo.TYPE_USB_HEADSET || activeDevice.type == AudioDeviceInfo.TYPE_USB_DEVICE -> {
                Triple("usb", "USB", getString(R.string.device_usb))
            }
            activeDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || activeDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activeDevice.type == AudioDeviceInfo.TYPE_BLE_HEADSET) -> {
                val productName = activeDevice.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
                val address = activeDevice.address?.trim()?.takeIf { it.isNotBlank() } ?: productName
                val cleanName = sanitizeDeviceKeyComponent(productName)
                val cleanAddress = sanitizeDeviceKeyComponent(address)
                val cleanKey = "bt_${cleanName}_$cleanAddress"
                val displayName = if (productName.lowercase().contains("bluetooth")) productName else "Bluetooth: $productName"
                Triple(cleanKey, "BLUETOOTH", displayName)
            }
            else -> {
                Triple("speaker", "SPEAKER", getString(R.string.device_speaker))
            }
        }

        val legacyDeviceKey = activeDevice?.takeIf { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }?.let { device ->
            val productName = device.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
            val address = device.address?.trim()?.takeIf { it.isNotBlank() } ?: productName
            "bt_${sanitizeDeviceKeyComponent(address)}"
        }

        val previousKey = _currentAudioDeviceKey.value
        _currentAudioDevice.value = deviceName
        _currentAudioDeviceType.value = deviceType

        if (previousKey != deviceKey) {
            AppLogger.log(tag, "Audio output switched: $previousKey -> $deviceKey ($deviceName)")
            if (previousKey.isNotEmpty()) {
                writeSettingsToDisk(previousKey)
            }
            _currentAudioDeviceKey.value = deviceKey
            loadDeviceSettings(deviceKey, legacyDeviceKey)
            applyCurrentDspSettingsToEngines()
        }

        AppLogger.log(tag, "Audio output: $deviceName (Key: $deviceKey, Type: $deviceType)")
    }

    private fun sanitizeDeviceKeyComponent(value: String): String =
        value.replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()

    private fun registerVolumeReceiver() {
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    updateVolumeAwareEq()
                    if (audioManager.mode == AudioManager.MODE_NORMAL && engines[0]?.isHealthy() == false) {
                        resurrectSession0IfNeeded()
                    }
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        ContextCompat.registerReceiver(this, volumeReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun unregisterVolumeReceiver() {
        volumeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
            volumeReceiver = null
        }
    }

    private fun registerDeviceCallback() {
        deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                updateAudioDeviceDescription()
                updateNotification()
                resurrectSession0IfNeeded()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                updateAudioDeviceDescription()
                updateNotification()
                resurrectSession0IfNeeded()
            }
        }
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
    }

    private fun registerModeListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modeChangedListener = AudioManager.OnModeChangedListener { mode ->
                handleAudioModeChanged(mode)
            }
            try {
                audioManager.addOnModeChangedListener(mainExecutor, modeChangedListener!!)
                AppLogger.log(tag, "Registered AudioManager.OnModeChangedListener")
            } catch (e: Exception) {
                AppLogger.log(tag, "Failed to register OnModeChangedListener: ${e.message}")
            }
        }
    }

    private fun unregisterModeListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modeChangedListener?.let {
                try {
                    audioManager.removeOnModeChangedListener(it)
                } catch (_: Exception) {}
            }
            modeChangedListener = null
        }
    }

    private fun handleAudioModeChanged(mode: Int) {
        val modeDesc = when (mode) {
            AudioManager.MODE_NORMAL -> "NORMAL"
            AudioManager.MODE_RINGTONE -> "RINGTONE"
            AudioManager.MODE_IN_CALL -> "IN_CALL"
            AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
            AudioManager.MODE_CALL_SCREENING -> "CALL_SCREENING"
            else -> "UNKNOWN ($mode)"
        }
        AppLogger.log(tag, "Audio mode shifted to $modeDesc")

        if (mode == AudioManager.MODE_NORMAL) {
            // delay 300ms to allow audioflinger to restore multimedia mixer thread after call
            resurrectJob?.cancel()
            resurrectJob = serviceScope.launch {
                delay(300)
                resurrectSession0IfNeeded(force = true)
            }
        } else {
            resurrectJob?.cancel()
            updateSessionArbitration()
        }
    }

    private fun resurrectSession0IfNeeded(force: Boolean = false) {
        val shouldBeActive = _isEnabled.value && !_isTemporaryBypass.value
        if (!shouldBeActive) return

        // do not touch session 0 during active phone or voip calls
        val inCall = audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
                audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_RINGTONE
        if (inCall) return

        val now = System.currentTimeMillis()
        if (!force && now - lastResurrectTime < 2000L) {
            // cooldown to prevent rapid resurrection thrashing
            return
        }

        val engine0 = engines[0]
        val isUnhealthy = (engine0 == null) || (!engine0.isHealthy())

        if (force || isUnhealthy) {
            lastResurrectTime = now
            AppLogger.log(tag, "Resurrecting session 0 engine (force=$force, unhealthy=$isUnhealthy)")
            detachSession(0)
            attachSession(0)
            updateSessionArbitration()

            // schedule retry if audioflinger needed more time to settle
            if (engines[0]?.isHealthy() == false) {
                resurrectJob?.cancel()
                resurrectJob = serviceScope.launch {
                    delay(500)
                    if (engines[0]?.isHealthy() == false && _isEnabled.value && audioManager.mode == AudioManager.MODE_NORMAL) {
                        lastResurrectTime = System.currentTimeMillis()
                        AppLogger.log(tag, "Retry resurrecting session 0 engine")
                        detachSession(0)
                        attachSession(0)
                        updateSessionArbitration()
                    }
                }
            }
        }
    }

    private fun initAudioEngines() {
        attachSession(0)
    }

    @SuppressLint("BlockedPrivateApi", "PrivateApi", "DiscouragedPrivateApi")
    private fun extractSessionId(config: AudioPlaybackConfiguration): Int {
        return try {
            val method = config.javaClass.getMethod("getAudioSessionId")
            (method.invoke(config) as? Int) ?: 0
        } catch (_: Exception) {
            try {
                val fieldName = "m" + "SessionId"
                val field = config.javaClass.getDeclaredField(fieldName)
                field.isAccessible = true
                field.getInt(config)
            } catch (_: Exception) {
                0
            }
        }
    }

    private fun updateSessionArbitration(isAudioPlayingOverride: Boolean? = null) {
        val specificSessions = engines.keys.filter { it != 0 }
        val hasSpecificSessions = specificSessions.isNotEmpty()
        val inCall = audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
                audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_RINGTONE ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && audioManager.mode == AudioManager.MODE_CALL_SCREENING)
        val shouldBeActive = _isEnabled.value && !_isTemporaryBypass.value && !inCall

        // prevent double eq: when specific sessions are active, mute session 0
        engines[0]?.setEnabled(shouldBeActive && !hasSpecificSessions)

        // all active specific sessions receive user's DSP settings
        specificSessions.forEach { sid ->
            engines[sid]?.setEnabled(shouldBeActive)
        }

        val playing = isAudioPlayingOverride ?: (audioManager.isMusicActive || hasSpecificSessions)
        _activeSessionsCount.value = if (shouldBeActive && playing) {
            if (hasSpecificSessions) specificSessions.size else 1
        } else 0

        updateNotification()
    }

    private fun registerPlaybackCallback() {
        try {
            playbackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    super.onPlaybackConfigChanged(configs)
                    configs ?: return

                    // filter out voice calls (discord, phone, voip) and inactive tracks
                    val mediaConfigs = configs.filter { config ->
                        val usage = config.audioAttributes?.usage ?: AudioAttributes.USAGE_UNKNOWN
                        // usage 17 corresponds to USAGE_CALL_ASSISTANCE on API 30+
                        val isVoice = usage == AudioAttributes.USAGE_VOICE_COMMUNICATION ||
                                usage == AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING ||
                                usage == 17
                        val isActive = try {
                            val method = config.javaClass.getMethod("isActive")
                            (method.invoke(config) as? Boolean) ?: true
                        } catch (_: Exception) {
                            true
                        }
                        !isVoice && isActive
                    }

                    // collect currently playing audio sessions from configs
                    val activeSessionIds = mediaConfigs
                        .map { extractSessionId(it) }
                        .filter { it > 0 }
                        .toSet()

                    // attach to new media sessions (forces android to disable offload for them)
                    activeSessionIds.forEach { sessionId ->
                        if (!engines.containsKey(sessionId)) {
                            attachSession(sessionId)
                        }
                    }

                    // detach closed media sessions (except global session 0)
                    engines.keys.toList().forEach { sessionId ->
                        if (sessionId != 0 && !activeSessionIds.contains(sessionId)) {
                            detachSession(sessionId)
                        }
                    }

                    val isAudioPlaying = audioManager.isMusicActive || mediaConfigs.isNotEmpty()
                    updateSessionArbitration(isAudioPlaying)

                    if (isAudioPlaying && audioManager.mode == AudioManager.MODE_NORMAL) {
                        if (engines[0]?.isHealthy() == false) {
                            resurrectSession0IfNeeded()
                        }
                    }
                }
            }
            audioManager.registerAudioPlaybackCallback(playbackCallback!!, null)
        } catch (e: Exception) {
            AppLogger.log(tag, "Failed to register AudioPlaybackCallback: ${e.message}")
        }
    }

    private fun registerAudioSessionReceiver() {
        audioSessionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val sessionId = intent?.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioManager.ERROR) ?: return
                if (sessionId <= 0) return
                when (intent.action) {
                    AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                        if (!engines.containsKey(sessionId)) {
                            attachSession(sessionId)
                            updateSessionArbitration()
                        }
                    }
                    AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                        detachSession(sessionId)
                        updateSessionArbitration()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(this, audioSessionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun unregisterAudioSessionReceiver() {
        audioSessionReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            audioSessionReceiver = null
        }
    }

    private fun attachSession(sessionId: Int) {
        if (engines.containsKey(sessionId)) return
        try {
            val engine = DynamicsEngine(sessionId)
            if (sessionId == 0) {
                engine.onControlStatusChanged = { granted ->
                    AppLogger.log(tag, "Session 0 control status: $granted")
                    if (granted && _isEnabled.value && audioManager.mode == AudioManager.MODE_NORMAL) {
                        resurrectJob?.cancel()
                        resurrectJob = serviceScope.launch {
                            delay(200)
                            if (engines[0]?.isHealthy() == false) {
                                resurrectSession0IfNeeded(force = true)
                            }
                        }
                    }
                }
            }

            val hasSpecificSessions = engines.keys.any { it != 0 } || (sessionId != 0)
            val isSessionActive = _isEnabled.value && !_isTemporaryBypass.value && (if (sessionId == 0) !hasSpecificSessions else true)

            val success = engine.initialize(
                bandGains = _bandGains.value,
                preAmpDb = _preAmpDb.value,
                bassBoostPercent = _bassBoostPercent.value,
                limiterEnabled = _limiterEnabled.value,
                isEnabled = isSessionActive
            )
            if (success) {
                engine.updateChannelVolumes(_leftChannelVolume.value, _rightChannelVolume.value)
                engines[sessionId] = engine
                _activeSessionsCount.value = engines.size
                AppLogger.log(tag, "Engine connected to session $sessionId (Total active: ${engines.size})")
            }
        } catch (e: Exception) {
            AppLogger.log(tag, "Failed to attach session $sessionId: ${e.message}")
        }
    }

    private fun detachSession(sessionId: Int) {
        try {
            engines.remove(sessionId)?.release()
            _activeSessionsCount.value = engines.size
            AppLogger.log(tag, "Engine detached from session $sessionId (Total active: ${engines.size})")
        } catch (e: Exception) {
            AppLogger.log(tag, "Failed to detach session $sessionId: ${e.message}")
        }
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("alleq_audio_state", Context.MODE_PRIVATE)
        _isEnabled.value = prefs.getBoolean("is_enabled", true)
        _leftChannelVolume.value = prefs.getFloat("left_channel_volume", 1.0f)
        _rightChannelVolume.value = prefs.getFloat("right_channel_volume", 1.0f)

        TelemetryManager.isDspEnabled = _isEnabled.value

        loadDeviceSettings(_currentAudioDeviceKey.value)
    }

    private fun loadDeviceSettings(deviceKey: String, legacyDeviceKey: String? = null) {
        val prefs = getSharedPreferences("alleq_audio_state", Context.MODE_PRIVATE)
        val prefix = "dev_${deviceKey}_"
        if (legacyDeviceKey != null) {
            val legacyPrefix = "dev_${legacyDeviceKey}_"
            val settingKeys = listOf(
                "pre_amp_db",
                "bass_boost_percent",
                "limiter_enabled",
                "preset_name",
                "band_gains",
                "preset_slots_json",
                "volume_aware_enabled",
                "volume_curve_json"
            )
            val editor = prefs.edit()
            var migratedSettings = false
            settingKeys.forEach { key ->
                val targetKey = "$prefix$key"
                val legacyValue = prefs.all["$legacyPrefix$key"]
                if (!prefs.contains(targetKey) && legacyValue != null) {
                    when (legacyValue) {
                        is Boolean -> editor.putBoolean(targetKey, legacyValue)
                        is Float -> editor.putFloat(targetKey, legacyValue)
                        is String -> editor.putString(targetKey, legacyValue)
                    }
                    migratedSettings = true
                }
            }
            if (migratedSettings) {
                editor.apply()
            }
        }

        val savedSlotsJson = prefs.getString("${prefix}preset_slots_json", null)
        _currentDevicePresetSlots.value = if (savedSlotsJson == null) {
            List(EqualizerDefaults.PRESET_SLOT_COUNT) { null }
        } else {
            EqualizerDefaults.presetSlotsFromJson(savedSlotsJson) ?: run {
                AppLogger.log(tag, "Could not load saved preset slots for output $deviceKey")
                List(EqualizerDefaults.PRESET_SLOT_COUNT) { null }
            }
        }

        if (prefs.contains("${prefix}preset_name")) {
            _preAmpDb.value = prefs.getFloat("${prefix}pre_amp_db", 0f)
            _bassBoostPercent.value = prefs.getFloat("${prefix}bass_boost_percent", 0f)
            _limiterEnabled.value = prefs.getBoolean("${prefix}limiter_enabled", false)
            _activePresetName.value = knownPresetSlotName(prefs.getString("${prefix}preset_name", null))

            val gainsStr = prefs.getString("${prefix}band_gains", null)
            if (gainsStr != null) {
                try {
                    val parsed = gainsStr.split(",").map { it.toFloat() }
                    if (parsed.size == 10) {
                        _bandGains.value = parsed
                    }
                } catch (_: Exception) {}
            }
        } else {
            _preAmpDb.value = prefs.getFloat("pre_amp_db", 0f)
            _bassBoostPercent.value = prefs.getFloat("bass_boost_percent", 0f)
            _limiterEnabled.value = prefs.getBoolean("limiter_enabled", false)
            _activePresetName.value = knownPresetSlotName(prefs.getString("preset_name", null))

            val gainsStr = prefs.getString("band_gains", null)
            if (gainsStr != null) {
                try {
                    val parsed = gainsStr.split(",").map { it.toFloat() }
                    if (parsed.size == 10) {
                        _bandGains.value = parsed
                    }
                } catch (_: Exception) {}
            }
            writeSettingsToDisk(deviceKey)
        }

        _isVolumeAwareEnabled.value = prefs.getBoolean("${prefix}volume_aware_enabled", false)
        val jsonCurve = prefs.getString("${prefix}volume_curve_json", null)
        _volumeCurvePoints.value = VolumeStepPoint.jsonListFromString(jsonCurve)

        TelemetryManager.currentPreset = currentPresetDisplayName()
        updateVolumeAwareEq()
    }

    private fun saveSettings(debounce: Boolean = false) {
        if (debounce) {
            saveSettingsJob?.cancel()
            saveSettingsJob = serviceScope.launch {
                delay(300)
                writeSettingsToDisk()
            }
        } else {
            saveSettingsJob?.cancel()
            writeSettingsToDisk()
        }
    }

    private fun writeSettingsToDisk(deviceKey: String = _currentAudioDeviceKey.value) {
        val prefs = getSharedPreferences("alleq_audio_state", Context.MODE_PRIVATE)
        val prefix = "dev_${deviceKey}_"
        prefs.edit()
            .putBoolean("is_enabled", _isEnabled.value)
            .putFloat("pre_amp_db", _preAmpDb.value)
            .putFloat("bass_boost_percent", _bassBoostPercent.value)
            .putBoolean("limiter_enabled", _limiterEnabled.value)
            .putString("preset_name", _activePresetName.value)
            .putString("band_gains", _bandGains.value.joinToString(","))
            .putFloat("left_channel_volume", _leftChannelVolume.value)
            .putFloat("right_channel_volume", _rightChannelVolume.value)
            .putFloat("${prefix}pre_amp_db", _preAmpDb.value)
            .putFloat("${prefix}bass_boost_percent", _bassBoostPercent.value)
            .putBoolean("${prefix}limiter_enabled", _limiterEnabled.value)
            .putString("${prefix}preset_name", _activePresetName.value)
            .putString("${prefix}band_gains", _bandGains.value.joinToString(","))
            .putString("${prefix}preset_slots_json", EqualizerDefaults.presetSlotsToJson(_currentDevicePresetSlots.value))
            .putBoolean("${prefix}volume_aware_enabled", _isVolumeAwareEnabled.value)
            .putString("${prefix}volume_curve_json", VolumeStepPoint.jsonListToString(_volumeCurvePoints.value))
            .apply()
    }

    private fun applyCurrentDspSettingsToEngines() {
        val gains = _bandGains.value
        val preAmp = _preAmpDb.value
        val bassBoost = _bassBoostPercent.value
        val limiter = _limiterEnabled.value
        engines.values.forEach { engine ->
            engine.updatePreAmpAndLimiter(preAmp, limiter)
            engine.updateBands(gains, bassBoost)
            engine.updateChannelVolumes(_leftChannelVolume.value, _rightChannelVolume.value)
        }
        TelemetryManager.currentPreset = currentPresetDisplayName()
    }

    fun setVolumeAwareEnabled(enabled: Boolean) {
        _isVolumeAwareEnabled.value = enabled
        if (enabled) {
            updateVolumeAwareEq()
        }
        writeSettingsToDisk()
        AppLogger.log(tag, "Volume-Aware EQ: ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    fun setLastAdjustedParameter(paramId: String) {
        _lastAdjustedParameterId.value = paramId
    }

    fun setVolumeCurvePoints(points: List<VolumeStepPoint>) {
        _volumeCurvePoints.value = points.sortedBy { it.stepIndex }
        writeSettingsToDisk()
        if (_isVolumeAwareEnabled.value) {
            updateVolumeAwareEq()
        }
    }

    fun addOrUpdateVolumeStepPoint(point: VolumeStepPoint) {
        val current = _volumeCurvePoints.value.filter { it.stepIndex != point.stepIndex }
        _volumeCurvePoints.value = (current + point).sortedBy { it.stepIndex }
        writeSettingsToDisk()
        if (_isVolumeAwareEnabled.value) {
            updateVolumeAwareEq()
        }
    }

    fun deleteVolumeStepPoint(stepIndex: Int) {
        _volumeCurvePoints.value = _volumeCurvePoints.value.filter { it.stepIndex != stepIndex }
        writeSettingsToDisk()
        if (_isVolumeAwareEnabled.value) {
            updateVolumeAwareEq()
        }
    }

    fun updateVolumeAwareEq() {
        try {
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            _currentVolumeStep.value = curVol
            _maxVolumeStep.value = maxVol

            if (!_isVolumeAwareEnabled.value || _isTemporaryBypass.value || !_isEnabled.value) {
                return
            }

            val defaultPoint = VolumeStepPoint(
                stepIndex = curVol,
                bandGains = _bandGains.value,
                preAmpDb = _preAmpDb.value,
                bassBoostPercent = _bassBoostPercent.value,
                leftChannelVolume = _leftChannelVolume.value,
                rightChannelVolume = _rightChannelVolume.value,
                presetName = _activePresetName.value
            )

            val calculated = VolumeStepPoint.calculateForVolumeStep(_volumeCurvePoints.value, curVol, defaultPoint)

            _bandGains.value = calculated.bandGains
            _preAmpDb.value = calculated.preAmpDb
            _bassBoostPercent.value = calculated.bassBoostPercent
            _leftChannelVolume.value = calculated.leftChannelVolume
            _rightChannelVolume.value = calculated.rightChannelVolume
            _activePresetName.value = calculated.presetName

            applyCurrentDspSettingsToEngines()
        } catch (e: Exception) {
            AppLogger.log(tag, "Error in updateVolumeAwareEq: ${e.message}")
        }
    }

    private fun syncCurrentStateToVolumePointIfNeeded() {
        if (_isVolumeAwareEnabled.value && !_isTemporaryBypass.value) {
            val stepIndex = _currentVolumeStep.value
            val point = VolumeStepPoint(
                stepIndex = stepIndex,
                bandGains = _bandGains.value,
                preAmpDb = _preAmpDb.value,
                bassBoostPercent = _bassBoostPercent.value,
                leftChannelVolume = _leftChannelVolume.value,
                rightChannelVolume = _rightChannelVolume.value,
                presetName = _activePresetName.value
            )
            val currentList = _volumeCurvePoints.value.filter { it.stepIndex != stepIndex }
            _volumeCurvePoints.value = (currentList + point).sortedBy { it.stepIndex }
        }
    }

    fun setLeftChannelVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _leftChannelVolume.value = clamped
        engines.values.forEach { it.updateChannelVolumes(clamped, _rightChannelVolume.value) }
        _lastAdjustedParameterId.value = "balance"
        syncCurrentStateToVolumePointIfNeeded()
        saveActivePresetSlot()
        saveSettings(debounce = true)
    }

    fun setRightChannelVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _rightChannelVolume.value = clamped
        engines.values.forEach { it.updateChannelVolumes(_leftChannelVolume.value, clamped) }
        _lastAdjustedParameterId.value = "balance"
        syncCurrentStateToVolumePointIfNeeded()
        saveActivePresetSlot()
        saveSettings(debounce = true)
    }

    fun setChannelPreAmpDb(leftDb: Float, rightDb: Float) {
        val left = leftDb.coerceIn(-48f, 12f)
        val right = rightDb.coerceIn(-48f, 12f)
        val sharedPreAmpDb = maxOf(left, right).coerceIn(-48f, 6.02f)

        fun channelVolume(channelDb: Float): Float {
            if (channelDb <= -48f) return 0f
            val channelOffsetDb = (channelDb - sharedPreAmpDb).coerceIn(-60f, 0f)
            if (channelOffsetDb <= -60f) return 0f
            return Math.pow(10.0, channelOffsetDb.toDouble() / 20.0).toFloat()
        }

        _preAmpDb.value = sharedPreAmpDb
        _leftChannelVolume.value = channelVolume(left)
        _rightChannelVolume.value = channelVolume(right)
        engines.values.forEach { engine ->
            engine.updatePreAmpAndLimiter(sharedPreAmpDb, _limiterEnabled.value)
            engine.updateChannelVolumes(_leftChannelVolume.value, _rightChannelVolume.value)
        }
        _lastAdjustedParameterId.value = "pre_amp"
        syncCurrentStateToVolumePointIfNeeded()
        saveActivePresetSlot()
        saveSettings(debounce = true)
    }

    fun toggleEnabled() {
        setEnabled(!_isEnabled.value)
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        updateSessionArbitration()
        saveSettings()
        AppLogger.log(tag, "Master switch: ${if (enabled) "ON" else "OFF"}")
        TelemetryManager.isDspEnabled = enabled
    }

    fun setTemporaryBypass(bypass: Boolean) {
        _isTemporaryBypass.value = bypass
        updateSessionArbitration()
        if (bypass) {
            AppLogger.log(tag, "A/B Bypass: raw original sound active")
        } else {
            AppLogger.log(tag, "A/B Bypass: restored preset ${_activePresetName.value}")
        }
    }

    fun updateBands(gains: List<Float>) {
        _bandGains.value = gains
        engines.values.forEach { it.updateBands(gains, _bassBoostPercent.value) }
        syncCurrentStateToVolumePointIfNeeded()
        saveActivePresetSlot()
        saveSettings(debounce = true)
    }

    fun updateBand(index: Int, gain: Float) {
        val current = _bandGains.value.toMutableList()
        if (index in current.indices) {
            current[index] = gain
            _lastAdjustedParameterId.value = "band_$index"
            updateBands(current)
        }
    }

    fun setPreAmp(db: Float) {
        _preAmpDb.value = db
        engines.values.forEach { it.updatePreAmpAndLimiter(db, _limiterEnabled.value) }
        _lastAdjustedParameterId.value = "pre_amp"
        syncCurrentStateToVolumePointIfNeeded()
        saveActivePresetSlot()
        saveSettings(debounce = true)
    }

    fun setBassBoost(percent: Float) {
        _bassBoostPercent.value = percent
        engines.values.forEach { it.updateBands(_bandGains.value, percent) }
        _lastAdjustedParameterId.value = "bass_boost"
        syncCurrentStateToVolumePointIfNeeded()
        saveActivePresetSlot()
        saveSettings(debounce = true)
    }

    fun setLimiterEnabled(enabled: Boolean) {
        _limiterEnabled.value = enabled
        engines.values.forEach { it.updatePreAmpAndLimiter(_preAmpDb.value, enabled) }
        saveActivePresetSlot()
        saveSettings()
    }

    fun selectPresetSlot(index: Int) {
        if (index !in 0 until EqualizerDefaults.PRESET_SLOT_COUNT) return
        val targetSlotName = presetSlotName(index)
        if (_activePresetName.value == targetSlotName) return

        saveActivePresetSlot()
        val slot = _currentDevicePresetSlots.value[index]
        if (slot != null) {
            applyPresetSlotSettings(slot)
        } else {
            savePresetSlotSettings(index, currentPresetSlotSettings())
        }
        val slotName = targetSlotName
        _activePresetName.value = slotName
        TelemetryManager.currentPreset = currentPresetDisplayName()
        saveSettings()
        updateNotification()
        AppLogger.log(tag, "Selected $slotName")
    }

    private fun presetSlotName(index: Int) = "Preset ${index + 1}"

    private fun knownPresetSlotName(name: String?): String =
        name?.takeIf { candidate -> (0 until EqualizerDefaults.PRESET_SLOT_COUNT).any { candidate == presetSlotName(it) } } ?: ""

    private fun currentPresetDisplayName() = _activePresetName.value.ifBlank { "Manual" }

    private fun currentPresetSlotSettings() = EqualizerPresetSlot(
        bandGains = _bandGains.value.toList(),
        preAmpDb = _preAmpDb.value,
        bassBoostPercent = _bassBoostPercent.value,
        limiterEnabled = _limiterEnabled.value,
        leftChannelVolume = _leftChannelVolume.value,
        rightChannelVolume = _rightChannelVolume.value
    )

    private fun savePresetSlotSettings(index: Int, slot: EqualizerPresetSlot) {
        val updatedSlots = _currentDevicePresetSlots.value.toMutableList()
        updatedSlots[index] = slot
        _currentDevicePresetSlots.value = updatedSlots
    }

    private fun saveActivePresetSlot() {
        val index = (0 until EqualizerDefaults.PRESET_SLOT_COUNT)
            .firstOrNull { _activePresetName.value == presetSlotName(it) }
            ?: return
        savePresetSlotSettings(index, currentPresetSlotSettings())
    }

    private fun applyPresetSlotSettings(slot: EqualizerPresetSlot) {
        _bandGains.value = slot.bandGains
        _preAmpDb.value = slot.preAmpDb
        _bassBoostPercent.value = slot.bassBoostPercent
        _limiterEnabled.value = slot.limiterEnabled
        _leftChannelVolume.value = slot.leftChannelVolume
        _rightChannelVolume.value = slot.rightChannelVolume
        engines.values.forEach { engine ->
            engine.updatePreAmpAndLimiter(slot.preAmpDb, slot.limiterEnabled)
            engine.updateBands(slot.bandGains, slot.bassBoostPercent)
            engine.updateChannelVolumes(slot.leftChannelVolume, slot.rightChannelVolume)
        }
    }

    fun getDiagnosticsReport(): String {
        val s0Engine = engines[0]
        val hwStatus = s0Engine?.getEngineStatus() ?: "Session 0: Engine Offline"
        val logs = AppLogger.getLogs().takeLast(40).joinToString("\n")

        return buildString {
            appendLine("=== BETTER ANDROID SPEAKER ENHANCER SYSTEM DIAGNOSTIC REPORT ===")
            appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine("[DEVICE SPECIFICATION]")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Build Display: ${Build.DISPLAY}")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("[AUDIO ROUTING & HARDWARE]")
            appendLine("Active Output: ${_currentAudioDevice.value}")
            appendLine("Active Session Count: ${_activeSessionsCount.value}")
            appendLine("Registered Session IDs: ${engines.keys.toList()}")
            appendLine(hwStatus)
            appendLine()
            appendLine("[DSP CONFIGURATION]")
            appendLine("Master Switch: ${if (_isEnabled.value) "ENABLED" else "DISABLED"}")
            appendLine("Active Preset: ${_activePresetName.value}")
            appendLine("Pre-Amp Gain: ${_preAmpDb.value} dB")
            appendLine("Bass Boost: ${_bassBoostPercent.value}%")
            appendLine("Limiter Status: ${if (_limiterEnabled.value) "ENABLED (0 dBFS)" else "BYPASSED"}")
            appendLine("Band Gains (31Hz..16kHz): ${_bandGains.value.map { it.toInt() }}")
            appendLine()
            appendLine("[RECENT EVENT JOURNAL (LAST 40)]")
            if (logs.isBlank()) {
                appendLine("No events recorded.")
            } else {
                appendLine(logs)
            }
            appendLine("=== END OF REPORT ===")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // ensure foreground service and notification remain active when task is swiped
        if (_isEnabled.value) {
            updateNotification()
        }
    }

    override fun onDestroy() {
        unregisterModeListener()
        unregisterAudioSessionReceiver()
        unregisterVolumeReceiver()
        try { deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) } } catch (_: Exception) {}
        try { playbackCallback?.let { audioManager.unregisterAudioPlaybackCallback(it) } } catch (_: Exception) {}
        engines.values.forEach {
            try { it.release() } catch (_: Exception) {}
        }
        engines.clear()
        try { serviceScope.cancel() } catch (_: Exception) {}
        super.onDestroy()
    }
}

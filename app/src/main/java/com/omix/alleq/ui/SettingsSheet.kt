package com.omix.alleq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.omix.alleq.R
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    shizukuRunning: Boolean,
    shizukuHasPermission: Boolean,
    whitelistApplied: Boolean,
    onRequestShizuku: () -> Unit,
    onApplyWhitelist: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    onOpenTutorial: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "SETTINGS",
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinkCard(
                title = "BACKGROUND PROTECTION",
                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_bg_protection_desc),
                icon = Icons.Default.Shield,
                onClick = onApplyWhitelist
            )

            val isOemAggressive = remember {
                val m = android.os.Build.MANUFACTURER.lowercase()
                m.contains("vivo") || m.contains("iqoo") || m.contains("xiaomi") || m.contains("oppo") || m.contains("realme")
            }
            if (isOemAggressive) {
                Spacer(modifier = Modifier.height(8.dp))
                ExpressiveCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.oem_lock_hint),
                        fontFamily = FontFamily.Default,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinkCard(
                title = "SHIZUKU PRIVILEGES",
                subtitle = if (shizukuHasPermission) {
                    androidx.compose.ui.res.stringResource(R.string.shizuku_granted_desc)
                } else if (shizukuRunning) {
                    androidx.compose.ui.res.stringResource(R.string.shizuku_running_desc)
                } else {
                    androidx.compose.ui.res.stringResource(R.string.shizuku_not_running_desc)
                },
                icon = Icons.Default.Terminal,
                onClick = onRequestShizuku
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinkCard(
                title = "QUICK GUIDE & HELP",
                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_tutorial_desc),
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                onClick = onOpenTutorial
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinkCard(
                title = "SYSTEM DIAGNOSTICS & LOGS",
                subtitle = androidx.compose.ui.res.stringResource(R.string.settings_diag_desc),
                icon = Icons.Default.BugReport,
                onClick = onCopyDiagnostics
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "allEQ • DSP ENGINE",
                        fontFamily = FontFamily.Default,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.footer_subtitle),
                        fontFamily = FontFamily.Default,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

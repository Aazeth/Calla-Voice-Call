package com.aazeth.calla.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.aazeth.calla.R
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import com.aazeth.calla.BuildConfig
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aazeth.calla.webrtc.AudioDspSettings
import com.aazeth.calla.webrtc.AudioRoute
import com.aazeth.calla.webrtc.CallConnectionStatus
import com.aazeth.calla.webrtc.DspPreset
import com.aazeth.calla.webrtc.TurnConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallaApp(
    viewModel: CallViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showAudioDspDialog) {
        SettingsBottomSheet(
            dspSettings = uiState.dspSettings,
            turnConfig = uiState.turnConfig,
            isLoggingEnabled = uiState.isLoggingEnabled,
            onLoggingEnabledChanged = viewModel::setEnableNetworkLogs,
            onDspSettingsChanged = viewModel::updateDspSettings,
            onTurnConfigChanged = viewModel::updateTurnConfig,
            onDismiss = { viewModel.setAudioDspDialogVisible(false) }
        )
    }

    if (uiState.showAudioControlSheet) {
        AudioControlBottomSheet(
            volumeBoostPercent = uiState.volumeBoostPercent,
            currentAudioRoute = uiState.audioRoute,
            onVolumeBoostChange = viewModel::setVolumeBoost,
            onAudioRouteSelected = { route ->
                if (uiState.audioRoute != route) {
                    viewModel.cycleAudioRoute()
                }
            },
            onDismiss = { viewModel.setAudioControlSheetVisible(false) }
        )
    }

    if (uiState.screen == ScreenState.IN_CALL && !uiState.isMinimized) {
        BackHandler {
            viewModel.minimizeCall()
        }
        InCallScreen(
            uiState = uiState,
            onMinimizeCall = viewModel::minimizeCall,
            onToggleMute = viewModel::toggleMute,
            onOpenAudioControlSheet = { viewModel.setAudioControlSheetVisible(true) },
            onOpenSettings = { viewModel.setAudioDspDialogVisible(true) },
            onEndCall = { viewModel.endCall() }
        )
    } else {
        LobbyScreen(
            uiState = uiState,
            onNicknameChange = viewModel::setNickname,
            onBitrateChange = viewModel::setBitrate,
            onJoinCodeChange = viewModel::setJoinCodeInput,
            onCreateRoom = viewModel::createRoom,
            onJoinRoom = viewModel::joinRoom,
            onExpandCall = viewModel::expandCall,
            onEndCall = viewModel::endCall,
            onOpenSettings = { viewModel.setAudioDspDialogVisible(true) },
            onDismissError = viewModel::dismissError
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    uiState: CallUiState,
    onNicknameChange: (String) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onJoinCodeChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onExpandCall: () -> Unit,
    onEndCall: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("calla_header_logo"),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "Calla Logo",
                                modifier = Modifier
                                    .size(36.dp)
                                    .scale(1.4f)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Calla",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("audio_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Audio Processing & Detail",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ongoing Active Call Banner (when minimized)
            AnimatedVisibility(visible = uiState.currentRoomId.isNotBlank()) {
                Surface(
                    onClick = onExpandCall,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ongoing_call_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Ongoing Call • Room #${uiState.currentRoomId}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                val minutes = uiState.callDurationSeconds / 60
                                val seconds = uiState.callDurationSeconds % 60
                                val timeStr = String.format("%02d:%02d", minutes, seconds)
                                Text(
                                    text = "${uiState.peerName.ifBlank { "Connected" }} • $timeStr (Tap to return)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onExpandCall,
                                modifier = Modifier.testTag("expand_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Expand Call Screen",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(
                                onClick = onEndCall,
                                modifier = Modifier.testTag("banner_end_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "End Call",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            // Error banner if any
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissError) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // 1. Profile Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your Profile",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.nickname,
                        onValueChange = onNicknameChange,
                        label = { Text("Display Name") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nickname_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // 2. Bitrate Controller Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Opus Audio Bitrate",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Badge value
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${uiState.bitrateKbps} kbps",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val qualityLabel = when {
                        uiState.bitrateKbps <= 96 -> "Low Bandwidth • Voice Focused"
                        uiState.bitrateKbps <= 160 -> "Balanced • 48 kHz Opus Fullband (Standard)"
                        else -> "Studio Fidelity • High Definition Stereo"
                    }
                    Text(
                        text = qualityLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Slider(
                        value = uiState.bitrateKbps.toFloat(),
                        onValueChange = { onBitrateChange(it.toInt()) },
                        valueRange = 64f..256f,
                        steps = 11,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bitrate_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "64 kbps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "128 kbps (Default)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "256 kbps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 3. Voice Call Rooms Section (Single Merged Card)
            var roomModeIndex by remember { mutableStateOf(0) }
            val roomOptions = listOf("Create Room", "Join Room")

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Voice Call Rooms",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        roomOptions.forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = roomModeIndex == index,
                                onClick = { roomModeIndex = index },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = roomOptions.size)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (roomModeIndex == 0) {
                        // Create Room Mode
                        Text(
                            text = "Generates a unique 6-digit code for your partner to join a 1-on-1 voice call instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onCreateRoom,
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("create_room_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (uiState.isLoading && uiState.isHost) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhoneInTalk,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Create Room",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    } else {
                        // Join Room Mode
                        Text(
                            text = "Enter the 6-digit code shared by the host to connect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = uiState.joinCodeInput,
                            onValueChange = { input ->
                                val digitsOnly = input.filter { it.isDigit() }.take(6)
                                onJoinCodeChange(digitsOnly)
                            },
                            label = { Text("6-Digit Code") },
                            placeholder = { Text("e.g. 482915") },
                            supportingText = {
                                Text(
                                    text = "${uiState.joinCodeInput.length}/6 digits",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.joinCodeInput.length == 6) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    if (uiState.joinCodeInput.length == 6) onJoinRoom()
                                }
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        coroutineScope.launch {
                                            delay(300)
                                            scrollState.animateScrollTo(scrollState.maxValue)
                                        }
                                    }
                                }
                                .testTag("join_code_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FilledTonalButton(
                            onClick = onJoinRoom,
                            enabled = !uiState.isLoading && uiState.joinCodeInput.length == 6,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("join_room_button")
                        ) {
                            if (uiState.isLoading && !uiState.isHost) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Join Room",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun InCallScreen(
    uiState: CallUiState,
    onMinimizeCall: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenAudioControlSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section Container: Header + Central Group closely connected
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header: Top Bar with statusBarsPadding, 56.dp height, 16.dp horizontal padding, CenterVertically
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Top-Left: Minimize Button (24.dp icon with 48.dp touch target)
                        IconButton(
                            onClick = onMinimizeCall,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("minimize_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Minimize Call",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Top-Center: Ping Indicator Mini Pill Badge
                        val pingVal = uiState.pingMs
                        val (pingDotColor, pingTextColor, pingText) = when {
                            pingVal == null -> Triple(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurfaceVariant, "Ping: -- ms")
                            pingVal < 100 -> Triple(Color(0xFF4CAF50), MaterialTheme.colorScheme.onSurface, "Ping: ${pingVal}ms")
                            pingVal <= 250 -> Triple(Color(0xFFFFB300), MaterialTheme.colorScheme.onSurface, "Ping: ${pingVal}ms")
                            else -> Triple(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onSurface, "Ping: ${pingVal}ms")
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("ping_indicator_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(pingDotColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = pingText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = pingTextColor
                                )
                            }
                        }

                        // Top-Right: Settings Button (24.dp icon with 48.dp touch target)
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("incall_audio_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Audio Processing & Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Compact Central Column: Room Code Card, Connection Status Badge, Avatar Visualizer, Peer Name, Duration Timer, Bitrate Chip
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Room Code Card (Auto-hidden once partner joins)
                    val partnerJoined = uiState.callStatus == CallConnectionStatus.CONNECTED || (uiState.peerName.isNotBlank() && !uiState.peerName.startsWith("Waiting"))
                    AnimatedVisibility(visible = !partnerJoined) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "ROOM CODE",
                                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = uiState.currentRoomId,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(uiState.currentRoomId))
                                        Toast.makeText(context, "Room code copied: ${uiState.currentRoomId}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("copy_room_code_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Room Code",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Connection status chip
                    val statusDotColor = when (uiState.callStatus) {
                        CallConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                        CallConnectionStatus.CONNECTING -> MaterialTheme.colorScheme.tertiary
                        CallConnectionStatus.FAILED,
                        CallConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.callStatusText.ifBlank { "Connecting..." },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Pulsing Ripple Wave Visualizer
                    PulsingAvatarVisualizer(
                        audioLevel = uiState.audioLevel,
                        isPeerMuted = uiState.isPeerMuted,
                        isConnected = uiState.callStatus == CallConnectionStatus.CONNECTED
                    )

                    // Peer Name + Muted Microphone Icon / Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.peerName.ifBlank { if (uiState.isHost) "Waiting for guest..." else "Host" },
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.isPeerMuted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MicOff,
                                        contentDescription = "Peer Muted",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Muted",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // Call Duration Timer
                    val minutes = uiState.callDurationSeconds / 60
                    val seconds = uiState.callDurationSeconds % 60
                    val formattedTime = String.format("%02d:%02d", minutes, seconds)

                    Text(
                        text = if (uiState.callStatus == CallConnectionStatus.CONNECTED) formattedTime else "00:00",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (uiState.callStatus == CallConnectionStatus.CONNECTED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )

                    // Active Audio Quality Info
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = "${uiState.bitrateKbps} kbps • Opus 48 kHz",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Symmetrical 3 Bottom Action Buttons (Mute, End Call, Speaker)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Mute / Unmute Button Column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FilledTonalIconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(60.dp)
                            .testTag("mute_mic_button"),
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (uiState.isMicMuted) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (uiState.isMicMuted) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (uiState.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (uiState.isMicMuted) "Unmute Mic" else "Mute Mic",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.height(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.isMicMuted) "Unmute" else "Mute",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 2. End Call Button Column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onEndCall,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("end_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.height(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "End Call",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 3. Audio / Speaker Button Column (Simplified Label "Speaker" / "Earpiece" / "Headset")
                val (routeIcon, routeLabel) = when (uiState.audioRoute) {
                    AudioRoute.SPEAKER -> Pair(Icons.AutoMirrored.Filled.VolumeUp, "Speaker")
                    AudioRoute.EARPIECE -> Pair(Icons.Default.PhoneInTalk, "Earpiece")
                    AudioRoute.HEADSET -> Pair(Icons.Default.Headphones, "Headset")
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FilledTonalIconButton(
                        onClick = onOpenAudioControlSheet,
                        modifier = Modifier
                            .size(60.dp)
                            .testTag("speaker_toggle_button"),
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (uiState.volumeBoostPercent > 100) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (uiState.volumeBoostPercent > 100) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    ) {
                        Icon(
                            imageVector = routeIcon,
                            contentDescription = "$routeLabel / Audio Controls",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.height(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = routeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PulsingAvatarVisualizer(
    audioLevel: Float,
    isPeerMuted: Boolean,
    isConnected: Boolean
) {
    val transition = rememberInfiniteTransition(label = "pulse_ripple")
    val animProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_progress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(170.dp)
    ) {
        // Multi-layered pulsing ripples when audioLevel > 0 and connected
        if (isConnected && audioLevel > 0.05f) {
            val scaleFactor1 = 1.0f + (animProgress * 0.45f * (0.5f + audioLevel * 0.5f))
            val alpha1 = ((1f - animProgress) * 0.4f * (0.5f + audioLevel * 0.5f)).coerceIn(0f, 1f)

            val scaleFactor2 = 1.0f + (((animProgress + 0.5f) % 1.0f) * 0.45f * (0.5f + audioLevel * 0.5f))
            val alpha2 = ((1f - ((animProgress + 0.5f) % 1.0f)) * 0.4f * (0.5f + audioLevel * 0.5f)).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = scaleFactor1
                        scaleY = scaleFactor1
                        this.alpha = alpha1
                    }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = scaleFactor2
                        scaleY = scaleFactor2
                        this.alpha = alpha2
                    }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
            )
        }

        // Main Central Avatar Container
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(
                    width = 3.dp,
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Peer Avatar",
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            // Muted Microphone badge overlay at bottom-right corner of avatar
            if (isPeerMuted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Peer Muted",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioControlBottomSheet(
    volumeBoostPercent: Int,
    currentAudioRoute: AudioRoute,
    onVolumeBoostChange: (Int) -> Unit,
    onAudioRouteSelected: (AudioRoute) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio & Volume Controls",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Audio Controls")
                }
            }

            // Volume Boost Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Volume Boost",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "$volumeBoostPercent%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Slider(
                        value = volumeBoostPercent.toFloat(),
                        onValueChange = { newValue ->
                            val stepped = ((newValue / 10f).let { kotlin.math.round(it) } * 10).toInt()
                            onVolumeBoostChange(stepped)
                        },
                        valueRange = 0f..500f,
                        steps = 49,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audio_sheet_volume_boost_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("300% (Default)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("500% (5x)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Audio Routing Mode Selection
            Column {
                Text(
                    text = "Audio Output Route",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val routes = listOf(
                        Triple(AudioRoute.SPEAKER, "Speaker", Icons.AutoMirrored.Filled.VolumeUp),
                        Triple(AudioRoute.EARPIECE, "Earpiece", Icons.Default.PhoneInTalk),
                        Triple(AudioRoute.HEADSET, "Headset", Icons.Default.Headphones)
                    )

                    routes.forEach { (route, label, icon) ->
                        val isSelected = currentAudioRoute == route
                        Surface(
                            onClick = { onAudioRouteSelected(route) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    dspSettings: AudioDspSettings,
    turnConfig: TurnConfig,
    isLoggingEnabled: Boolean,
    onLoggingEnabledChanged: (Boolean) -> Unit,
    onDspSettingsChanged: (AudioDspSettings) -> Unit,
    onTurnConfigChanged: (TurnConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var customServerInput by remember(turnConfig.customServer) { mutableStateOf(turnConfig.customServer) }
    var customUserInput by remember(turnConfig.customUsername) { mutableStateOf(turnConfig.customUsername) }
    var customPassInput by remember(turnConfig.customCredential) { mutableStateOf(turnConfig.customCredential) }

    var showLogViewerSheet by remember { mutableStateOf(false) }

    if (showLogViewerSheet) {
        NetworkLogViewerSheet(
            onDismiss = { showLogViewerSheet = false }
        )
    }

    val handleDismiss: () -> Unit = {
        onTurnConfigChanged(
            turnConfig.copy(
                customServer = customServerInput,
                customUsername = customUserInput,
                customCredential = customPassInput
            )
        )
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = handleDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                IconButton(onClick = handleDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Settings"
                    )
                }
            }

            // Section 1: Audio DSP
            Text(
                text = "Audio DSP Processing",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )

            val selectedPresetIndex = when (dspSettings.preset) {
                DspPreset.RAW -> 0
                DspPreset.BALANCED -> 1
                DspPreset.CUSTOM -> 2
            }

            val presetOptions = listOf("Raw Audio", "Balanced", "Custom")

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                presetOptions.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedPresetIndex == index,
                        onClick = {
                            when (index) {
                                0 -> onDspSettingsChanged(
                                    dspSettings.copy(
                                        noiseSuppression = false,
                                        echoCancellation = false,
                                        autoGainControl = false,
                                        highpassFilter = false,
                                        preset = DspPreset.RAW
                                    )
                                )
                                1 -> onDspSettingsChanged(
                                    dspSettings.copy(
                                        noiseSuppression = true,
                                        echoCancellation = true,
                                        autoGainControl = true,
                                        highpassFilter = true,
                                        preset = DspPreset.BALANCED
                                    )
                                )
                                2 -> onDspSettingsChanged(dspSettings.copy(preset = DspPreset.CUSTOM))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = presetOptions.size)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // DSP Switches Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DspToggleItem(
                        title = "Noise Suppression",
                        desc = "Attenuates ambient and background noise",
                        checked = dspSettings.noiseSuppression,
                        onCheckedChange = {
                            onDspSettingsChanged(
                                dspSettings.copy(
                                    noiseSuppression = it,
                                    preset = DspPreset.CUSTOM
                                )
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    DspToggleItem(
                        title = "Echo Cancellation",
                        desc = "Prevents speaker and earpiece acoustic feedback",
                        checked = dspSettings.echoCancellation,
                        onCheckedChange = {
                            onDspSettingsChanged(
                                dspSettings.copy(
                                    echoCancellation = it,
                                    preset = DspPreset.CUSTOM
                                )
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    DspToggleItem(
                        title = "Auto Gain Control",
                        desc = "Dynamically balances input microphone levels",
                        checked = dspSettings.autoGainControl,
                        onCheckedChange = {
                            onDspSettingsChanged(
                                dspSettings.copy(
                                    autoGainControl = it,
                                    preset = DspPreset.CUSTOM
                                )
                            )
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    DspToggleItem(
                        title = "High-pass Filter",
                        desc = "Eliminates low-frequency hum and rumble",
                        checked = dspSettings.highpassFilter,
                        onCheckedChange = {
                            onDspSettingsChanged(
                                dspSettings.copy(
                                    highpassFilter = it,
                                    preset = DspPreset.CUSTOM
                                )
                            )
                        }
                    )
                }
            }

            // Divider
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Section 2: Network Relay
            Text(
                text = "Network Relay",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Use Custom TURN Server",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (turnConfig.useCustomTurn) "Connecting via custom server" else "Using built-in OpenRelay automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = turnConfig.useCustomTurn,
                    onCheckedChange = { checked ->
                        val updated = turnConfig.copy(
                            useCustomTurn = checked,
                            customServer = customServerInput,
                            customUsername = customUserInput,
                            customCredential = customPassInput
                        )
                        onTurnConfigChanged(updated)
                    }
                )
            }

            AnimatedVisibility(visible = turnConfig.useCustomTurn) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customServerInput,
                        onValueChange = {
                            customServerInput = it
                            onTurnConfigChanged(turnConfig.copy(customServer = it))
                        },
                        label = { Text("TURN Server URL") },
                        placeholder = { Text("turn:turn.example.com:3478") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = customUserInput,
                        onValueChange = {
                            customUserInput = it
                            onTurnConfigChanged(turnConfig.copy(customUsername = it))
                        },
                        label = { Text("Username (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = customPassInput,
                        onValueChange = {
                            customPassInput = it
                            onTurnConfigChanged(turnConfig.copy(customCredential = it))
                        },
                        label = { Text("Password / Credential (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            customServerInput = ""
                            customUserInput = ""
                            customPassInput = ""
                            onTurnConfigChanged(
                                TurnConfig(
                                    useCustomTurn = false,
                                    customServer = "",
                                    customUsername = "",
                                    customCredential = ""
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset to Built-in OpenRelay")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Enable Network Logs",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isLoggingEnabled) "Recording WebSocket, WebRTC & ICE events" else "Disabled to save memory",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isLoggingEnabled,
                    onCheckedChange = onLoggingEnabledChanged,
                    modifier = Modifier.testTag("enable_network_logs_switch")
                )
            }

            AnimatedVisibility(visible = isLoggingEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showLogViewerSheet = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Logs", style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = {
                                val formatted = com.aazeth.calla.util.NetworkLogger.getFormattedLogs()
                                clipboardManager.setText(AnnotatedString(formatted))
                                Toast.makeText(context, "Network logs copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Logs", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            com.aazeth.calla.util.NetworkLogger.clear()
                            Toast.makeText(context, "Network logs cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear Logs", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer with dynamic version string from BuildConfig
            val dynamicVersionName = BuildConfig.VERSION_NAME
            Text(
                text = "Calla v$dynamicVersionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkLogViewerSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val logs by com.aazeth.calla.util.NetworkLogger.logs.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size, autoScroll) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Network Logs",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${logs.size} / 500",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Logs")
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = autoScroll,
                    onClick = { autoScroll = !autoScroll },
                    label = { Text("Auto-scroll", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (autoScroll) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { com.aazeth.calla.util.NetworkLogger.clear() },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = {
                            val formatted = com.aazeth.calla.util.NetworkLogger.getFormattedLogs()
                            clipboardManager.setText(AnnotatedString(formatted))
                            Toast.makeText(context, "Network logs copied", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Monospace Log Terminal Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No network logs recorded yet.\nConnect or create a room to capture logs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            val tagColor = when (log.tag) {
                                "WebSocket" -> MaterialTheme.colorScheme.primary
                                "WebRTC" -> MaterialTheme.colorScheme.secondary
                                "ICE" -> MaterialTheme.colorScheme.tertiary
                                "Network" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "[${log.timestamp}] ",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "[${log.tag}] ",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = tagColor
                                )
                                Text(
                                    text = log.message,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DspToggleItem(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

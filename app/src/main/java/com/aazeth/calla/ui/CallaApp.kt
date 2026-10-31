package com.aazeth.calla.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aazeth.calla.webrtc.AudioDspSettings
import com.aazeth.calla.webrtc.AudioRoute
import com.aazeth.calla.webrtc.CallConnectionStatus
import com.aazeth.calla.webrtc.TurnConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallaApp(
    viewModel: CallViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showAudioDspDialog) {
        AudioDspSettingsDialog(
            dspSettings = uiState.dspSettings,
            turnConfig = uiState.turnConfig,
            onDspSettingsChanged = viewModel::updateDspSettings,
            onTurnConfigChanged = viewModel::updateTurnConfig,
            onDismiss = { viewModel.setAudioDspDialogVisible(false) }
        )
    }

    when (uiState.screen) {
        ScreenState.LOBBY -> {
            LobbyScreen(
                uiState = uiState,
                onNicknameChange = viewModel::setNickname,
                onBitrateChange = viewModel::setBitrate,
                onJoinCodeChange = viewModel::setJoinCodeInput,
                onCreateRoom = viewModel::createRoom,
                onJoinRoom = viewModel::joinRoom,
                onOpenSettings = { viewModel.setAudioDspDialogVisible(true) },
                onDismissError = viewModel::dismissError
            )
        }
        ScreenState.IN_CALL -> {
            BackHandler {
                viewModel.endCall()
            }
            InCallScreen(
                uiState = uiState,
                onToggleMute = viewModel::toggleMute,
                onToggleSpeaker = viewModel::cycleAudioRoute,
                onOpenSettings = { viewModel.setAudioDspDialogVisible(true) },
                onEndCall = { viewModel.endCall() }
            )
        }
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
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
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
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("audio_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Audio Processing & Detail",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                windowInsets = WindowInsets(top = (statusBarTopPadding / 2)),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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

            // 3. Room Actions Section
            Text(
                text = "Voice Call Rooms",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Create Room Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Host a New Room",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
                }
            }

            // Join Room Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Join Existing Room",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun InCallScreen(
    uiState: CallUiState,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Room Code & Action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ROOM CODE",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = uiState.currentRoomId,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        FilledTonalIconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(uiState.currentRoomId))
                                Toast.makeText(context, "Room code copied: ${uiState.currentRoomId}", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("copy_room_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Room Code",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        FilledTonalIconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("incall_audio_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Audio Processing & Settings",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
            }

            // Center: Peer Avatar, Peer Name, Timer, and Bitrate Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large Avatar
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(
                            width = 3.dp,
                            color = if (uiState.callStatus == CallConnectionStatus.CONNECTED) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
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
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = uiState.peerName.ifBlank { if (uiState.isHost) "Waiting for guest..." else "Host" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

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

                Spacer(modifier = Modifier.height(14.dp))

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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Controls Bar: Mute, End Call, Speaker
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute / Unmute Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        Text(
                            text = if (uiState.isMicMuted) "Unmute" else "Mute",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // End Call Button (Red, prominent)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        Text(
                            text = "End Call",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Smart Audio Routing Toggle Button (Speaker / Earpiece / Headset)
                    val (routeIcon, routeLabel) = when (uiState.audioRoute) {
                        AudioRoute.SPEAKER -> Pair(Icons.AutoMirrored.Filled.VolumeUp, "Speaker")
                        AudioRoute.EARPIECE -> Pair(Icons.Default.PhoneInTalk, "Earpiece")
                        AudioRoute.HEADSET -> Pair(Icons.Default.Headphones, "Headset")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledTonalIconButton(
                            onClick = onToggleSpeaker,
                            modifier = Modifier
                                .size(60.dp)
                                .testTag("speaker_toggle_button"),
                            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (uiState.audioRoute == AudioRoute.SPEAKER) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                contentColor = if (uiState.audioRoute == AudioRoute.SPEAKER) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        ) {
                            Icon(
                                imageVector = routeIcon,
                                contentDescription = routeLabel,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = routeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioDspSettingsDialog(
    dspSettings: AudioDspSettings,
    turnConfig: TurnConfig,
    onDspSettingsChanged: (AudioDspSettings) -> Unit,
    onTurnConfigChanged: (TurnConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    var customServerInput by remember(turnConfig.customServer) { mutableStateOf(turnConfig.customServer) }
    var customUserInput by remember(turnConfig.customUsername) { mutableStateOf(turnConfig.customUsername) }
    var customPassInput by remember(turnConfig.customCredential) { mutableStateOf(turnConfig.customCredential) }

    AlertDialog(
        onDismissRequest = {
            onTurnConfigChanged(
                turnConfig.copy(
                    customServer = customServerInput,
                    customUsername = customUserInput,
                    customCredential = customPassInput
                )
            )
            onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Audio DSP", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("TURN Relay", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                if (selectedTab == 0) {
                    val isRawMode = !dspSettings.noiseSuppression &&
                            !dspSettings.echoCancellation &&
                            !dspSettings.autoGainControl &&
                            !dspSettings.highpassFilter

                    // Status Banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isRawMode) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isRawMode) "Mode: Raw Audio (Full Detail)" else "Mode: Filtered Audio (Voice Compression)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isRawMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isRawMode) {
                                    "All WebRTC DSP filters disabled. Microphone captures raw uncompressed sound including breath and subtle vocal nuances."
                                } else {
                                    "WebRTC acoustic filters active. Background noise, acoustic echoes, and rumble are attenuated."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRawMode) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Quick Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDspSettingsChanged(AudioDspSettings(false, false, false, false))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("All OFF (Raw)", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                onDspSettingsChanged(AudioDspSettings(true, true, true, true))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("All ON (Filtered)", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider()

                    // DSP Toggles
                    DspToggleItem(
                        title = "Noise Suppression",
                        desc = "Attenuates ambient and background noise",
                        checked = dspSettings.noiseSuppression,
                        onCheckedChange = { onDspSettingsChanged(dspSettings.copy(noiseSuppression = it)) }
                    )

                    DspToggleItem(
                        title = "Echo Cancellation",
                        desc = "Prevents speaker and earpiece acoustic feedback",
                        checked = dspSettings.echoCancellation,
                        onCheckedChange = { onDspSettingsChanged(dspSettings.copy(echoCancellation = it)) }
                    )

                    DspToggleItem(
                        title = "Auto Gain Control",
                        desc = "Dynamically balances input microphone levels",
                        checked = dspSettings.autoGainControl,
                        onCheckedChange = { onDspSettingsChanged(dspSettings.copy(autoGainControl = it)) }
                    )

                    DspToggleItem(
                        title = "High-pass Filter",
                        desc = "Eliminates low-frequency hum and rumble",
                        checked = dspSettings.highpassFilter,
                        onCheckedChange = { onDspSettingsChanged(dspSettings.copy(highpassFilter = it)) }
                    )
                } else {
                    // TURN Relay Section
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Cross-Network Connectivity",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Relays encrypted audio packets across different cellular carriers (CGNAT) and Wi-Fi networks when direct P2P NAT traversal is blocked.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (!turnConfig.useCustomTurn) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (!turnConfig.useCustomTurn) "OpenRelay Active (Default)" else "OpenRelay (Standby)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (!turnConfig.useCustomTurn) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Built-in public TURN cluster (openrelay.metered.ca:80/443 UDP & TCP). Free and enabled by default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!turnConfig.useCustomTurn) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Use Custom TURN Server",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Use private Coturn or custom TURN relay credentials",
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

                    if (turnConfig.useCustomTurn) {
                        OutlinedTextField(
                            value = customServerInput,
                            onValueChange = {
                                customServerInput = it
                                onTurnConfigChanged(turnConfig.copy(customServer = it))
                            },
                            label = { Text("TURN Server URL") },
                            placeholder = { Text("turn:turn.example.com:3478") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = customUserInput,
                            onValueChange = {
                                customUserInput = it
                                onTurnConfigChanged(turnConfig.copy(customUsername = it))
                            },
                            label = { Text("Username (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = customPassInput,
                            onValueChange = {
                                customPassInput = it
                                onTurnConfigChanged(turnConfig.copy(customCredential = it))
                            },
                            label = { Text("Password / Credential (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
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
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset to OpenRelay Default")
                        }
                    }
                }

                HorizontalDivider()

                // App info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Calla",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "v1.1.0 (arm64-v8a)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTurnConfigChanged(
                        turnConfig.copy(
                            customServer = customServerInput,
                            customUsername = customUserInput,
                            customCredential = customPassInput
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Close")
            }
        }
    )
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

package com.aazeth.calla.webrtc

data class TurnConfig(
    val useCustomTurn: Boolean = false,
    val customServer: String = "",
    val customUsername: String = "",
    val customCredential: String = ""
)

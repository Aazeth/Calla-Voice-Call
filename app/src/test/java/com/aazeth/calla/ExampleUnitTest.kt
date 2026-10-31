package com.aazeth.calla

import com.aazeth.calla.util.SdpUtil
import com.aazeth.calla.webrtc.AudioDspSettings
import com.aazeth.calla.webrtc.AudioRoute
import com.aazeth.calla.webrtc.TurnConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun sdpUtil_insertsOpusBitrateWhenFmtpMissing() {
        val originalSdp = "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2\r\na=sendrecv"
        val modifiedSdp = SdpUtil.setOpusBitrate(originalSdp, 128)

        assertTrue(modifiedSdp.contains("a=fmtp:111 maxaveragebitrate=128000;stereo=1;sprop-stereo=1;useinbandfec=1"))
    }

    @Test
    fun sdpUtil_updatesExistingOpusBitrate() {
        val originalSdp = "v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2\r\na=fmtp:111 minptime=10;useinbandfec=1;maxaveragebitrate=64000\r\na=sendrecv"
        val modifiedSdp = SdpUtil.setOpusBitrate(originalSdp, 256)

        assertTrue(modifiedSdp.contains("maxaveragebitrate=256000"))
        assertFalse(modifiedSdp.contains("maxaveragebitrate=64000"))
    }

    @Test
    fun sdpUtil_clampsOutOfRangeBitrates() {
        val originalSdp = "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=rtpmap:111 opus/48000/2"
        val lowClamped = SdpUtil.setOpusBitrate(originalSdp, 32)
        assertTrue(lowClamped.contains("maxaveragebitrate=64000"))

        val highClamped = SdpUtil.setOpusBitrate(originalSdp, 500)
        assertTrue(highClamped.contains("maxaveragebitrate=256000"))
    }

    @Test
    fun audioDspSettings_defaultsToRawMode() {
        val defaultDsp = AudioDspSettings()
        // Default must be raw audio (all filters off for maximum detail)
        assertFalse(defaultDsp.noiseSuppression)
        assertFalse(defaultDsp.echoCancellation)
        assertFalse(defaultDsp.autoGainControl)
        assertFalse(defaultDsp.highpassFilter)
    }

    @Test
    fun audioRoute_enumHasExpectedValues() {
        val routes = AudioRoute.values()
        assertTrue(routes.contains(AudioRoute.SPEAKER))
        assertTrue(routes.contains(AudioRoute.EARPIECE))
        assertTrue(routes.contains(AudioRoute.HEADSET))
    }

    @Test
    fun turnConfig_defaultsToBuiltInOpenRelay() {
        val defaultTurn = TurnConfig()
        assertFalse(defaultTurn.useCustomTurn)
        assertEquals("", defaultTurn.customServer)
        assertEquals("", defaultTurn.customUsername)
        assertEquals("", defaultTurn.customCredential)
    }

    @Test
    fun turnConfig_preservesCustomValues() {
        val customTurn = TurnConfig(
            useCustomTurn = true,
            customServer = "turn:turn.mycloud.org:3478",
            customUsername = "testuser",
            customCredential = "secretpassword"
        )
        assertTrue(customTurn.useCustomTurn)
        assertEquals("turn:turn.mycloud.org:3478", customTurn.customServer)
        assertEquals("testuser", customTurn.customUsername)
        assertEquals("secretpassword", customTurn.customCredential)
    }
}

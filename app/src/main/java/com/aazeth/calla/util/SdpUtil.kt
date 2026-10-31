package com.aazeth.calla.util

object SdpUtil {

    /**
     * Modifies the SDP string to enforce the Opus codec to use the specified bitrate
     * in fullband 48 kHz stereo/fec format.
     *
     * @param sdp Original SDP string
     * @param bitrateKbps Bitrate in kbps (e.g. 64 to 256)
     * @return Modified SDP string
     */
    fun setOpusBitrate(sdp: String, bitrateKbps: Int): String {
        val bitrateBps = (bitrateKbps.coerceIn(64, 256)) * 1000
        val lines = sdp.split("\r\n").toMutableList()

        // 1. Locate the Opus rtpmap line to get the dynamic payload type ID (typically 111)
        val opusRtpMapRegex = Regex("""^a=rtpmap:(\d+)\s+opus/48000/2""", RegexOption.IGNORE_CASE)
        var opusPayloadType: String? = null
        var rtpMapIndex = -1

        for (i in lines.indices) {
            val match = opusRtpMapRegex.find(lines[i].trim())
            if (match != null) {
                opusPayloadType = match.groupValues[1]
                rtpMapIndex = i
                break
            }
        }

        if (opusPayloadType == null) {
            // Opus not found in SDP, return original SDP unchanged
            return sdp
        }

        // 2. Look for existing a=fmtp:<pt> line
        val fmtpPrefix = "a=fmtp:$opusPayloadType"
        var fmtpIndex = -1
        for (i in lines.indices) {
            if (lines[i].trim().startsWith(fmtpPrefix)) {
                fmtpIndex = i
                break
            }
        }

        val bitrateParam = "maxaveragebitrate=$bitrateBps;stereo=1;sprop-stereo=1;useinbandfec=1"

        if (fmtpIndex != -1) {
            val currentFmtp = lines[fmtpIndex]
            val updatedFmtp = if (currentFmtp.contains("maxaveragebitrate=")) {
                currentFmtp.replace(
                    Regex("""maxaveragebitrate=\d+"""),
                    "maxaveragebitrate=$bitrateBps"
                )
            } else {
                "$currentFmtp;$bitrateParam"
            }
            lines[fmtpIndex] = updatedFmtp
        } else {
            // Insert fmtp line immediately following the rtpmap line
            val newFmtpLine = "$fmtpPrefix $bitrateParam"
            lines.add(rtpMapIndex + 1, newFmtpLine)
        }

        return lines.joinToString("\r\n")
    }
}

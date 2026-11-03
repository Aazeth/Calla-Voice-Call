package com.aazeth.calla.util

object SdpUtil {

    /**
     * Modifies the SDP string to enforce the Opus codec to use high-fidelity media mode:
     * 48 kHz fullband sampling rate, requested bitrate, stereo capability, inband FEC, and CBR.
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

        val highFidelityParams = "maxaveragebitrate=$bitrateBps;stereo=1;sprop-stereo=1;sprop-maxcapturerate=48000;useinbandfec=1;cbr=1"

        if (fmtpIndex != -1) {
            val currentFmtp = lines[fmtpIndex]
            val updatedParams = mutableMapOf<String, String>()
            val paramPairs = currentFmtp.substringAfter("$fmtpPrefix ").trim().split(";")
            for (pair in paramPairs) {
                val kv = pair.split("=", limit = 2)
                if (kv.size == 2) {
                    updatedParams[kv[0].trim()] = kv[1].trim()
                }
            }
            updatedParams["maxaveragebitrate"] = bitrateBps.toString()
            updatedParams["stereo"] = "1"
            updatedParams["sprop-stereo"] = "1"
            updatedParams["sprop-maxcapturerate"] = "48000"
            updatedParams["useinbandfec"] = "1"
            updatedParams["cbr"] = "1"

            val fmtpStr = updatedParams.entries.joinToString(";") { "${it.key}=${it.value}" }
            lines[fmtpIndex] = "$fmtpPrefix $fmtpStr"
        } else {
            // Insert fmtp line immediately following the rtpmap line
            val newFmtpLine = "$fmtpPrefix $highFidelityParams"
            lines.add(rtpMapIndex + 1, newFmtpLine)
        }

        return lines.joinToString("\r\n")
    }
}

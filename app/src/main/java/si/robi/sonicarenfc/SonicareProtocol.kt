package si.robi.sonicarenfc

import android.nfc.Tag
import android.nfc.tech.NfcA
import java.nio.charset.StandardCharsets

/** Known Philips BrushSync/NTAG213 layout; no write of configuration/password pages. */
internal object SonicareProtocol {
    data class Head(
        val uid: String,
        val mfg: String,
        val usedSeconds: Int,
        val targetSeconds: Int,
        val page24: ByteArray
    )

    fun read(tag: Tag, nfca: NfcA): Head {
        val uidBytes = tag.id
        require(uidBytes.size == 7) { "uid_length" }
        val p20 = read16(nfca, 0x20) // pages 20..23
        val p24 = read16(nfca, 0x24) // pages 24..27
        // Page 21: lifetime LE in bytes 0-1; MFG begins at bytes 2-3.
        val target = le16(p20[4], p20[5])
        val mfg = String(p20.copyOfRange(6, 16), StandardCharsets.US_ASCII)
        require(Regex("^[0-9]{6} [0-9]{2}[A-Za-z]$").matches(mfg)) {
            "mfg_invalid"
        }
        // Restrict writes to a recognized Sonicare format; deliberately conservative.
        require(target == 21_600) { "lifetime_invalid" }
        return Head(hex(uidBytes), mfg, le16(p24[0], p24[1]), target, p24.copyOfRange(0, 4))
    }

    fun reset(tag: Tag, nfca: NfcA, previouslyRead: Head): Head {
        val before = read(tag, nfca)
        require(before.uid == previouslyRead.uid && before.mfg == previouslyRead.mfg) {
            "wrong_head"
        }
        require(before.usedSeconds > 0) { "already_zero" }
        val password = password(tag.id, before.mfg)
        // Authenticate exactly once: repeated wrong attempts may lock some NTAGs.
        val auth = nfca.transceive(byteArrayOf(0x1B) + password)
        require(auth.size == 2) { "auth_failed" }

        // Modify ONLY the two little-endian seconds bytes. Preserve intensity and mode.
        val write = byteArrayOf(0xA2.toByte(), 0x24, 0x00, 0x00, before.page24[2], before.page24[3])
        val ack = nfca.transceive(write)
        require(ack.size == 1 && (ack[0].toInt() and 0xFF) == 0x0A) {
            "write_failed"
        }
        val after = read(tag, nfca)
        require(after.usedSeconds == 0 && after.page24[2] == before.page24[2] && after.page24[3] == before.page24[3]) {
            "verify_failed"
        }
        return after
    }

    private fun read16(nfca: NfcA, page: Int): ByteArray {
        val bytes = nfca.transceive(byteArrayOf(0x30, page.toByte()))
        require(bytes.size == 16) { "read_failed" }
        return bytes
    }

    private fun le16(lo: Byte, hi: Byte): Int = (lo.toInt() and 0xFF) or ((hi.toInt() and 0xFF) shl 8)

    private fun crc16(initial: Int, data: ByteArray): Int {
        var crc = initial
        for (byte in data) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) (crc shl 1) xor 0x1021 else (crc shl 1)
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    /** Kotlin implementation of the method documented by @ATC1441; see THIRD_PARTY_NOTICES.md. */
    fun password(uid: ByteArray, mfg: String): ByteArray {
        require(uid.size == 7 && Regex("^[0-9]{6} [0-9]{2}[A-Za-z]$").matches(mfg))
        val lower = crc16(0x49A3, uid)
        val upper = crc16(lower, mfg.toByteArray(StandardCharsets.US_ASCII))
        val packed = lower.toLong() or (upper.toLong() shl 16)
        val swizzled = ((packed ushr 8) and 0x00FF00FFL) or ((packed shl 8) and 0xFF00FF00L)
        return byteArrayOf(
            ((swizzled ushr 24) and 0xFF).toByte(),
            ((swizzled ushr 16) and 0xFF).toByte(),
            ((swizzled ushr 8) and 0xFF).toByte(),
            (swizzled and 0xFF).toByte()
        )
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
}

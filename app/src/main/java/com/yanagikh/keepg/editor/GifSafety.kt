package com.yanagikh.keepg.editor

/** Validate dimensions and aggregate decoded allocation before passing untrusted GIFs to Movie. */
object GifSafety {
    fun validate(bytes: ByteArray): Int {
        require(bytes.size in 14..16 * 1024 * 1024)
        require(bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII) in setOf("GIF87a", "GIF89a"))
        var p = 6
        fun byte(): Int { require(p < bytes.size); return bytes[p++].toInt() and 255 }
        fun word(): Int = byte() or (byte() shl 8)
        fun skip(n: Int) { require(n >= 0 && p.toLong() + n <= bytes.size); p += n }
        fun blocks() { var n = byte(); while (n != 0) { skip(n); n = byte() } }
        val width = word(); val height = word()
        require(width > 0 && height > 0 && width.toLong() * height <= 4_000_000L)
        val packed = byte(); skip(2)
        if (packed and 128 != 0) skip(3 * (1 shl ((packed and 7) + 1)))
        var frames = 0; var decodedPixels = 0L
        while (p < bytes.size) when (byte()) {
            0x3B -> { require(frames > 0); return frames }
            0x21 -> { byte(); blocks() }
            0x2C -> {
                val x = word(); val y = word(); val w = word(); val h = word()
                require(w > 0 && h > 0 && x.toLong() + w <= width && y.toLong() + h <= height)
                decodedPixels += width.toLong() * height // Decoders may retain composited full frames.
                require(++frames <= 600 && decodedPixels <= 16_000_000L)
                val flags = byte()
                if (flags and 128 != 0) skip(3 * (1 shl ((flags and 7) + 1)))
                require(byte() in 2..8)
                blocks()
            }
            else -> error("Malformed GIF block")
        }
        error("Missing GIF trailer")
    }
}

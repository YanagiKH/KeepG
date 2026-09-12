package com.yanagikh.keepg.editor

import java.io.OutputStream

/** Streaming GIF89a writer. Fixed RGB332 palette bounds memory regardless of frame count. */
class GifEncoder(private val out: OutputStream, val width: Int, val height: Int, loops: Int = 0) : AutoCloseable {
    private var closed = false
    init {
        require(width in 1..640 && height in 1..640 && loops in 0..65535)
        out.write("GIF89a".toByteArray(Charsets.US_ASCII)); word(width); word(height)
        out.write(byteArrayOf(0xf7.toByte(), 0, 0))
        repeat(256) { color ->
            out.write(((color shr 5) and 7) * 255 / 7)
            out.write(((color shr 2) and 7) * 255 / 7)
            out.write((color and 3) * 255 / 3)
        }
        out.write(byteArrayOf(0x21, 0xff.toByte(), 11)); out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(3, 1)); word(loops); out.write(0)
    }
    fun frame(argb: IntArray, delayCentiseconds: Int) {
        check(!closed); require(argb.size == width * height && delayCentiseconds in 2..65535)
        out.write(byteArrayOf(0x21, 0xf9.toByte(), 4, 4)); word(delayCentiseconds); out.write(byteArrayOf(0, 0))
        out.write(0x2c); word(0); word(0); word(width); word(height); out.write(0); out.write(8)
        val bits = Codes(out)
        val dictionary = HashMap<Int, Int>(4096)
        var next = 258
        bits.code(256)
        var prefix = index(argb[0])
        for (i in 1 until argb.size) {
            val pixel = index(argb[i]); val key = (prefix shl 8) or pixel
            val found = dictionary[key]
            if (found != null) { prefix = found; continue }
            bits.code(prefix)
            if (next < 4096) dictionary[key] = next++
            else { bits.code(256); dictionary.clear(); next = 258 }
            prefix = pixel
        }
        bits.code(prefix); bits.code(257); bits.finish()
    }
    override fun close() { if (!closed) { out.write(0x3b); out.flush(); closed = true } }
    private fun word(value: Int) { out.write(value and 255); out.write((value shr 8) and 255) }
    private fun index(argb: Int): Int {
        // GIF palette has no partial alpha; composite against white before quantization.
        val a = (argb ushr 24) and 255
        fun channel(shift: Int) = (((argb ushr shift) and 255) * a + 255 * (255 - a)) / 255
        return ((channel(16) shr 5) shl 5) or ((channel(8) shr 5) shl 2) or (channel(0) shr 6)
    }
    private class Codes(private val out: OutputStream) {
        private val block = ByteArray(255)
        private var count = 0; private var buffer = 0; private var used = 0
        private var size = 9; private var decoderNext = 258; private var previous = false
        fun code(value: Int) {
            buffer = buffer or (value shl used); used += size
            while (used >= 8) { byte(buffer and 255); buffer = buffer ushr 8; used -= 8 }
            // Mirror decoder dictionary growth, avoiding the one-code encoder/decoder lag.
            if (value == 256) { size = 9; decoderNext = 258; previous = false }
            else if (value != 257) {
                if (previous && decoderNext < 4096) { decoderNext++; if (decoderNext == (1 shl size) && size < 12) size++ }
                previous = true
            }
        }
        fun finish() { if (used > 0) byte(buffer and 255); flush(); out.write(0) }
        private fun byte(value: Int) { block[count++] = value.toByte(); if (count == 255) flush() }
        private fun flush() { if (count > 0) { out.write(count); out.write(block, 0, count); count = 0 } }
    }
}

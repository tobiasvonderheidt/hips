package org.vonderheidt.hips.tests

import android.util.Log
import org.vonderheidt.hips.compression.Adaptive
import org.vonderheidt.hips.compression.ArithmeticCompression
import org.vonderheidt.hips.compression.BitCrush
import kotlin.math.roundToInt

class TestCompression {

    private val msgs = listOf(
        "hello",
        "hello world",
        "hi how are you?",
        "meet me at 6",
        "overthrow the govt with me? \uD83E\uDD7A",
        "glasses attic rational desire photograph",
        "i keep getting untethered from my bed - now i just float",
        "sphinx of black quartz, judge my vow",
        "今天天气真好!",
        "was für schönes wetter da draußen",
        "Q6mhbAyc"
    )

    fun runArithmetic() {
        Log.i("CompTest", "TESTING ARITHMETIC COMPRESSION")
        msgs.forEach {
            try {
                val compressed = ArithmeticCompression.compress(it)
                Log.d("CompTest", "compressed '$it' to $compressed (${((compressed.bitLength().toDouble() / (it.encodeToByteArray().size * 8))*100).roundToInt()}%)")
                val uncompressed = ArithmeticCompression.inflate(compressed)
                Log.d("CompTest", "decompressed to '$uncompressed'")
            }
            catch (e: Exception) {
                Log.e("CompTest", e.toString())
                Log.e("CompTest", e.stackTraceToString())
            }
        }
    }

    fun runAdaptive() {
        Log.i("CompTest", "TESTING ADAPTIVE COMPRESSION")
        msgs.forEach {
            try {
                val compressed = Adaptive.compress(it)
                Log.d("CompTest", "compressed '$it' to $compressed (${((compressed.bitLength().toDouble() / (it.encodeToByteArray().size * 8))*100).roundToInt()}%)")
                val uncompressed = Adaptive.inflate(compressed)
                Log.d("CompTest", "decompressed to '$uncompressed'")
            }
            catch (e: Exception) {
                Log.e("CompTest", e.toString())
                Log.e("CompTest", e.stackTraceToString())
            }
        }
    }

    fun runBitCrush() {
        Log.i("CompTest", "TESTING BITCRUSH COMPRESSION")
        msgs.forEach {
            try {
                val compressed = BitCrush.compress(it)
                Log.d("CompTest", "compressed '$it' to $compressed (${((compressed.bitLength().toDouble() / (it.encodeToByteArray().size * 8))*100).roundToInt()}%)")
                val uncompressed = BitCrush.inflate(compressed)
                Log.d("CompTest", "decompressed to '$uncompressed'")
            }
            catch (e: Exception) {
                Log.e("CompTest", e.toString())
                Log.e("CompTest", e.stackTraceToString())
            }
        }
    }
}
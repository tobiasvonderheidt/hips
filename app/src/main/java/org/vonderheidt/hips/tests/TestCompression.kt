package org.vonderheidt.hips.tests

import android.util.Log
import org.vonderheidt.hips.compression.Adaptive
import org.vonderheidt.hips.compression.ArithmeticCompression
import org.vonderheidt.hips.compression.BitCrush
import kotlin.math.roundToInt

private const val TAG = "TestCompression.kt"

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
        Log.i(TAG, "TESTING ARITHMETIC COMPRESSION")
        msgs.forEach {
            try {
                val compressed = ArithmeticCompression.compress(it)
                Log.d(TAG, "compressed '$it' to $compressed (${((compressed.bitLength().toDouble() / (it.encodeToByteArray().size * 8))*100).roundToInt()}%)")
                val uncompressed = ArithmeticCompression.inflate(compressed)
                Log.d(TAG, "decompressed to '$uncompressed'")
            }
            catch (e: Exception) {
                Log.e(TAG, e.toString())
                Log.e(TAG, e.stackTraceToString())
            }
        }
    }

    fun runAdaptive() {
        Log.i(TAG, "TESTING ADAPTIVE COMPRESSION")
        msgs.forEach {
            try {
                val compressed = Adaptive.compress(it)
                Log.d(TAG, "compressed '$it' to $compressed (${((compressed.bitLength().toDouble() / (it.encodeToByteArray().size * 8))*100).roundToInt()}%)")
                val uncompressed = Adaptive.inflate(compressed)
                Log.d(TAG, "decompressed to '$uncompressed'")
            }
            catch (e: Exception) {
                Log.e(TAG, e.toString())
                Log.e(TAG, e.stackTraceToString())
            }
        }
    }

    fun runBitCrush() {
        Log.i(TAG, "TESTING BITCRUSH COMPRESSION")
        msgs.forEach {
            try {
                val compressed = BitCrush.compress(it)
                Log.d(TAG, "compressed '$it' to $compressed (${((compressed.bitLength().toDouble() / (it.encodeToByteArray().size * 8))*100).roundToInt()}%)")
                val uncompressed = BitCrush.inflate(compressed)
                Log.d(TAG, "decompressed to '$uncompressed'")
            }
            catch (e: Exception) {
                Log.e(TAG, e.toString())
                Log.e(TAG, e.stackTraceToString())
            }
        }
    }
}
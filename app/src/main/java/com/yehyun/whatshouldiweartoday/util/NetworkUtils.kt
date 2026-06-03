package com.yehyun.whatshouldiweartoday.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

const val PERCEPTUAL_HASH_THRESHOLD = 10

fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

// Just DCT hash — call trimBorders separately before this if needed.
fun computePerceptualHash(bitmap: Bitmap): String = dctPHash(bitmap)

// Detects background color from corners, then trims any uniform-color border.
// Works for white, black, gray, or any solid-color margin.
// Returns the same bitmap object if no trimming was done (safe to compare with ===).
fun trimBorders(bitmap: Bitmap): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 8 || h < 8) return bitmap

    val c00 = bitmap.getPixel(0, 0)
    val c10 = bitmap.getPixel(w - 1, 0)
    val c01 = bitmap.getPixel(0, h - 1)
    val c11 = bitmap.getPixel(w - 1, h - 1)

    fun maxChDiff(p1: Int, p2: Int) = maxOf(
        abs(Color.red(p1) - Color.red(p2)),
        abs(Color.green(p1) - Color.green(p2)),
        abs(Color.blue(p1) - Color.blue(p2))
    )

    // Need at least 3 of 4 corners to be the same color (within tolerance)
    val tol = 30
    val agreeing = listOf(c10, c01, c11).count { maxChDiff(it, c00) <= tol }
    if (agreeing < 2) return bitmap

    val bgR = Color.red(c00); val bgG = Color.green(c00); val bgB = Color.blue(c00)

    fun isBg(pixel: Int) =
        abs(Color.red(pixel) - bgR) <= tol &&
        abs(Color.green(pixel) - bgG) <= tol &&
        abs(Color.blue(pixel) - bgB) <= tol

    fun rowIsBg(y: Int): Boolean {
        val step = max(1, w / 20)
        var bg = 0; var total = 0
        for (x in 0 until w step step) { if (isBg(bitmap.getPixel(x, y))) bg++; total++ }
        return total > 0 && bg.toFloat() / total >= 0.90f
    }

    fun colIsBg(x: Int): Boolean {
        val step = max(1, h / 20)
        var bg = 0; var total = 0
        for (y in 0 until h step step) { if (isBg(bitmap.getPixel(x, y))) bg++; total++ }
        return total > 0 && bg.toFloat() / total >= 0.90f
    }

    var top = 0; var bottom = h - 1; var left = 0; var right = w - 1
    while (top < bottom && rowIsBg(top)) top++
    while (bottom > top && rowIsBg(bottom)) bottom--
    while (left < right && colIsBg(left)) left++
    while (right > left && colIsBg(right)) right--

    if ((bottom - top + 1) < h * 0.2f || (right - left + 1) < w * 0.2f) return bitmap
    if (top == 0 && bottom == h - 1 && left == 0 && right == w - 1) return bitmap
    return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
}

private fun dctPHash(bitmap: Bitmap): String {
    val size = 32
    val hashSize = 8
    val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
    val pixels = FloatArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val p = small.getPixel(x, y)
            pixels[y * size + x] = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
        }
    }
    small.recycle()

    val cosTable = Array(hashSize) { u -> FloatArray(size) { x -> cos((2 * x + 1) * u * PI / (2 * size)).toFloat() } }

    val dct = FloatArray(hashSize * hashSize)
    for (u in 0 until hashSize) {
        for (v in 0 until hashSize) {
            var sum = 0f
            for (x in 0 until size) {
                val cx = cosTable[u][x]
                for (y in 0 until size) {
                    sum += pixels[y * size + x] * cx * cosTable[v][y]
                }
            }
            dct[v * hashSize + u] = sum
        }
    }

    val sorted = dct.copyOfRange(1, hashSize * hashSize).also { it.sort() }
    val median = (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f

    var hash = 0L
    for (i in 1 until hashSize * hashSize) {
        if (dct[i] > median) hash = hash or (1L shl (i - 1))
    }
    return hash.toString(16).padStart(16, '0')
}

fun hammingDistance(hash1: String, hash2: String): Int {
    val h1 = hash1.toLongOrNull(16) ?: return Int.MAX_VALUE
    val h2 = hash2.toLongOrNull(16) ?: return Int.MAX_VALUE
    return java.lang.Long.bitCount(h1 xor h2)
}

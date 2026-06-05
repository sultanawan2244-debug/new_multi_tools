package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect

object ImageProcessor {

    /**
     * Applies filters, brightness, and contrast to a source Bitmap using fast JVM pixel iteration.
     * brightness ranges from -100f to 100f (default 0f).
     * contrast ranges from 0.5f to 2.5f (default 1.0f).
     */
    fun applyEffects(src: Bitmap, filterName: String, brightness: Float, contrast: Float): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (i in pixels.indices) {
            val color = pixels[i]
            val a = (color shr 24) and 0xff
            val r = (color shr 16) and 0xff
            val g = (color shr 8) and 0xff
            val b = color and 0xff

            // Apply contrast and brightness
            // Formula: channel_out = (channel_in - 128) * contrast + 128 + brightness
            var nr = ((r - 128) * contrast + 128 + brightness).toInt().coerceIn(0, 255)
            var ng = ((g - 128) * contrast + 128 + brightness).toInt().coerceIn(0, 255)
            var nb = ((b - 128) * contrast + 128 + brightness).toInt().coerceIn(0, 255)

            when (filterName) {
                "Gray" -> {
                    val gray = (0.299f * nr + 0.587f * ng + 0.114f * nb).toInt().coerceIn(0, 255)
                    pixels[i] = (a shl 24) or (gray shl 16) or (gray shl 8) or gray
                }
                "Magic" -> {
                    // Magic Scan filter: Whitens paper backgrounds while keeping text dark and sharp.
                    val gray = (0.299f * nr + 0.587f * ng + 0.114f * nb).toInt()
                    val magicGray = if (gray > 130) {
                        // Whiten background aggressively
                        ((gray - 130) * 1.5 + 180).toInt().coerceIn(0, 255)
                    } else {
                        // Darken text aggressively
                        (gray * 0.6).toInt().coerceIn(0, 255)
                    }
                    pixels[i] = (a shl 24) or (magicGray shl 16) or (magicGray shl 8) or magicGray
                }
                "Binarize" -> {
                    // Convert to high-contrast monochrome (pure black and pure white)
                    val gray = (0.299f * nr + 0.587f * ng + 0.114f * nb).toInt()
                    val binVal = if (gray > 127) 255 else 0
                    pixels[i] = (a shl 24) or (binVal shl 16) or (binVal shl 8) or binVal
                }
                "Auto" -> {
                    // Auto enhance: stretch levels and boost saturation
                    val autr = (nr * 1.15f + 5f).toInt().coerceIn(0, 255)
                    val autg = (ng * 1.15f + 5f).toInt().coerceIn(0, 255)
                    val autb = (nb * 1.15f + 5f).toInt().coerceIn(0, 255)
                    pixels[i] = (a shl 24) or (autr shl 16) or (autg shl 8) or autb
                }
                else -> {
                    // Original
                    pixels[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
        }

        outBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    /**
     * Cropping using relative bounds values (0.0 to 1.0).
     */
    fun cropBitmap(src: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val w = src.width
        val h = src.height

        val x = (left * w).toInt().coerceIn(0, w - 1)
        val y = (top * h).toInt().coerceIn(0, h - 1)
        var width = ((right - left) * w).toInt().coerceIn(1, w - x)
        var height = ((bottom - top) * h).toInt().coerceIn(1, h - y)

        return Bitmap.createBitmap(src, x, y, width, height)
    }

    /**
     * Scans the image for high intensity gradients to automatically detect the document edges,
     * returning [left, top, right, bottom] bounds in relative coordinates (0.0 to 1.0).
     */
    fun detectEdges(src: Bitmap): FloatArray {
        val w = src.width
        val h = src.height
        
        // Downsample for fast scanning
        val scanW = 100
        val scanH = 100
        val resized = Bitmap.createScaledBitmap(src, scanW, scanH, false)
        val pixels = IntArray(scanW * scanH)
        resized.getPixels(pixels, 0, scanW, 0, 0, scanW, scanH)

        var minX = scanW
        var maxX = 0
        var minY = scanH
        var maxY = 0

        val gray = IntArray(scanW * scanH)
        for (y in 0 until scanH) {
            for (x in 0 until scanW) {
                val p = pixels[y * scanW + x]
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                gray[y * scanW + x] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
            }
        }

        // Apply a simple Sobel gradient checking
        for (y in 1 until scanH - 1) {
            for (x in 1 until scanW - 1) {
                val gx = gray[y * scanW + (x + 1)] - gray[y * scanW + (x - 1)]
                val gy = gray[(y + 1) * scanW + x] - gray[(y - 1) * scanW + x]
                val grad = Math.abs(gx) + Math.abs(gy)

                // High gradient value indicates an edge
                if (grad > 35) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // Clean up resized bitmap
        resized.recycle()

        // Fallback or clamp detected area to safe bounds (at least 60% of the image)
        val left = if (minX < scanW && minX > 2) minX / 100f else 0.08f
        val top = if (minY < scanH && minY > 2) minY / 100f else 0.08f
        val right = if (maxX > 0 && maxX < scanW - 2) maxX / 100f else 0.92f
        val bottom = if (maxY > 0 && maxY < scanH - 2) maxY / 100f else 0.92f

        // Ensure safe rect boundaries
        val finalLeft = left.coerceIn(0f, 0.4f)
        val finalTop = top.coerceIn(0f, 0.4f)
        val finalRight = right.coerceIn(0.6f, 1f)
        val finalBottom = bottom.coerceIn(0.6f, 1f)

        return floatArrayOf(finalLeft, finalTop, finalRight, finalBottom)
    }
}

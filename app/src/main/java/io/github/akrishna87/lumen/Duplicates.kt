package io.github.akrishna87.lumen

import android.content.Context
import android.graphics.Bitmap

/**
 * Finds exact duplicates: photos with the same width, height and file size whose thumbnails match.
 * Near-duplicates (a resized or re-saved copy) aren't found.
 */
object Duplicates {
    /** Sets of copies, each oldest (lowest id) first. [items] is the Library's list, so hidden photos stay out. */
    suspend fun find(context: Context, items: List<MediaItem>): List<List<MediaItem>> {
        val candidates = items.filter { !it.isVideo && it.sizeBytes > 0 }
            .groupBy { Triple(it.width, it.height, it.sizeBytes) }
            .values.filter { it.size > 1 }
        val sets = ArrayList<List<MediaItem>>()
        for (group in candidates) {
            group.groupBy { photo -> Thumbnails.load(context, photo)?.let(::fingerprint) }
                .filterKeys { it != null }
                .values.filter { it.size > 1 }
                .forEach { sets += it.sortedBy { photo -> photo.id } }
        }
        return sets
    }

    /** A 64-bit difference hash of the thumbnail: each bit says whether a pixel is brighter than its neighbour. */
    private fun fingerprint(bitmap: Bitmap): Long {
        // Android's thumbnails can be GPU-only ("hardware") bitmaps, whose pixels can't be read.
        val readable = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        val small = Bitmap.createScaledBitmap(readable, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            hash = hash shl 1
            if (luma(small.getPixel(x, y)) > luma(small.getPixel(x + 1, y))) hash = hash or 1L
        }
        if (small !== readable) small.recycle()
        if (readable !== bitmap) readable.recycle()
        return hash
    }

    private fun luma(c: Int): Int = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
}

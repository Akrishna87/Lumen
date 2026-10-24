package io.github.akrishna87.lumen

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Photos' filters, as colour matrices. */
enum class Filter(val label: String) {
    ORIGINAL("Original"), VIVID("Vivid"), VIVID_WARM("Vivid Warm"), VIVID_COOL("Vivid Cool"),
    DRAMATIC("Dramatic"), MONO("Mono"), SILVERTONE("Silvertone"), NOIR("Noir"),
}

/** Crop shapes, centred on the photo. The ratio is long side over short side, matched to the photo's orientation. */
enum class Aspect(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null), SQUARE("Square", 1f), FOUR_THREE("4:3", 4f / 3f), SIXTEEN_NINE("16:9", 16f / 9f),
}

/** Everything the editor can change. Adjustments run from -1 to 1; 0 leaves the photo as it is. */
data class Edits(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val filter: Filter = Filter.ORIGINAL,
    /** Quarter turns to the left, 0–3. */
    val turns: Int = 0,
    /** Straighten, in degrees (-45 to 45). */
    val straighten: Float = 0f,
    val aspect: Aspect = Aspect.ORIGINAL,
)

/**
 * Applies [Edits] to a bitmap. The editor's preview and the saved copy both go through [render],
 * so what you see is what gets saved.
 */
object EditRenderer {

    fun colors(edits: Edits): ColorMatrix {
        val m = filter(edits.filter)
        m.postConcat(ColorMatrix().apply { setSaturation(1f + edits.saturation) })
        m.postConcat(contrast(1f + edits.contrast * 0.6f))
        m.postConcat(offset(edits.brightness * 60f))
        m.postConcat(warmth(edits.warmth))
        return m
    }

    fun filter(filter: Filter): ColorMatrix = when (filter) {
        Filter.ORIGINAL -> ColorMatrix()
        Filter.VIVID -> saturation(1.35f).also { it.postConcat(contrast(1.1f)) }
        Filter.VIVID_WARM -> filter(Filter.VIVID).also { it.postConcat(warmth(0.5f)) }
        Filter.VIVID_COOL -> filter(Filter.VIVID).also { it.postConcat(warmth(-0.5f)) }
        Filter.DRAMATIC -> saturation(0.8f).also { it.postConcat(contrast(1.35f)); it.postConcat(offset(-10f)) }
        Filter.MONO -> saturation(0f)
        Filter.SILVERTONE -> saturation(0f).also { it.postConcat(contrast(0.9f)); it.postConcat(offset(15f)) }
        Filter.NOIR -> saturation(0f).also { it.postConcat(contrast(1.5f)) }
    }

    private fun saturation(s: Float) = ColorMatrix().apply { setSaturation(s) }

    private fun contrast(k: Float): ColorMatrix {
        val t = 128f * (1f - k)
        return ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, t, 0f, k, 0f, 0f, t, 0f, 0f, k, 0f, t, 0f, 0f, 0f, 1f, 0f))
    }

    private fun offset(v: Float) =
        ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, v, 0f, 1f, 0f, 0f, v, 0f, 0f, 1f, 0f, v, 0f, 0f, 0f, 1f, 0f))

    private fun warmth(w: Float) = ColorMatrix().apply { setScale(1f + 0.12f * w, 1f, 1f - 0.12f * w, 1f) }

    /** The crop window, in the source's pixels, after turning: [width, height]. */
    fun outputSize(width: Int, height: Int, edits: Edits): Pair<Int, Int> {
        val turned = edits.turns % 2 == 1
        val w = (if (turned) height else width).toFloat()
        val h = (if (turned) width else height).toFloat()
        val ratio = edits.aspect.ratio ?: return w.roundToInt() to h.roundToInt()
        val target = if (w >= h) ratio else 1f / ratio // keep the photo's orientation
        return if (w / h > target) (h * target).roundToInt() to h.roundToInt() else w.roundToInt() to (w / target).roundToInt()
    }

    /**
     * One pass: turn, straighten (zoomed just enough that no blank corners show), crop to the
     * window, and colour. Only the source and the result are in memory at once.
     */
    fun render(src: Bitmap, edits: Edits): Bitmap {
        val (cw, ch) = outputSize(src.width, src.height, edits)
        val turned = edits.turns % 2 == 1
        val rw = if (turned) src.height else src.width
        val rh = if (turned) src.width else src.height
        val theta = Math.toRadians(abs(edits.straighten).toDouble())
        val c = cos(theta).toFloat()
        val s = sin(theta).toFloat()
        val zoom = max(1f, max((cw * c + ch * s) / rw, (cw * s + ch * c) / rh))
        val matrix = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postRotate(-90f * edits.turns + edits.straighten)
            postScale(zoom, zoom)
            postTranslate(cw / 2f, ch / 2f)
        }
        val out = Bitmap.createBitmap(max(1, cw), max(1, ch), Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colors(edits)) }
        Canvas(out).drawBitmap(src, matrix, paint)
        return out
    }
}

/** Decoding photos for editing, and saving the edited copy. */
object EditStore {
    private const val SAVE_MAX = 4096

    /** EXIF tags carried over to the copy. Pixel sizes and orientation are left out: the copy's are new. */
    private val TAGS = listOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
    )

    /** The photo, upright, no bigger than [maxSide] on its long side, in memory the editor can draw from. */
    fun decode(context: Context, item: MediaItem, maxSide: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri)) { decoder, info, _ ->
                val scale = min(1f, maxSide.toFloat() / max(info.size.width, info.size.height))
                decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bitmap = context.contentResolver.openInputStream(item.uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            // BitmapFactory ignores EXIF orientation; ImageDecoder (Android 9+) applies it itself.
            val degrees = context.contentResolver.openInputStream(item.uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            if (degrees == 0) bitmap
            else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        }
    }.getOrNull()

    /**
     * Saves the edited photo as a new JPEG next to the original (or in Pictures/Lumen), with the
     * original's date and camera details. The original is never changed. Returns the copy's id.
     */
    suspend fun saveCopy(context: Context, item: MediaItem, edits: Edits): Long? = withContext(Dispatchers.IO) {
        val src = decode(context, item, SAVE_MAX) ?: return@withContext null
        val out = EditRenderer.render(src, edits)
        src.recycle()
        val tmp = File(context.cacheDir, "edit.jpg")
        try {
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            out.recycle()
            copyExif(context, item, tmp)
            val name = item.fileName.substringBeforeLast('.') + "_edited.jpg"
            if (Build.VERSION.SDK_INT >= 29) insert(context, item, name, tmp) else insertLegacy(context, name, tmp)
        } catch (e: Exception) {
            null
        } finally {
            tmp.delete()
        }
    }

    private fun copyExif(context: Context, item: MediaItem, file: File) {
        val dst = ExifInterface(file)
        val src = runCatching { context.contentResolver.openInputStream(item.uri)?.use { ExifInterface(it) } }.getOrNull()
        TAGS.forEach { tag -> src?.getAttribute(tag)?.let { dst.setAttribute(tag, it) } }
        // A photo with no date of its own (a screenshot, a download) gets the one Lumen sorts it by,
        // so the copy sits next to the original.
        if (dst.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == null) {
            val time = Instant.ofEpochMilli(item.takenMs).atZone(ZoneId.systemDefault())
            dst.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, time.format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")))
            dst.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, time.format(DateTimeFormatter.ofPattern("xxx")))
        }
        // The turn is in the pixels now; don't let viewers turn it again.
        dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        dst.saveAttributes()
    }

    @RequiresApi(29)
    private fun insert(context: Context, item: MediaItem, name: String, file: File): Long? {
        // Android 11+ only accepts new photos under DCIM/ or Pictures/.
        val folder = item.folder.takeIf { it.startsWith("DCIM") || it.startsWith("Pictures") } ?: "Pictures/Lumen"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/")
            put(MediaStore.Images.ImageColumns.DATE_TAKEN, item.takenMs)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no output")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            ContentUris.parseId(uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /** Android 8–9: write the file into Pictures/Lumen and have the media scanner add it. */
    @Suppress("DEPRECATION")
    private suspend fun insertLegacy(context: Context, name: String, file: File): Long? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Lumen").apply { mkdirs() }
        var target = File(dir, name)
        var n = 2
        while (target.exists()) target = File(dir, name.removeSuffix(".jpg") + " $n.jpg").also { n++ }
        file.copyTo(target)
        val uri: Uri? = suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("image/jpeg")) { _, scanned -> cont.resume(scanned) }
        }
        return uri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }
    }
}

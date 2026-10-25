package io.github.akrishna87.lumen

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Rect
import com.googlecode.leptonica.android.ReadFile
import com.googlecode.leptonica.android.Skew
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A line of text found in a photo, with where it is in the photo's pixels (upright): [box] is the
 * line's size around its centre, turned by [tilt] degrees (clockwise) when the text is slanted.
 */
data class TextLine(val text: String, val box: Rect, val tilt: Float = 0f)

/**
 * Finds text in photos with Tesseract, entirely on the phone: nothing is sent anywhere. One engine
 * is shared, so the background index and the viewer's Live Text take turns.
 */
object TextReader {
    /** Size photos are read at: big enough for screenshot and document text, quick enough for a library. */
    private const val INDEX_SIZE = 1400
    private const val LIVE_SIZE = 2048
    /** Tesseract's confidence (0–100) a word needs; lower ones are usually noise in ordinary photos. */
    private const val MIN_WORD = 75
    private const val MIN_LINE = 65

    private val lock = Mutex()
    private var engine: TessBaseAPI? = null

    /** The words in a photo, lower case, for search ("" when there are none). */
    suspend fun words(context: Context, item: MediaItem): String = read(context, item, INDEX_SIZE) { api, _ ->
        val words = ArrayList<String>()
        val it = api.getResultIterator() ?: return@read ""
        it.begin()
        do {
            val word = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim().orEmpty()
            if (word.length >= 2 && word.any { c -> c.isLetterOrDigit() } &&
                it.confidence(TessBaseAPI.PageIteratorLevel.RIL_WORD) >= MIN_WORD
            ) words += word.lowercase()
        } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
        it.delete()
        words.joinToString(" ")
    } ?: ""

    /** The lines of text in a photo and where they are, for Live Text in the viewer. Boxes are in the photo's pixels. */
    suspend fun lines(context: Context, item: MediaItem): Pair<List<TextLine>, android.util.Size>? {
        var size: android.util.Size? = null
        val lines = read(context, item, LIVE_SIZE, onBitmap = { size = android.util.Size(it.width, it.height) }) { api, angle ->
            val found = ArrayList<TextLine>()
            val it = api.getResultIterator() ?: return@read found
            it.begin()
            do {
                val text = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)?.trim().orEmpty()
                if (text.any { c -> c.isLetterOrDigit() } && it.confidence(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE) >= MIN_LINE) {
                    found += unturn(it.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE), angle, size!!)
                        .let { (box, tilt) -> TextLine(text, box, tilt) }
                }
            } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE))
            it.delete()
            found
        } ?: return null
        return lines to (size ?: return null)
    }

    private suspend fun <T> read(
        context: Context,
        item: MediaItem,
        maxSide: Int,
        onBitmap: (android.graphics.Bitmap) -> Unit = {},
        use: (TessBaseAPI, Float) -> T,
    ): T? = withContext(Dispatchers.Default) {
        val bitmap = EditStore.decode(context, item, maxSide) ?: return@withContext null
        onBitmap(bitmap)
        lock.withLock {
            val api = engine(context) ?: return@withLock null
            val pix = runCatching { ReadFile.readBitmap(bitmap) }.getOrNull()
            var straight = pix
            try {
                // Slanted text (a label photographed at an angle) reads badly, so the photo is first
                // turned to make its lines level. Leptonica gives back the same picture when it
                // finds no clear slant.
                val found = FloatArray(2)
                if (pix != null) straight = runCatching { Skew.deskew(pix, found) }.getOrNull() ?: pix
                val angle = if (straight != null && straight.nativePix != pix?.nativePix) found[0] else 0f
                if (straight != null) api.setImage(straight) else api.setImage(bitmap)
                api.getUTF8Text() // runs the recognition; the iterators read its results
                use(api, angle)
            } catch (e: Exception) {
                null
            } finally {
                api.clear()
                if (straight != null && straight.nativePix != pix?.nativePix) straight.recycle()
                pix?.recycle()
                bitmap.recycle()
            }
        }
    }

    /**
     * Where a line found in the straightened picture is on the photo itself. Straightening turned
     * the photo [angle] degrees clockwise about its centre (keeping its size), so the line's centre
     * is turned back, and the line is drawn tilted the other way.
     */
    private fun unturn(box: Rect, angle: Float, size: android.util.Size): Pair<Rect, Float> {
        if (angle == 0f) return box to 0f
        val a = Math.toRadians(-angle.toDouble())
        val cx = size.width / 2.0
        val cy = size.height / 2.0
        val dx = box.exactCenterX() - cx
        val dy = box.exactCenterY() - cy
        val x = cx + dx * cos(a) - dy * sin(a)
        val y = cy + dx * sin(a) + dy * cos(a)
        val w = box.width() / 2.0
        val h = box.height() / 2.0
        return Rect((x - w).roundToInt(), (y - h).roundToInt(), (x + w).roundToInt(), (y + h).roundToInt()) to -angle
    }

    /** Starts Tesseract with the English model from the app's assets (copied out once). */
    private fun engine(context: Context): TessBaseAPI? {
        engine?.let { return it }
        val dir = File(context.filesDir, "tesseract")
        val model = File(dir, "tessdata/eng.traineddata")
        if (!model.exists()) {
            model.parentFile?.mkdirs()
            val tmp = File(model.path + ".tmp")
            runCatching { context.assets.open("tessdata/eng.traineddata").use { input -> tmp.outputStream().use { input.copyTo(it) } } }
                .onFailure { return null }
            tmp.renameTo(model)
        }
        val api = TessBaseAPI()
        if (!api.init(dir.path, "eng")) {
            api.recycle()
            return null
        }
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        return api.also { engine = it }
    }
}

/** The words found in each photo, kept in a small database so each photo is read only once. */
class TextIndex(context: Context) : SQLiteOpenHelper(context.applicationContext, "text.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE text (id INTEGER PRIMARY KEY, modified INTEGER NOT NULL, words TEXT NOT NULL)")
    }

    // Version 2 straightens slanted text before reading, so photos read before are read again.
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS text")
        onCreate(db)
    }

    /** id → (modified time it was read at, its words). */
    fun all(): Map<Long, Pair<Long, String>> {
        val out = HashMap<Long, Pair<Long, String>>()
        readableDatabase.rawQuery("SELECT id, modified, words FROM text", null).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getLong(1) to c.getString(2)
        }
        return out
    }

    fun put(id: Long, modified: Long, words: String) {
        writableDatabase.insertWithOnConflict(
            "text", null,
            ContentValues().apply { put("id", id); put("modified", modified); put("words", words) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }
}

package com.stickerlab.app.data

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.stickerlab.app.model.EditorState
import com.stickerlab.app.model.StickerItem
import com.stickerlab.app.model.StickerPack
import com.stickerlab.app.model.StickerShape
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class StickerRepository(private val context: Context) {

    private val rootDir: File = File(context.filesDir, "stickers").apply { mkdirs() }
    private val metaFile: File = File(rootDir, "packs.json")

    fun getPacks(): List<StickerPack> {
        if (!metaFile.exists()) {
            val defaultPack = createPack("My Stickers", "Me")
            return listOf(defaultPack)
        }
        return try {
            val arr = JSONArray(metaFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val sArr = o.optJSONArray("stickers") ?: JSONArray()
                val stickers = (0 until sArr.length()).map { j ->
                    val so = sArr.getJSONObject(j)
                    val eArr = so.optJSONArray("emojis") ?: JSONArray()
                    val emojis = (0 until eArr.length()).map { k -> eArr.getString(k) }
                    StickerItem(
                        fileName = so.getString("fileName"),
                        emojis = emojis.ifEmpty { listOf("✨") }
                    )
                }
                StickerPack(
                    identifier = o.getString("identifier"),
                    name = o.getString("name"),
                    publisher = o.optString("publisher", "StickerLab"),
                    trayImageFile = o.optString("trayImageFile", "tray.png"),
                    imageDataVersion = o.optInt("imageDataVersion", 1),
                    stickers = stickers
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getPack(identifier: String): StickerPack? = getPacks().find { it.identifier == identifier }

    fun createPack(name: String, publisher: String = "StickerLab"): StickerPack {
        val packs = if (metaFile.exists()) getPacks().toMutableList() else mutableListOf()
        val id = "pack_" + UUID.randomUUID().toString().take(8)
        val packDir = getPackDir(id).apply { mkdirs() }
        // Create a clean default 96x96 tray icon so pack metadata is always valid
        createDefaultTrayIcon(File(packDir, "tray.png"))

        val pack = StickerPack(
            identifier = id,
            name = name.trim().ifEmpty { "My Stickers" },
            publisher = publisher.trim().ifEmpty { "StickerLab" }
        )
        packs.add(0, pack)
        savePacks(packs)
        return pack
    }

    fun deletePack(identifier: String) {
        val packs = getPacks().filterNot { it.identifier == identifier }
        getPackDir(identifier).deleteRecursively()
        savePacks(packs)
    }

    fun deleteSticker(packId: String, fileName: String) {
        val packs = getPacks().map { pack ->
            if (pack.identifier != packId) pack
            else {
                File(getPackDir(packId), fileName).delete()
                pack.copy(
                    stickers = pack.stickers.filterNot { it.fileName == fileName },
                    imageDataVersion = pack.imageDataVersion + 1
                )
            }
        }
        savePacks(packs)
    }

    fun getPackDir(identifier: String): File = File(rootDir, identifier)

    fun getStickerFile(identifier: String, fileName: String): File =
        File(getPackDir(identifier), fileName)

    /**
     * Loads a Bitmap from an Image, GIF (first/decoded frame), or Video URI (at frameMs).
     */
    fun loadMediaBitmap(uri: Uri, isVideo: Boolean, frameMs: Long = 0L): Pair<Bitmap?, Long> {
        return try {
            if (isVideo) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val bmp = retriever.getFrameAtTime(
                    frameMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                retriever.release()
                bmp to dur
            } else {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                bmp to 0L
            }
        } catch (_: Exception) {
            null to 0L
        }
    }

    /**
     * Renders the 512x512 transparent sticker with shape mask, outline, transforms, and text overlay.
     */
    fun renderStickerBitmap(source: Bitmap, state: EditorState): Bitmap {
        val size = 512
        val mediaBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val mCanvas = Canvas(mediaBmp)

        // 1. Apply shape clip + transform
        mCanvas.save()
        buildShapePath(state.shape, size.toFloat(), state.outlineWidth + 10f)?.let { path ->
            mCanvas.clipPath(path)
        }

        val fitScale = min((size - 48f) / source.width, (size - 48f) / source.height)
        val drawW = source.width * fitScale
        val drawH = source.height * fitScale

        mCanvas.translate(size / 2f + state.offsetX, size / 2f + state.offsetY)
        mCanvas.rotate(state.rotation)
        mCanvas.scale(state.scale, state.scale)

        val srcRect = Rect(0, 0, source.width, source.height)
        val dstRect = RectF(-drawW / 2f, -drawH / 2f, drawW / 2f, drawH / 2f)
        mCanvas.drawBitmap(source, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        mCanvas.restore()

        // 2. Final composite with optional white sticker outline
        val finalBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBmp)

        if (state.outlineWidth > 0.5f) {
            val alphaBmp = mediaBmp.extractAlpha()
            val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = state.outlineColor
            }
            val steps = 16
            val r = state.outlineWidth
            for (i in 0 until steps) {
                val angle = (i.toDouble() / steps) * Math.PI * 2.0
                canvas.drawBitmap(
                    alphaBmp,
                    (cos(angle) * r).toFloat(),
                    (sin(angle) * r).toFloat(),
                    outlinePaint
                )
            }
            alphaBmp.recycle()
        }

        canvas.drawBitmap(mediaBmp, 0f, 0f, null)
        mediaBmp.recycle()

        // 3. Draw optional text overlay
        if (state.text.isNotBlank()) {
            val textY = size * state.textOffsetY
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = state.textSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                style = Paint.Style.STROKE
                strokeWidth = (state.textSize * 0.16f).coerceAtLeast(6f)
                strokeJoin = Paint.Join.ROUND
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = state.textColor
                textSize = state.textSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                style = Paint.Style.FILL
            }
            canvas.drawText(state.text.trim(), size / 2f, textY, strokePaint)
            canvas.drawText(state.text.trim(), size / 2f, textY, fillPaint)
        }

        return finalBmp
    }

    /**
     * Saves the 512x512 sticker as <100 KB WebP inside the selected pack and refreshes the 96x96 tray icon.
     */
    fun saveStickerToPack(packId: String, stickerBmp: Bitmap): File {
        val packDir = getPackDir(packId).apply { mkdirs() }
        val fileName = "stk_${System.currentTimeMillis()}.webp"
        val outFile = File(packDir, fileName)

        // Compress to WebP under WhatsApp's 100 KB limit
        var quality = 92
        var bytes: ByteArray
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        do {
            val baos = ByteArrayOutputStream()
            stickerBmp.compress(format, quality, baos)
            bytes = baos.toByteArray()
            quality -= 10
        } while (bytes.size > 98 * 1024 && quality >= 30)

        FileOutputStream(outFile).use { it.write(bytes) }

        // Update pack metadata & tray icon (96x96 PNG < 50 KB)
        val packs = getPacks().map { pack ->
            if (pack.identifier != packId) pack
            else {
                if (pack.stickers.isEmpty()) {
                    val tray = Bitmap.createScaledBitmap(stickerBmp, 96, 96, true)
                    FileOutputStream(File(packDir, pack.trayImageFile)).use { fos ->
                        tray.compress(Bitmap.CompressFormat.PNG, 90, fos)
                    }
                    tray.recycle()
                }
                pack.copy(
                    stickers = pack.stickers + StickerItem(fileName = fileName),
                    imageDataVersion = pack.imageDataVersion + 1
                )
            }
        }
        savePacks(packs)
        return outFile
    }

    /**
     * Exports a 512x512 transparent PNG for Telegram @Stickers bot.
     */
    fun exportForTelegramPng(stickerBmp: Bitmap): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "telegram_sticker_${System.currentTimeMillis()}.png")
        FileOutputStream(out).use { fos ->
            stickerBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        return out
    }

    private fun savePacks(packs: List<StickerPack>) {
        val arr = JSONArray()
        packs.forEach { p ->
            val o = JSONObject().apply {
                put("identifier", p.identifier)
                put("name", p.name)
                put("publisher", p.publisher)
                put("trayImageFile", p.trayImageFile)
                put("imageDataVersion", p.imageDataVersion)
                val sArr = JSONArray()
                p.stickers.forEach { s ->
                    sArr.put(JSONObject().apply {
                        put("fileName", s.fileName)
                        put("emojis", JSONArray(s.emojis))
                    })
                }
                put("stickers", sArr)
            }
            arr.put(o)
        }
        metaFile.writeText(arr.toString(2))
    }

    private fun createDefaultTrayIcon(target: File) {
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF25D366.toInt() }
        c.drawCircle(48f, 48f, 42f, p)
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0D14.toInt() }
        c.drawCircle(35f, 40f, 5f, eyePaint)
        c.drawCircle(61f, 40f, 5f, eyePaint)
        val smile = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A0D14.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        c.drawArc(RectF(30f, 38f, 66f, 66f), 20f, 140f, false, smile)
        FileOutputStream(target).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    private fun buildShapePath(shape: StickerShape, size: Float, pad: Float): Path? {
        val w = size - pad * 2f
        return when (shape) {
            StickerShape.FULL -> null
            StickerShape.ROUNDED -> Path().apply {
                addRoundRect(RectF(pad, pad, size - pad, size - pad), 72f, 72f, Path.Direction.CW)
            }
            StickerShape.CIRCLE -> Path().apply {
                addCircle(size / 2f, size / 2f, w / 2f, Path.Direction.CW)
            }
            StickerShape.HEART -> Path().apply {
                val cx = size / 2f
                val cy = size / 2f + 16f
                val s = w * 0.88f
                moveTo(cx, cy + s * 0.42f)
                cubicTo(cx - s * 0.9f, cy - s * 0.05f, cx - s * 0.55f, cy - s * 0.72f, cx, cy - s * 0.2f)
                cubicTo(cx + s * 0.55f, cy - s * 0.72f, cx + s * 0.9f, cy - s * 0.05f, cx, cy + s * 0.42f)
                close()
            }
            StickerShape.STAR -> Path().apply {
                val cx = size / 2f
                val cy = size / 2f + 8f
                val outerR = w / 2f
                val innerR = w * 0.24f
                var rot = Math.PI / 2.0 * 3.0
                val step = Math.PI / 5.0
                moveTo(cx, cy - outerR)
                for (i in 0 until 5) {
                    lineTo(cx + (cos(rot) * outerR).toFloat(), cy + (sin(rot) * outerR).toFloat())
                    rot += step
                    lineTo(cx + (cos(rot) * innerR).toFloat(), cy + (sin(rot) * innerR).toFloat())
                    rot += step
                }
                close()
            }
        }
    }
}

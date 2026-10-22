package com.example.contactgrid

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

sealed interface CropSource {
    data class FromUri(val uri: Uri) : CropSource
    data class FromContact(val contactId: String) : CropSource
}

data class CropRequest(val personId: String, val source: CropSource)

/** Прямоугольник обрезки в пикселях исходной картинки. */
data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

object Photos {
    private const val MAX_SIDE = 2048

    fun load(ctx: Context, source: CropSource): Bitmap? = try {
        when (source) {
            is CropSource.FromUri -> loadUri(ctx, source.uri)
            is CropSource.FromContact -> {
                val uri = ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI, source.contactId.toLong()
                )
                ContactsContract.Contacts.openContactPhotoInputStream(ctx.contentResolver, uri, true)
                    ?.use { BitmapFactory.decodeStream(it) }
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun loadUri(ctx: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder сам учитывает поворот снимка с камеры
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val m = max(info.size.width, info.size.height)
                if (m > MAX_SIDE) {
                    val s = MAX_SIDE.toFloat() / m
                    dec.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Вырезает область, приводит к размеру карточки и сохраняет. Возвращает file:// адрес. */
    fun saveCrop(ctx: Context, contactId: String, bmp: Bitmap, r: CropRect, aspect: Float): String? = try {
        val cropped = Bitmap.createBitmap(bmp, r.left, r.top, r.width, r.height)
        val outW = minOf(720, r.width)
        val outH = (outW / aspect).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(cropped, outW, outH, true)
        ContactsReader.deletePhotos(ctx, contactId)
        // Имя каждый раз новое, иначе загрузчик покажет старое фото из кэша
        val file = File(ctx.filesDir, "photo_${contactId}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Uri.fromFile(file).toString()
    } catch (e: Exception) {
        null
    }
}

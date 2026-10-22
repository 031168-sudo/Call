package com.example.contactgrid

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

const val MIME_TELEGRAM = "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile"
const val MIME_WHATSAPP = "vnd.android.cursor.item/vnd.com.whatsapp.profile"

/** Человек в сетке. Флаги мессенджеров определяются по записям контакта при добавлении. */
data class Person(
    val id: String,
    val name: String,
    val phone: String?,
    val photo: String?,
    val hasTelegram: Boolean,
    val hasWhatsApp: Boolean,
    /** Место на «рабочем столе»: страница * 12 + позиция на странице. Пустые места допускаются. */
    val slot: Int = 0
)

/** Контакт из системного выбора, ещё не сохранённый: нужен выбор номера, если их несколько. */
data class Candidate(
    val id: String,
    val name: String,
    val photo: String?,
    val phones: List<String>,
    val hasTelegram: Boolean,
    val hasWhatsApp: Boolean
)

data class DataRow(val mimetype: String, val data1: String?)

object ContactStore {
    private const val PREFS = "people"
    private const val KEY = "list"

    fun load(ctx: Context): List<Person> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Person(
                id = o.getString("id"),
                name = o.getString("name"),
                phone = o.optString("phone").ifEmpty { null },
                photo = o.optString("photo").ifEmpty { null },
                hasTelegram = o.optBoolean("telegram"),
                hasWhatsApp = o.optBoolean("whatsapp"),
                slot = o.optInt("slot", i)   // старые сохранения без слота — по порядку
            )
        }
    }

    fun save(ctx: Context, list: List<Person>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("phone", p.phone ?: "")
                    .put("photo", p.photo ?: "")
                    .put("telegram", p.hasTelegram)
                    .put("whatsapp", p.hasWhatsApp)
                    .put("slot", p.slot)
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}

object ContactsReader {

    /** Читает контакт, выбранный через системный выбор. Возвращает null, если не удалось прочитать. */
    fun fromPicked(ctx: Context, uri: Uri): Candidate? {
        val cursor = ctx.contentResolver.query(
            uri,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.PHOTO_URI),
            null, null, null
        ) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val id = it.getString(0)
            val name = it.getString(1) ?: "Без имени"
            val photo = it.getString(2)
            val rows = rows(ctx, id)
            return Candidate(
                id = id,
                name = name,
                photo = photo,
                phones = phones(ctx, id),
                hasTelegram = rows.any { r -> r.mimetype == MIME_TELEGRAM },
                hasWhatsApp = rows.any { r -> r.mimetype == MIME_WHATSAPP }
            )
        }
    }

    /** Все записи контакта: нужны для флагов мессенджеров и для экрана отладки. */
    fun rows(ctx: Context, contactId: String): List<DataRow> {
        val out = mutableListOf<DataRow>()
        ctx.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
            "${ContactsContract.Data.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { c ->
            while (c.moveToNext()) out += DataRow(c.getString(0) ?: "", c.getString(1))
        }
        return out
    }

    /** Все номера контакта без повторов. */
    fun phones(ctx: Context, contactId: String): List<String> {
        val out = linkedSetOf<String>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { c ->
            while (c.moveToNext()) c.getString(0)?.let { out += it }
        }
        return out.toList()
    }

    /** Фото из самого контакта (для кнопки «Фото из контакта»). */
    fun contactPhoto(ctx: Context, contactId: String): String? =
        ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.PHOTO_URI),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId),
            null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** Признаки мессенджеров заново — при редактировании карточки. */
    fun messengers(ctx: Context, contactId: String): Pair<Boolean, Boolean> {
        val rows = rows(ctx, contactId)
        return rows.any { it.mimetype == MIME_TELEGRAM } to rows.any { it.mimetype == MIME_WHATSAPP }
    }

    /**
     * Копирует фото, выбранное вручную, в хранилище приложения. Возвращает file:// адрес.
     * Имя файла каждый раз новое, иначе загрузчик картинок покажет старое фото из кэша.
     */
    fun copyPhoto(ctx: Context, source: Uri, contactId: String): String? {
        ctx.filesDir.listFiles { f -> f.name.startsWith("photo_${contactId}_") }?.forEach { it.delete() }
        val target = File(ctx.filesDir, "photo_${contactId}_${System.currentTimeMillis()}.jpg")
        ctx.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: return null
        return Uri.fromFile(target).toString()
    }

    fun deletePhotos(ctx: Context, contactId: String) {
        ctx.filesDir.listFiles { f -> f.name.startsWith("photo_${contactId}_") }?.forEach { it.delete() }
    }
}

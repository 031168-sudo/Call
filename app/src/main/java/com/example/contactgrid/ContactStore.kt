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
                hasWhatsApp = o.optBoolean("whatsapp")
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
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}

object ContactsReader {

    /** Контакт, выбранный через системный выбор контактов. Возвращает null, если не удалось прочитать. */
    fun fromPicked(ctx: Context, uri: Uri): Person? {
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
            return build(ctx, id, name, photo)
        }
    }

    fun build(ctx: Context, contactId: String, name: String, photo: String?): Person {
        val rows = rows(ctx, contactId)
        return Person(
            id = contactId,
            name = name,
            phone = phone(ctx, contactId),
            photo = photo,
            hasTelegram = rows.any { it.mimetype == MIME_TELEGRAM },
            hasWhatsApp = rows.any { it.mimetype == MIME_WHATSAPP }
        )
    }

    /** Все записи контакта: нужны и для флагов, и для экрана отладки. */
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

    fun phone(ctx: Context, contactId: String): String? =
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** Копирует фото, выбранное вручную, в хранилище приложения. Возвращает file:// адрес. */
    fun copyPhoto(ctx: Context, source: Uri, contactId: String): String? {
        val target = File(ctx.filesDir, "photo_$contactId.jpg")
        ctx.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: return null
        return Uri.fromFile(target).toString()
    }
}

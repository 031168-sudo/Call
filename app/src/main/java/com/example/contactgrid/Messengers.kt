package com.example.contactgrid

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

// Пакеты: WhatsApp и Telegram стандартные. Max — ru.oneme.app, уточнить на устройстве.
private const val WHATSAPP_PKG = "com.whatsapp"
private const val TELEGRAM_PKG = "org.telegram.messenger"
private const val MAX_PKG = "ru.oneme.app"

private fun digits(phone: String) = phone.filter { it.isDigit() }

/** Открывает номеронабиратель с номером — запасной вариант без разрешения на звонки. */
fun callIntent(phone: String): Intent =
    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))

/** Звонит сразу. Требует разрешения CALL_PHONE. */
fun directCallIntent(phone: String): Intent =
    Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone"))

fun whatsAppIntent(phone: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${digits(phone)}"))
        .setPackage(WHATSAPP_PKG)

/** Telegram открывает чат по номеру через tg://resolve. Работоспособность проверяется на устройстве. */
fun telegramIntent(phone: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=${digits(phone)}"))
        .setPackage(TELEGRAM_PKG)

/** Max не добавляет записей в контакты, поэтому номер кладём в буфер, а приложение открываем вручную. */
fun openMax(ctx: Context, phone: String?) {
    if (phone != null) {
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("phone", phone))
        Toast.makeText(ctx, "Номер скопирован. Вставьте его в поиск Max", Toast.LENGTH_LONG).show()
    }
    val launch = ctx.packageManager.getLaunchIntentForPackage(MAX_PKG)
    if (launch == null) {
        Toast.makeText(ctx, "Max не установлен", Toast.LENGTH_SHORT).show()
    } else {
        ctx.launch(launch)
    }
}

/** Запускает intent и не падает, если нужного приложения нет. */
fun Context.launch(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Приложение не найдено", Toast.LENGTH_SHORT).show()
    }
}

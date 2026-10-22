package com.example.contactgrid

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Приложение рисуется под системными панелями, их цвет следует системной теме
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                App()
            }
        }
    }
}

private val WhatsAppGreen = Color(0xFF25D366)
private val TelegramBlue = Color(0xFF229ED9)
private val MaxViolet = Color(0xFF6A4DFF)

private fun LazyGridItemInfo.contains(p: Offset): Boolean =
    p.x >= offset.x && p.x <= offset.x + size.width &&
        p.y >= offset.y && p.y <= offset.y + size.height

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()

    var people by remember { mutableStateOf(ContactStore.load(ctx)) }
    var selected by remember { mutableStateOf<Person?>(null) }      // меню звонка (короткое нажатие)
    var menuFor by remember { mutableStateOf<String?>(null) }       // меню «Изменить / Удалить» (долгое нажатие)
    var editFor by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<Person?>(null) }
    var debugFor by remember { mutableStateOf<Person?>(null) }
    var photoFor by remember { mutableStateOf<String?>(null) }
    var pendingCandidate by remember { mutableStateOf<Candidate?>(null) }

    // Перетаскивание
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun update(list: List<Person>) {
        people = list
        ContactStore.save(ctx, list)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { permission.launch(Manifest.permission.READ_CONTACTS) }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val c = ContactsReader.fromPicked(ctx, uri)
            when {
                c == null -> Unit
                people.any { it.id == c.id } -> Unit
                c.phones.size > 1 -> pendingCandidate = c   // несколько номеров — спросим
                else -> update(people + c.toPerson(c.phones.firstOrNull()))
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = photoFor
        if (uri != null && id != null) {
            ContactsReader.copyPhoto(ctx, uri, id)?.let { file ->
                update(people.map { if (it.id == id) it.copy(photo = file) else it })
            }
        }
        photoFor = null
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { pickContact.launch(null) }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить контакт")
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            userScrollEnabled = draggingKey == null,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(padding)
                .padding(8.dp)
                // Один обработчик на всю сетку: нажатие, долгое нажатие и перетаскивание
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = gridState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.contains(down.position) } ?: return@awaitEachGesture
                        val key = start.key as String

                        // Ждём: отпустили быстро (нажатие), повели (прокрутка) или держат (долгое нажатие)
                        val quickResult = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var r = false
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (ch.changedToUpIgnoreConsumed()) { r = true; break }
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                            }
                            r
                        }
                        when (quickResult) {
                            true -> { selected = people.firstOrNull { it.id == key }; return@awaitEachGesture }
                            false -> return@awaitEachGesture        // это прокрутка
                            null -> Unit                            // долгое нажатие
                        }

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        draggingKey = key
                        dragOffset = Offset.Zero
                        var moved = false

                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (ch.changedToUpIgnoreConsumed()) break
                            dragOffset += ch.positionChange()
                            ch.consume()
                            if (!moved && dragOffset.getDistance() > viewConfiguration.touchSlop) moved = true
                            if (!moved) continue

                            // Если центр карточки над другой — переставляем
                            val info = gridState.layoutInfo.visibleItemsInfo
                            val cur = info.firstOrNull { it.key == key } ?: continue
                            if (cur.index != people.indexOfFirst { it.id == key }) continue
                            val center = Offset(
                                cur.offset.x + cur.size.width / 2f,
                                cur.offset.y + cur.size.height / 2f
                            ) + dragOffset
                            val target = info.firstOrNull { it.key != key && it.contains(center) } ?: continue
                            people = people.toMutableList().apply { add(target.index, removeAt(cur.index)) }
                            dragOffset += Offset(
                                (cur.offset.x - target.offset.x).toFloat(),
                                (cur.offset.y - target.offset.y).toFloat()
                            )
                        }

                        draggingKey = null
                        dragOffset = Offset.Zero
                        if (moved) ContactStore.save(ctx, people) else menuFor = key
                    }
                }
        ) {
            items(people, key = { it.id }) { p ->
                val dragging = p.id == draggingKey
                val itemModifier = if (dragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = 1.08f
                            scaleY = 1.08f
                            shadowElevation = 24f
                        }
                } else {
                    Modifier.animateItem()
                }
                Box(itemModifier) {
                    PersonCard(p, lifted = dragging)
                    DropdownMenu(
                        expanded = menuFor == p.id,
                        onDismissRequest = { menuFor = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Изменить") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuFor = null; editFor = p.id }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuFor = null; deleteFor = p }
                        )
                    }
                }
            }
        }
    }

    // Выбор номера при добавлении, если у контакта их несколько
    pendingCandidate?.let { c ->
        PhonePickDialog(
            title = "Какой номер у ${c.name}?",
            phones = c.phones,
            initial = c.phones.first(),
            onDismiss = { pendingCandidate = null },
            onConfirm = { number ->
                update(people + c.toPerson(number))
                pendingCandidate = null
            }
        )
    }

    // Меню звонка
    selected?.let { p ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Text(
                p.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            p.phone?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            val phone = p.phone
            if (phone != null) {
                ActionRow(
                    icon = { Badge(MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(20.dp)) } },
                    label = "Позвонить"
                ) { ctx.launch(callIntent(phone)); selected = null }
                if (p.hasWhatsApp) {
                    ActionRow(icon = { LetterBadge("W", WhatsAppGreen) }, label = "WhatsApp") {
                        ctx.launch(whatsAppIntent(phone)); selected = null
                    }
                }
                if (p.hasTelegram) {
                    ActionRow(icon = { LetterBadge("T", TelegramBlue) }, label = "Telegram") {
                        ctx.launch(telegramIntent(phone)); selected = null
                    }
                }
            }
            ActionRow(icon = { LetterBadge("M", MaxViolet) }, label = "Max") {
                openMax(ctx, phone); selected = null
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Редактирование карточки
    editFor?.let { id ->
        val p = people.firstOrNull { it.id == id }
        if (p == null) { editFor = null; return@let }
        EditDialog(
            ctx = ctx,
            person = p,
            onDismiss = { editFor = null },
            onPickPhoto = {
                photoFor = p.id
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onContactPhoto = {
                ContactsReader.deletePhotos(ctx, p.id)
                val photo = ContactsReader.contactPhoto(ctx, p.id)
                update(people.map { if (it.id == p.id) it.copy(photo = photo) else it })
            },
            onShowRows = { debugFor = p },
            onSave = { number ->
                val (tg, wa) = ContactsReader.messengers(ctx, p.id)
                update(people.map {
                    if (it.id == p.id) it.copy(phone = number, hasTelegram = tg, hasWhatsApp = wa) else it
                })
                editFor = null
            }
        )
    }

    deleteFor?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Удалить карточку?") },
            text = { Text("${p.name} будет убран(а) из сетки. В телефонной книге контакт останется.") },
            confirmButton = {
                TextButton(onClick = {
                    ContactsReader.deletePhotos(ctx, p.id)
                    update(people.filter { it.id != p.id })
                    deleteFor = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Отмена") } }
        )
    }

    debugFor?.let { p ->
        val rows = remember(p.id) { ContactsReader.rows(ctx, p.id) }
        AlertDialog(
            onDismissRequest = { debugFor = null },
            confirmButton = { TextButton(onClick = { debugFor = null }) { Text("Закрыть") } },
            title = { Text("Записи: ${p.name}") },
            text = {
                Column {
                    rows.forEach { r ->
                        Text(
                            "${r.mimetype.substringAfterLast('/')}: ${r.data1 ?: ""}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        )
    }
}

private fun Candidate.toPerson(phone: String?) = Person(
    id = id, name = name, phone = phone, photo = photo,
    hasTelegram = hasTelegram, hasWhatsApp = hasWhatsApp
)

@Composable
fun PersonCard(person: Person, lifted: Boolean) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (lifted) 0.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
    ) {
        Box(Modifier.fillMaxSize()) {
            val photo = person.photo
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        person.name.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            // Подпись: одна строка мелким шрифтом у самого низа
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(start = 7.dp, end = 7.dp, top = 10.dp, bottom = 4.dp)
            ) {
                Text(
                    person.name,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

@Composable
private fun PhonePickDialog(
    title: String,
    phones: List<String>,
    initial: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var chosen by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { PhoneList(phones, chosen) { chosen = it } },
        confirmButton = { TextButton(onClick = { onConfirm(chosen) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun PhoneList(phones: List<String>, chosen: String?, onChoose: (String) -> Unit) {
    Column {
        phones.forEach { number ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onChoose(number) }
            ) {
                RadioButton(selected = number == chosen, onClick = { onChoose(number) })
                Text(number, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun EditDialog(
    ctx: Context,
    person: Person,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onContactPhoto: () -> Unit,
    onShowRows: () -> Unit,
    onSave: (String?) -> Unit
) {
    val phones = remember(person.id) { ContactsReader.phones(ctx, person.id) }
    var chosen by remember(person.id) { mutableStateOf(person.phone ?: phones.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(person.name) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (person.photo != null) {
                            AsyncImage(
                                model = person.photo,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(person.name.firstOrNull()?.uppercase() ?: "?", fontSize = 28.sp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        OutlinedButton(onClick = onPickPhoto) { Text("Выбрать фото") }
                        TextButton(onClick = onContactPhoto) { Text("Фото из контакта") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Номер", style = MaterialTheme.typography.labelLarge)
                if (phones.isEmpty()) {
                    Text("В контакте нет номеров", style = MaterialTheme.typography.bodyMedium)
                } else {
                    PhoneList(phones, chosen) { chosen = it }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onShowRows) {
                    Text("Записи контакта (тест)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun Badge(color: Color, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun LetterBadge(letter: String, color: Color) {
    Badge(color) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun ActionRow(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

package com.example.contactgrid

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateIntOffsetAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var people by remember { mutableStateOf(normalize(ContactStore.load(ctx))) }
    var selected by remember { mutableStateOf<Person?>(null) }      // меню звонка (короткое нажатие)
    var menuFor by remember { mutableStateOf<String?>(null) }       // «Изменить / Удалить» (долгое нажатие)
    var editFor by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<Person?>(null) }
    var debugFor by remember { mutableStateOf<Person?>(null) }
    var photoFor by remember { mutableStateOf<String?>(null) }
    var pendingCandidate by remember { mutableStateOf<Candidate?>(null) }

    // Подгонка фото
    var cropReq by remember { mutableStateOf<CropRequest?>(null) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Геометрия и перетаскивание
    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragMoved by remember { mutableStateOf(false) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var edgeDir by remember { mutableStateOf(0) }

    val padPx = with(density) { 8.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    fun grid(): Grid? = Grid.of(areaSize.width, areaSize.height, padPx, gapPx)
    val cardAspect = grid()?.aspect ?: 0.75f

    // Во время перетаскивания в конце есть пустая страница — туда можно унести карточку
    val pagerState = rememberPagerState { pageCount(people) + if (draggingKey != null) 1 else 0 }

    fun hoverSlot(): Int? {
        val g = grid() ?: return null
        if (draggingKey == null || !dragMoved) return null
        val center = finger - grab + Offset(g.cellW / 2, g.cellH / 2)
        return pagerState.currentPage * PER_PAGE + g.cellAtClamped(center)
    }

    fun update(list: List<Person>) {
        people = list
        ContactStore.save(ctx, list)
    }

    fun addCandidate(c: Candidate, phone: String?) {
        val slot = firstFreeSlot(people)
        update(people + c.toPerson(phone, slot))
        scope.launch { pagerState.animateScrollToPage(slot / PER_PAGE) }
        if (c.photo != null) cropReq = CropRequest(c.id, CropSource.FromContact(c.id))
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { permission.launch(Manifest.permission.READ_CONTACTS) }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val c = ContactsReader.fromPicked(ctx, uri)
            when {
                c == null -> Unit
                people.any { it.id == c.id } -> Toast.makeText(ctx, "${c.name} уже есть", Toast.LENGTH_SHORT).show()
                c.phones.size > 1 -> pendingCandidate = c   // несколько номеров — спросим
                else -> addCandidate(c, c.phones.firstOrNull())
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = photoFor
        if (uri != null && id != null) cropReq = CropRequest(id, CropSource.FromUri(uri))
        photoFor = null
    }

    // Загрузка фото для подгонки — в фоне
    LaunchedEffect(cropReq) {
        cropBitmap = null
        val req = cropReq ?: return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) { Photos.load(ctx, req.source) }
        if (bmp == null) {
            Toast.makeText(ctx, "Не удалось открыть фото", Toast.LENGTH_SHORT).show()
            cropReq = null
        } else {
            cropBitmap = bmp
        }
    }

    // Карточку держат у края экрана — листаем страницы
    LaunchedEffect(edgeDir) {
        if (edgeDir == 0) return@LaunchedEffect
        while (true) {
            delay(650)
            val target = pagerState.currentPage + edgeDir
            if (target !in 0 until pagerState.pageCount) break
            pagerState.animateScrollToPage(target)
        }
    }

    val layout = arrange(people, draggingKey, hoverSlot())
    val pages = pagerState.pageCount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Звонилка") },
                actions = {
                    IconButton(onClick = { pickContact.launch(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить контакт")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(24.dp)
            ) {
                if (pages > 1) {
                    repeat(pages) { i ->
                        val active = i == pagerState.currentPage
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (active) 9.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                                .align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .onSizeChanged { areaSize = it }
                // Один обработчик: нажатие, долгое нажатие, перетаскивание. Свайп страниц — у пейджера.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val g = grid() ?: return@awaitEachGesture
                        if (pagerState.isScrollInProgress) return@awaitEachGesture
                        val pos = g.cellAt(down.position) ?: return@awaitEachGesture
                        val slot = pagerState.currentPage * PER_PAGE + pos
                        val person = people.firstOrNull { it.slot == slot } ?: return@awaitEachGesture

                        // Отпустили быстро — нажатие; повели — свайп страницы; держат — долгое нажатие
                        val quick = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var tapped = false
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (ch.changedToUpIgnoreConsumed()) { tapped = true; break }
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                            }
                            tapped
                        }
                        when (quick) {
                            true -> { selected = person; return@awaitEachGesture }
                            false -> return@awaitEachGesture
                            null -> Unit
                        }

                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        grab = down.position - g.origin(pos)
                        finger = down.position
                        dragMoved = false
                        draggingKey = person.id
                        val edge = 36.dp.toPx()

                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (ch.changedToUpIgnoreConsumed()) break
                            finger += ch.positionChange()
                            ch.consume()
                            if (!dragMoved && (finger - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragMoved = true
                            }
                            edgeDir = when {
                                !dragMoved -> 0
                                finger.x < edge -> -1
                                finger.x > size.width - edge -> 1
                                else -> 0
                            }
                        }

                        edgeDir = 0
                        if (dragMoved) {
                            val placed = arrange(people, person.id, hoverSlot())
                            update(normalize(people.map { it.copy(slot = placed[it.id] ?: it.slot) }))
                        } else {
                            menuFor = person.id
                        }
                        draggingKey = null
                        dragMoved = false
                    }
                }
        ) {
            val g = grid()
            if (g != null) {
                val cellW = with(density) { g.cellW.toDp() }
                val cellH = with(density) { g.cellH.toDp() }

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = draggingKey == null,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Box(Modifier.fillMaxSize()) {
                        people.filter { (layout[it.id] ?: it.slot) / PER_PAGE == page }.forEach { p ->
                            key(p.id) {
                                val slot = layout[p.id] ?: p.slot
                                val o = g.origin(slot % PER_PAGE)
                                val target = IntOffset(o.x.roundToInt(), o.y.roundToInt())
                                val animated by animateIntOffsetAsState(target, label = "card")
                                Box(
                                    Modifier
                                        .offset { animated }
                                        .size(cellW, cellH)
                                        .graphicsLayer { alpha = if (p.id == draggingKey) 0f else 1f }
                                ) {
                                    PersonCard(p, lifted = false)
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
                }

                if (people.isEmpty()) {
                    Text(
                        "Нажмите «+» вверху, чтобы добавить контакт",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                }

                // Карточка «в руке» поверх всех страниц
                people.firstOrNull { it.id == draggingKey }?.let { p ->
                    Box(
                        Modifier
                            .zIndex(2f)
                            .offset { (finger - grab).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                            .size(cellW, cellH)
                            .graphicsLayer {
                                scaleX = 1.08f
                                scaleY = 1.08f
                                shadowElevation = 24f
                                shape = RoundedCornerShape(18.dp)
                                clip = false
                            }
                    ) {
                        PersonCard(p, lifted = true)
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
                pendingCandidate = null
                addCandidate(c, number)
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
                    icon = {
                        Badge(MaterialTheme.colorScheme.primary) {
                            Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    },
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
        if (p == null) {
            editFor = null
        } else {
            EditDialog(
                ctx = ctx,
                person = p,
                onDismiss = { editFor = null },
                onPickPhoto = {
                    photoFor = p.id
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onContactPhoto = { cropReq = CropRequest(p.id, CropSource.FromContact(p.id)) },
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
    }

    // Подгонка фото под карточку
    cropReq?.let { req ->
        val bmp = cropBitmap
        if (bmp == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            CropDialog(
                bitmap = bmp,
                aspect = cardAspect,
                onCancel = { cropReq = null },
                onDone = { rect ->
                    val file = Photos.saveCrop(ctx, req.personId, bmp, rect, cardAspect)
                    if (file != null) {
                        update(people.map { if (it.id == req.personId) it.copy(photo = file) else it })
                    } else {
                        Toast.makeText(ctx, "Не удалось сохранить фото", Toast.LENGTH_SHORT).show()
                    }
                    cropReq = null
                }
            )
        }
    }

    deleteFor?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Удалить карточку?") },
            text = { Text("${p.name} будет убран(а) с экрана. В телефонной книге контакт останется.") },
            confirmButton = {
                TextButton(onClick = {
                    ContactsReader.deletePhotos(ctx, p.id)
                    update(normalize(people.filter { it.id != p.id }))
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

private fun Candidate.toPerson(phone: String?, slot: Int) = Person(
    id = id, name = name, phone = phone, photo = photo,
    hasTelegram = hasTelegram, hasWhatsApp = hasWhatsApp, slot = slot
)

@Composable
fun PersonCard(person: Person, lifted: Boolean) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (lifted) 0.dp else 2.dp),
        modifier = Modifier.fillMaxSize()
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
                            .size(width = 66.dp, height = 88.dp)
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
                        OutlinedButton(onClick = onPickPhoto) { Text("Фото из галереи") }
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

package com.example.contactgrid

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Приложение рисуется под статус-баром и навигационной панелью, цвет панелей берётся из системной темы
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                App()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    var people by remember { mutableStateOf(ContactStore.load(ctx)) }
    var selected by remember { mutableStateOf<Person?>(null) }
    var photoFor by remember { mutableStateOf<Person?>(null) }
    var debugFor by remember { mutableStateOf<Person?>(null) }
    var pendingCandidate by remember { mutableStateOf<Candidate?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { permission.launch(Manifest.permission.READ_CONTACTS) }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            val c = ContactsReader.fromPicked(ctx, uri)
            when {
                c == null -> Unit
                c.phones.size > 1 -> pendingCandidate = c   // несколько номеров: спросим, какой выбрать
                else -> {
                    addPerson(ctx, people, c, c.phones.firstOrNull())?.let { people = it }
                }
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = photoFor
        if (uri != null && target != null) {
            ContactsReader.copyPhoto(ctx, uri, target.id)?.let { file ->
                people = people.map { if (it.id == target.id) it.copy(photo = file) else it }
                ContactStore.save(ctx, people)
            }
        }
        photoFor = null
    }

    fun changePhoto(p: Person) {
        photoFor = p
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
            modifier = Modifier.padding(padding).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(people, key = { it.id }) { p ->
                PersonCard(
                    person = p,
                    onClick = { selected = p },
                    onEditPhoto = { changePhoto(p) },
                    onLongClick = { debugFor = p }
                )
            }
        }
    }

    // Выбор номера, если у контакта их несколько
    pendingCandidate?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingCandidate = null },
            confirmButton = { TextButton(onClick = { pendingCandidate = null }) { Text("Отмена") } },
            title = { Text("Какой номер у ${c.name}?") },
            text = {
                Column {
                    c.phones.forEach { number ->
                        Text(
                            number,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    addPerson(ctx, people, c, number)?.let { people = it }
                                    pendingCandidate = null
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        )
    }

    selected?.let { p ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Text(
                p.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            val phone = p.phone
            if (phone != null) {
                ActionRow("Позвонить") {
                    ctx.launch(callIntent(phone))
                    selected = null
                }
                if (p.hasWhatsApp) {
                    ActionRow("WhatsApp") { ctx.launch(whatsAppIntent(phone)) }
                }
                if (p.hasTelegram) {
                    ActionRow("Telegram") { ctx.launch(telegramIntent(phone)) }
                }
            }
            ActionRow("Max") { openMax(ctx, phone) }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ActionRow("Сменить фото") {
                selected = null
                changePhoto(p)
            }
            ActionRow("Записи контакта (тест)") {
                selected = null
                debugFor = p
            }
            Spacer(Modifier.height(24.dp))
        }
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

/** Добавляет контакт с выбранным номером. Возвращает новый список или null, если контакт уже есть. */
private fun addPerson(
    ctx: android.content.Context,
    current: List<Person>,
    c: Candidate,
    phone: String?
): List<Person>? {
    if (current.any { it.id == c.id }) return null
    val person = Person(
        id = c.id,
        name = c.name,
        phone = phone,
        photo = c.photo,
        hasTelegram = c.hasTelegram,
        hasWhatsApp = c.hasWhatsApp
    )
    val updated = current + person
    ContactStore.save(ctx, updated)
    return updated
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonCard(
    person: Person,
    onClick: () -> Unit,
    onEditPhoto: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        person.name.firstOrNull()?.toString() ?: "?",
                        fontSize = 48.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Text(
                    person.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
            }
            // Кнопка смены фото прямо на карточке
            IconButton(
                onClick = onEditPhoto,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Сменить фото", tint = Color.White)
            }
        }
    }
}

@Composable
fun ActionRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

package com.example.contactgrid

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
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

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { permission.launch(Manifest.permission.READ_CONTACTS) }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            ContactsReader.fromPicked(ctx, uri)?.let { p ->
                if (people.none { it.id == p.id }) {
                    people = people + p
                    ContactStore.save(ctx, people)
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
                    onLongClick = { debugFor = p }
                )
            }
        }
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
            ActionRow("Добавить фото") {
                photoFor = p
                selected = null
                pickPhoto.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonCard(person: Person, onClick: () -> Unit, onLongClick: () -> Unit) {
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
            .combinedClickableSimple(onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSimple(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

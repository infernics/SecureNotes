package com.andrey.securenotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Тёмная тема: белые иконки статус-бара
        window.statusBarColor = android.graphics.Color.rgb(16, 17, 18)
        window.navigationBarColor = android.graphics.Color.rgb(16, 17, 18)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent { App() }
    }
}

// ── Корень приложения: экраны «Создание пароля» / «Разблокировка» / «Заметки»

@Composable
fun App() {
    val context = LocalContext.current
    var crypto by remember { mutableStateOf<Crypto?>(null) }
    var needsSetup by remember { mutableStateOf(!Vault.isSetup(context)) }

    // Автозащита: как только приложение уходит в фон — сразу блокируем
    // (ключ стирается из памяти, разблокировка — только по паролю)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) crypto = null
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            // Спокойная монохромная палитра: графит, серые поверхности и мягкий текст.
            primary = Color(0xFFC5C8CC),
            onPrimary = Color(0xFF17191B),
            primaryContainer = Color(0xFF303438),
            onPrimaryContainer = Color(0xFFE5E7E9),
            secondary = Color(0xFFB8BDC1),
            onSecondary = Color(0xFF1B1D1F),
            secondaryContainer = Color(0xFF292D31),
            onSecondaryContainer = Color(0xFFE0E3E5),
            tertiary = Color(0xFFADB3B8),
            onTertiary = Color(0xFF191B1D),
            background = Color(0xFF101112),
            onBackground = Color(0xFFF1F1F1),
            surface = Color(0xFF151719),
            onSurface = Color(0xFFF1F1F1),
            surfaceVariant = Color(0xFF202326),
            onSurfaceVariant = Color(0xFFB5B9BD),
            outline = Color(0xFF42474B),
            outlineVariant = Color(0xFF303438),
            error = Color(0xFFD6A09B),
            onError = Color(0xFF351F1D)
        ),
        typography = Typography(
            titleLarge = TextStyle(fontSize = 22.sp, fontFamily = FontFamily.SansSerif),
            titleMedium = TextStyle(fontSize = 17.sp, fontFamily = FontFamily.SansSerif)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val c = crypto
            if (c == null) {
                if (needsSetup) {
                    SetupScreen(onCreated = { crypto = it; needsSetup = false })
                } else {
                    LockScreen(onUnlocked = { crypto = it })
                }
            } else {
                MainScreen(crypto = c, onLock = { crypto = null })
            }
        }
    }
}

// ── Первый запуск: создание пароля

@Composable
fun SetupScreen(onCreated: (Crypto) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pass1 by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Создайте пароль", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Все заметки будут зашифрованы ключом, выведенным из этого пароля.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = pass1,
            onValueChange = { pass1 = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass2,
            onValueChange = { pass2 = it },
            label = { Text("Повторите пароль") },
            singleLine = true,
            isError = pass2.isNotEmpty() && pass1 != pass2,
            supportingText = {
                if (pass2.isNotEmpty() && pass1 != pass2) Text("Пароли не совпадают")
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))

        Button(
            onClick = {
                busy = true
                scope.launch {
                    val c = withContext(Dispatchers.Default) { Vault.create(context, pass1) }
                    onCreated(c)
                }
            },
            enabled = !busy && pass1.length >= 4 && pass1 == pass2,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Создание…" else "Создать") }

        Spacer(Modifier.height(16.dp))
        Text(
            "Пароль не сохраняется нигде. Если вы его забудете — заметки восстановить невозможно.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Разблокировка

@Composable
fun LockScreen(onUnlocked: (Crypto) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text("Введите пароль", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it; error = false },
            label = { Text("Пароль") },
            singleLine = true,
            isError = error,
            supportingText = { if (error) Text("Неверный пароль") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                busy = true
                scope.launch {
                    val c = withContext(Dispatchers.Default) { Vault.unlock(context, pass) }
                    if (c == null) { error = true; busy = false } else onUnlocked(c)
                }
            },
            enabled = !busy && pass.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Проверка…" else "Разблокировать") }
    }
}

// ── Главный экран: список заметок + редактор

@Composable
fun MainScreen(crypto: Crypto, onLock: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { NoteRepository(context, crypto) }
    val scope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var editingIsNew by remember { mutableStateOf(true) }

    suspend fun reload() {
        notes = withContext(Dispatchers.IO) { repo.loadAll() }
        loaded = true
    }

    LaunchedEffect(Unit) { reload() }

    val editor = editing
    if (editor != null) {
        EditorScreen(
            note = editor,
            isNew = editingIsNew,
            onSaved = { updated ->
                scope.launch {
                    withContext(Dispatchers.IO) { repo.save(updated) }
                    reload()
                    editing = null
                }
            },
            onDeleted = { id ->
                scope.launch {
                    withContext(Dispatchers.IO) { repo.delete(id) }
                    reload()
                    editing = null
                }
            },
            onDiscard = { editing = null }
        )
    } else {
        ListScreen(
            notes = notes,
            loaded = loaded,
            onNew = { editing = Note(); editingIsNew = true },
            onOpen = { editing = it; editingIsNew = false },
            onLock = onLock
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    notes: List<Note>,
    loaded: Boolean,
    onNew: () -> Unit,
    onOpen: (Note) -> Unit,
    onLock: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("d MMM yyyy · HH:mm", Locale("ru")) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Заметки")
                        Text(
                            "Личное пространство",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, contentDescription = "Заблокировать")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNew,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новая заметка")
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (loaded && notes.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Пока нет заметок",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Создайте первую заметку кнопкой + ниже.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(note) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Text(
                                    note.title.ifBlank { "Без названия" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (note.body.isNotBlank()) {
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        note.body,
                                        maxLines = 2,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(9.dp))
                                Text(
                                    fmt.format(Date(note.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    note: Note,
    isNew: Boolean,
    onSaved: (Note) -> Unit,
    onDeleted: (String) -> Unit,
    onDiscard: () -> Unit
) {
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }

    fun exit(save: Boolean) {
        val blank = isNew && title.isBlank() && body.isBlank()
        if (save && !blank) {
            val changed = title != note.title || body != note.body
            onSaved(
                note.copy(
                    title = title.trim(),
                    body = body,
                    updatedAt = if (changed) System.currentTimeMillis() else note.updatedAt
                )
            )
        } else {
            onDiscard()
        }
    }

    BackHandler { exit(true) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Новая заметка" else "Редактирование") },
                navigationIcon = {
                    IconButton(onClick = { exit(true) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { onDeleted(note.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                    }
                    IconButton(onClick = { exit(true) }) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge
                    .copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (title.isEmpty()) {
                            Text(
                                "Заголовок",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = body,
                onValueChange = { body = it },
                textStyle = MaterialTheme.typography.bodyLarge
                    .copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 24.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize()) {
                        if (body.isEmpty()) {
                            Text(
                                "Текст заметки",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

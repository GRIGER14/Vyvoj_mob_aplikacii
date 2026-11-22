package sk.receipttracker.ui.screens

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import sk.receipttracker.data.model.Expense
import sk.receipttracker.ui.viewmodel.ExpenseViewModel
import sk.receipttracker.util.FileHelper
import sk.receipttracker.util.OcrResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditExpenseScreen(
    viewModel: ExpenseViewModel,
    expenseId: Long? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val allCategories by viewModel.allCategories.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()

    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Expense.DEFAULT_CATEGORIES[0]) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCustomCategoryDialog by remember { mutableStateOf(false) }
    var customCategoryInput by remember { mutableStateOf("") }

    var note by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }

    var categoryExpanded by remember { mutableStateOf(false) }

    // OCR state
    var isProcessingOcr by remember { mutableStateOf(false) }
    var ocrSuggestion by remember { mutableStateOf<OcrResult?>(null) }

    fun triggerOcr(path: String) {
        isProcessingOcr = true
        viewModel.processReceiptOcr(
            context = context,
            photoPath = path,
            onResult = { result ->
                isProcessingOcr = false
                ocrSuggestion = result
                if (result.detectedStore != null || result.detectedAmount != null) {
                    Toast.makeText(context, "Údaje z bločku úspešne rozpoznané!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Text bol naskenovaný, ale nenašla sa suma.", Toast.LENGTH_SHORT).show()
                }
            },
            onError = {
                isProcessingOcr = false
                Toast.makeText(context, "Chyba pri OCR skenovaní bločku", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // If editing existing expense, load its data
    LaunchedEffect(expenseId) {
        if (expenseId != null && expenseId > 0L) {
            viewModel.getExpense(expenseId).collect { existing ->
                if (existing != null) {
                    title = existing.title
                    amountText = String.format(Locale.US, "%.2f", existing.amount)
                    category = existing.category
                    selectedDateMillis = existing.date
                    note = existing.note
                    photoPath = existing.photoPath
                    latitude = existing.latitude
                    longitude = existing.longitude
                }
            }
        }
    }

    // Temporary URI for camera capture
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null) {
            val path = tempCameraFile!!.absolutePath
            photoPath = path
            Toast.makeText(context, "Bloček odfotený, spúšťam OCR...", Toast.LENGTH_SHORT).show()
            triggerOcr(path)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val (uri, file) = FileHelper.createTempImageUri(context)
            tempCameraUri = uri
            tempCameraFile = file
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Povolenie fotoaparátu je potrebné na odfotenie bločku", Toast.LENGTH_SHORT).show()
        }
    }

    // Fallback: pick image from gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val (destUri, destFile) = FileHelper.createTempImageUri(context)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val path = destFile.absolutePath
                photoPath = path
                Toast.makeText(context, "Obrázok vybraný, spúšťam OCR...", Toast.LENGTH_SHORT).show()
                triggerOcr(path)
            } catch (e: Exception) {
                Toast.makeText(context, "Chyba pri načítaní obrázka", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Location Permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isFetchingLocation = true
            viewModel.fetchCurrentLocation(context) { lat, lon ->
                isFetchingLocation = false
                latitude = lat
                longitude = lon
                if (lat != null && lon != null) {
                    Toast.makeText(context, "Poloha úspešne zaznamenaná", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Nepodarilo sa získať GPS polohu (zapnite GPS)", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Pre zaznamenanie polohy je potrebné udeliť oprávnenie", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-fetch location on opening screen if creating a new expense and location is null
    LaunchedEffect(Unit) {
        if (expenseId == null || expenseId == 0L) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (expenseId != null && expenseId > 0) "Upraviť výdavok" else "Nový výdavok") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Späť")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Názov / Obchod
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Názov / Obchod *") },
                placeholder = { Text("Napr. Billa, Shell, Lekáreň") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Suma
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d*(\\.|,)?\\d{0,2}$"))) {
                        amountText = input.replace(',', '.')
                    }
                },
                label = { Text("Suma (€) *") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Kategória (Dropdown s možnosťou vytvoriť vlastnú kategóriu)
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Kategória *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    val baseWithoutIne = allCategories.filter { !it.equals("Iné", ignoreCase = true) }
                    baseWithoutIne.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                category = cat
                                categoryExpanded = false
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "+ Pridať novú kategóriu...",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        onClick = {
                            categoryExpanded = false
                            customCategoryInput = ""
                            showCustomCategoryDialog = true
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Iné") },
                        onClick = {
                            category = "Iné"
                            categoryExpanded = false
                        }
                    )
                }
            }

            // Dátum nákupu (Kalendár)
            val dateFormatted = remember(selectedDateMillis) {
                val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                sdf.format(Date(selectedDateMillis))
            }
            OutlinedTextField(
                value = dateFormatted,
                onValueChange = {},
                readOnly = true,
                label = { Text("Dátum nákupu *") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Vybrať dátum z kalendára",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            )

            // Poznámka
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Poznámka (voliteľné)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            HorizontalDivider()

            // Fotoaparát (Camera) & Fotografia bločku
            Text(
                text = "Fotografia pokladničného bloku",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (!photoPath.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    AsyncImage(
                        model = File(photoPath!!),
                        contentDescription = "Odfotený bloček",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    IconButton(
                        onClick = {
                            photoPath = null
                            ocrSuggestion = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Odstrániť foto", tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }

                // OCR Suggestion Card
                if (isProcessingOcr) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("ML Kit analyzuje bloček a hľadá sumu...", fontSize = 13.sp)
                        }
                    }
                } else if (ocrSuggestion != null && (ocrSuggestion!!.detectedAmount != null || ocrSuggestion!!.detectedStore != null)) {
                    val ocr = ocrSuggestion!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Rozpoznané údaje z bločku (ML Kit OCR)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (ocr.detectedStore != null) {
                                Text("Obchod: ${ocr.detectedStore}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            if (ocr.detectedAmount != null) {
                                Text("Suma: ${String.format(Locale.US, "%.2f €", ocr.detectedAmount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (ocr.detectedStore != null && title.isBlank()) {
                                        title = ocr.detectedStore
                                    }
                                    if (ocr.detectedAmount != null) {
                                        amountText = String.format(Locale.US, "%.2f", ocr.detectedAmount)
                                    }
                                    Toast.makeText(context, "Údaje doplnené do formulára", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Použiť rozpoznané údaje")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (photoPath == null) "Odfotiť bloček" else "Premerať foto")
                }

                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch("image/*")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Z galérie")
                }
            }

            if (!photoPath.isNullOrBlank() && !isProcessingOcr) {
                OutlinedButton(
                    onClick = { triggerOcr(photoPath!!) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Znova naskenovať bloček (OCR)")
                }
            }

            HorizontalDivider()

            // GPS Poloha (Location Services)
            Text(
                text = "GPS Poloha nákupu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (latitude != null && longitude != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Poloha úspešne priradená",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = String.format(Locale.US, "Lat: %.5f | Lon: %.5f", latitude, longitude),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isFetchingLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zisťujem GPS polohu...")
                } else {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (latitude == null) "Zaznamenať aktuálnu polohu" else "Aktualizovať GPS polohu")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Uložiť Button (Save)
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (title.isBlank()) {
                        Toast.makeText(context, "Zadajte názov výdavku alebo obchodu", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (amount == null || amount <= 0.0) {
                        Toast.makeText(context, "Zadajte platnú sumu výdavku", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    viewModel.saveExpense(
                        title = title.trim(),
                        amount = amount,
                        category = category,
                        photoPath = photoPath,
                        latitude = latitude,
                        longitude = longitude,
                        note = note.trim(),
                        date = selectedDateMillis,
                        existingId = expenseId ?: 0L,
                        onSuccess = {
                            Toast.makeText(context, "Výdavok bol úspešne uložený", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (expenseId != null && expenseId > 0) "Uložiť zmeny" else "Pridať výdavok",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Dialóg pre pridanie vlastného druhu nákupu (kategórie)
    if (showCustomCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCustomCategoryDialog = false },
            title = { Text("Nový druh nákupu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zadajte vlastný názov pre nový typ výdavkov:")
                    OutlinedTextField(
                        value = customCategoryInput,
                        onValueChange = { customCategoryInput = it },
                        placeholder = { Text("Napr. Darčeky, Šport, Domáce zvieratá") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customCategoryInput.trim()
                        if (trimmed.isNotBlank()) {
                            category = trimmed
                            showCustomCategoryDialog = false
                            Toast.makeText(context, "Kategória '$trimmed' nastavená", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Zadajte názov kategórie", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Použiť")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomCategoryDialog = false }) {
                    Text("Zrušiť")
                }
            }
        )
    }

    // Krásny kalendár s označením dní nákupov
    if (showDatePicker) {
        sk.receipttracker.ui.components.ExpenseCalendarDialog(
            initialDateMillis = selectedDateMillis,
            expenses = allExpenses,
            onDateSelected = { dateMillis ->
                selectedDateMillis = dateMillis
                showDatePicker = false
            },
            onDismissRequest = { showDatePicker = false }
        )
    }
}

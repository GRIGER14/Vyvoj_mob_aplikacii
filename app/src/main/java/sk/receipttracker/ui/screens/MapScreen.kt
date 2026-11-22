package sk.receipttracker.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.preference.PreferenceManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import sk.receipttracker.data.model.StoreInsight
import sk.receipttracker.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val expensesWithLocation by viewModel.expensesWithLocation.collectAsState()
    val storeInsights by viewModel.storeInsights.collectAsState()

    var selectedStore by remember { mutableStateOf<StoreInsight?>(null) }
    var isClusterMode by remember { mutableStateOf(true) }

    // Initialize osmdroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context,
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        Configuration.getInstance().userAgentValue = context.packageName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa nákupov a analýza") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Späť")
                    }
                },
                actions = {
                    // Toggle clustering mode
                    IconButton(
                        onClick = { isClusterMode = !isClusterMode }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Prepnúť zhlukovanie (klasterizáciu)",
                            tint = if (isClusterMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(14.0)

                        // Center on default (Slovakia center: 48.6690, 19.6990 or first item)
                        val startPoint = if (expensesWithLocation.isNotEmpty()) {
                            GeoPoint(
                                expensesWithLocation.first().latitude ?: 48.6690,
                                expensesWithLocation.first().longitude ?: 19.6990
                            )
                        } else {
                            GeoPoint(48.1486, 17.1077) // Bratislava default
                        }
                        controller.setCenter(startPoint)
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    if (isClusterMode) {
                        // Store Insights cluster mode
                        // Grouping points by store: each distinct store with location has its own cluster marker
                        storeInsights.forEach { store ->
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(store.latitude, store.longitude)
                            marker.title = store.storeName
                            marker.snippet = "${store.count} nákupov | Spolu: ${String.format(Locale.US, "%.2f €", store.totalAmount)}"
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                            // Custom cluster icon with count and price
                            marker.icon = createStoreClusterIcon(context, store.count, store.totalAmount)

                            marker.setOnMarkerClickListener { _, _ ->
                                selectedStore = store
                                true
                            }
                            mapView.overlays.add(marker)
                        }
                    } else {
                        // Standard individual markers for every single receipt
                        expensesWithLocation.forEach { expense ->
                            val lat = expense.latitude
                            val lon = expense.longitude
                            if (lat != null && lon != null) {
                                val marker = Marker(mapView)
                                marker.position = GeoPoint(lat, lon)
                                marker.title = "${expense.title} (${expense.formattedAmount()})"
                                marker.snippet = "Kategória: ${expense.category}\nDátum: ${expense.formattedDate()}"
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                                marker.setOnMarkerClickListener { _, _ ->
                                    val match = storeInsights.find { it.storeName.equals(expense.title.trim(), ignoreCase = true) }
                                    selectedStore = match
                                    marker.showInfoWindow()
                                    true
                                }
                                mapView.overlays.add(marker)
                            }
                        }
                    }

                    mapView.invalidate()
                }
            )

            // Info chip at top-center indicating mode
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isClusterMode) MaterialTheme.colorScheme.primary else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isClusterMode) "Režim: Analýza obchodov (Store Insights)" else "Režim: Všetky jednotlivé bločky",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Bottom Insight Sheet when a store is tapped
            AnimatedVisibility(
                visible = selectedStore != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                selectedStore?.let { store ->
                    StoreInsightCard(
                        store = store,
                        onClose = { selectedStore = null }
                    )
                }
            }

            if (expensesWithLocation.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Zatiaľ nemáte žiadne výdavky so zaznamenanou GPS polohou. Pri pridávaní výdavku kliknite na 'Zaznamenať aktuálnu polohu'.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StoreInsightCard(
    store: StoreInsight,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Store icon + Name + Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = store.storeName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Analýza výdavkov v tejto lokalite",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Zavrieť")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // Two-column summary stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Celkovo minuté",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.2f €", store.totalAmount),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Počet návštev / nákupov",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${store.count} nákupov",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Info chip for latest purchase
            val sdf = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kategória: ${store.latestCategory}  •  Naposledy: ${sdf.format(Date(store.latestDate))}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Generates an attractive custom round cluster marker showing purchase count and total amount.
 */
private fun createStoreClusterIcon(context: android.content.Context, count: Int, totalAmount: Double): BitmapDrawable {
    val sizePx = 130
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Outer glow circle
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33006D44 // Translucent emerald
        style = Paint.Style.FILL
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, glowPaint)

    // Main solid circle
    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF006D44.toInt() // Solid theme emerald
        style = Paint.Style.FILL
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 6f, circlePaint)

    // White border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f - 6f, borderPaint)

    // Text: count (e.g. "14x")
    val countText = "${count}x"
    val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 28f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val countBounds = Rect()
    countPaint.getTextBounds(countText, 0, countText.length, countBounds)
    canvas.drawText(countText, sizePx / 2f, sizePx / 2f - 4f, countPaint)

    // Text: total amount (e.g. "320 €")
    val amountText = String.format(Locale.US, "%.0f €", totalAmount)
    val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0F2F1.toInt()
        textSize = 21f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(amountText, sizePx / 2f, sizePx / 2f + 24f, amountPaint)

    return BitmapDrawable(context.resources, bitmap)
}


package sk.receipttracker.ui.screens

import android.preference.PreferenceManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import sk.receipttracker.ui.viewmodel.ExpenseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val expensesWithLocation by viewModel.expensesWithLocation.collectAsState()

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
                title = { Text("Mapa nákupov a výdavkov") },
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

                    expensesWithLocation.forEach { expense ->
                        val lat = expense.latitude
                        val lon = expense.longitude
                        if (lat != null && lon != null) {
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(lat, lon)
                            marker.title = "${expense.title} (${expense.formattedAmount()})"
                            marker.snippet = "Kategória: ${expense.category}\nDátum: ${expense.formattedDate()}"
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            mapView.overlays.add(marker)
                        }
                    }

                    if (expensesWithLocation.isNotEmpty()) {
                        val first = expensesWithLocation.first()
                        if (first.latitude != null && first.longitude != null) {
                            mapView.controller.animateTo(GeoPoint(first.latitude, first.longitude))
                        }
                    }
                    mapView.invalidate()
                }
            )

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

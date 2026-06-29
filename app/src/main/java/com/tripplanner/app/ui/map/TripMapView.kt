package com.tripplanner.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.tripplanner.app.BuildConfig
import com.tripplanner.app.model.TripObjectAttribute
import com.tripplanner.app.model.TripObjectDraft
import com.tripplanner.app.model.TripObjectType
import com.tripplanner.app.util.isOnline

@Composable
fun TripMapView(
    tripObjects: List<TripObjectDraft>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapItems = remember(tripObjects) { tripObjects.toMapItems() }
    val canRenderLiveMap = BuildConfig.MAPS_API_KEY.isNotBlank() && context.isOnline()

    if (!canRenderLiveMap) {
        OfflineMapFallback(
            mapItems = mapItems,
            reason = if (BuildConfig.MAPS_API_KEY.isBlank()) {
                "Add MAPS_API_KEY to local.properties to render the live map."
            } else {
                "Offline mode is using locally saved map details."
            },
            modifier = modifier
        )
        return
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            onResume()
        }
    }
    val routeColor = MaterialTheme.colorScheme.primary.toArgb()

    DisposableEffect(mapView) {
        onDispose {
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        factory = { mapView },
        update = { view ->
            view.getMapAsync { googleMap ->
                googleMap.clear()
                mapItems.markers.forEach { marker ->
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(marker.position)
                            .title(marker.title)
                            .snippet(marker.subtitle)
                    )
                }
                mapItems.routes.forEach { route ->
                    googleMap.addPolyline(
                        PolylineOptions()
                            .add(route.departure, route.arrival)
                            .color(routeColor)
                            .width(8f)
                    )
                }

                val cameraTarget = mapItems.markers.firstOrNull()?.position ?: DEFAULT_CAMERA_TARGET
                googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        cameraTarget,
                        if (mapItems.markers.isEmpty()) 4f else 11f
                    )
                )
            }
        }
    )
}

@Composable
private fun OfflineMapFallback(
    mapItems: TripMapItems,
    reason: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Map details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (mapItems.markers.isNotEmpty()) {
                mapItems.markers.take(5).forEach { marker ->
                    Text(
                        text = "${marker.title}: ${marker.position.latitude}, ${marker.position.longitude}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class MapMarker(
    val position: LatLng,
    val title: String,
    val subtitle: String
)

private data class MapRoute(
    val departure: LatLng,
    val arrival: LatLng
)

private data class TripMapItems(
    val markers: List<MapMarker>,
    val routes: List<MapRoute>
)

private fun List<TripObjectDraft>.toMapItems(): TripMapItems {
    val markers = mutableListOf<MapMarker>()
    val routes = mutableListOf<MapRoute>()

    forEach { tripObject ->
        if (tripObject.type == TripObjectType.TRANSPORTATION) {
            val departure = tripObject.latLngFrom(
                latitudeAttribute = TripObjectAttribute.DEPARTURE_LATITUDE,
                longitudeAttribute = TripObjectAttribute.DEPARTURE_LONGITUDE
            )
            val arrival = tripObject.latLngFrom(
                latitudeAttribute = TripObjectAttribute.ARRIVAL_LATITUDE,
                longitudeAttribute = TripObjectAttribute.ARRIVAL_LONGITUDE
            )

            departure?.let {
                markers.add(
                    MapMarker(
                        position = it,
                        title = "${tripObject.priorityOrder}. ${tripObject.name}",
                        subtitle = "Departure"
                    )
                )
            }
            arrival?.let {
                markers.add(
                    MapMarker(
                        position = it,
                        title = "${tripObject.priorityOrder}. ${tripObject.name}",
                        subtitle = "Arrival"
                    )
                )
            }
            if (departure != null && arrival != null) {
                routes.add(MapRoute(departure = departure, arrival = arrival))
            }
        } else {
            tripObject.latLngFrom(
                latitudeAttribute = TripObjectAttribute.LATITUDE,
                longitudeAttribute = TripObjectAttribute.LONGITUDE
            )?.let {
                markers.add(
                    MapMarker(
                        position = it,
                        title = "${tripObject.priorityOrder}. ${tripObject.name}",
                        subtitle = tripObject.type.displayName
                    )
                )
            }
        }
    }

    return TripMapItems(
        markers = markers,
        routes = routes
    )
}

private fun TripObjectDraft.latLngFrom(
    latitudeAttribute: TripObjectAttribute,
    longitudeAttribute: TripObjectAttribute
): LatLng? {
    val latitude = attributes[latitudeAttribute]?.toDoubleOrNull() ?: return null
    val longitude = attributes[longitudeAttribute]?.toDoubleOrNull() ?: return null
    return if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
        LatLng(latitude, longitude)
    } else {
        null
    }
}

private val DEFAULT_CAMERA_TARGET = LatLng(31.7683, 35.2137)

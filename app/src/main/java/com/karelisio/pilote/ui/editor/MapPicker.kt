package com.karelisio.pilote.ui.editor

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.sin

private const val OSM_STYLE = """
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [{ "id": "osm", "type": "raster", "source": "osm" }]
}
"""

/** MapLibre map with OpenStreetMap tiles: tap to place the zone, radius drawn live. */
@Composable
fun MapPicker(
    latitude: Double,
    longitude: Double,
    radiusMeters: Float,
    onPick: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val onPickState by rememberUpdatedState(onPick)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            val hasPoint = latitude != 0.0 || longitude != 0.0
            m.cameraPosition = CameraPosition.Builder()
                .target(if (hasPoint) LatLng(latitude, longitude) else LatLng(46.6, 2.4))
                .zoom(if (hasPoint) 15.0 else 4.5)
                .build()
            m.setStyle(Style.Builder().fromJson(OSM_STYLE)) { style ->
                if (dark) {
                    style.getLayerAs<RasterLayer>("osm")?.setProperties(
                        PropertyFactory.rasterBrightnessMax(0.75f),
                        PropertyFactory.rasterSaturation(-0.35f),
                        PropertyFactory.rasterContrast(0.1f),
                    )
                }
                style.addSource(GeoJsonSource("zone"))
                style.addSource(GeoJsonSource("center"))
                style.addLayer(FillLayer("zone-fill", "zone").withProperties(PropertyFactory.fillColor(accent), PropertyFactory.fillOpacity(0.22f)))
                style.addLayer(LineLayer("zone-line", "zone").withProperties(PropertyFactory.lineColor(accent), PropertyFactory.lineWidth(2.5f)))
                style.addLayer(
                    CircleLayer("center-dot", "center").withProperties(
                        PropertyFactory.circleColor(accent), PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE), PropertyFactory.circleStrokeWidth(2.5f),
                    )
                )
                styleReady = true
            }
            m.addOnMapClickListener { point ->
                onPickState(point.latitude, point.longitude)
                true
            }
        }
    }

    // Redraw the zone whenever the point or radius changes.
    LaunchedEffect(map, styleReady, latitude, longitude, radiusMeters) {
        val style = map?.style ?: return@LaunchedEffect
        if (!styleReady || (latitude == 0.0 && longitude == 0.0)) return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>("zone")?.setGeoJson(Feature.fromGeometry(circlePolygon(latitude, longitude, radiusMeters)))
        style.getSourceAs<GeoJsonSource>("center")?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(longitude, latitude)))
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        SmallFloatingActionButton(
            onClick = {
                scope.launch {
                    currentLocation(context)?.let { (lat, lng) ->
                        onPickState(lat, lng)
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16.0), 700)
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        ) { Icon(Icons.Rounded.MyLocation, "Ma position") }
    }
}

@SuppressLint("MissingPermission")
private suspend fun currentLocation(context: android.content.Context): Pair<Double, Double>? {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val client = LocationServices.getFusedLocationProviderClient(context)
    val loc = runCatching { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await() }.getOrNull()
        ?: runCatching { client.lastLocation.await() }.getOrNull()
    return loc?.let { it.latitude to it.longitude }
}

/** Circle approximated by a 64-point polygon (meters → degrees). */
private fun circlePolygon(lat: Double, lng: Double, radius: Float): Polygon {
    val points = (0..64).map { i ->
        val angle = Math.toRadians(i * 360.0 / 64)
        val dLat = radius / 111_320.0 * cos(angle)
        val dLng = radius / (111_320.0 * cos(Math.toRadians(lat))) * sin(angle)
        Point.fromLngLat(lng + dLng, lat + dLat)
    }
    return Polygon.fromLngLats(listOf(points))
}

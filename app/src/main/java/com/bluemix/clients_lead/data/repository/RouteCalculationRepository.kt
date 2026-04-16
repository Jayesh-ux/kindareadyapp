// data/repository/RouteCalculationRepository.kt
package com.bluemix.clients_lead.data.repository

import android.location.Location
import com.bluemix.clients_lead.domain.model.LocationPlace
import com.bluemix.clients_lead.domain.model.TransportMode
import com.google.android.gms.maps.model.LatLng
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.client.plugins.timeout
import timber.log.Timber

data class RouteResult(
    val distanceKm: Double,
    val durationMinutes: Int,
    val routePolyline: List<LatLng>? = null
)

class RouteCalculationRepository(
    private val httpClient: HttpClient,
    private val googleMapsApiKey: String? = null
) {
    companion object {
        private const val OSRM_BASE = "https://router.project-osrm.org"
        private const val OVERPASS_API = "https://overpass-api.de/api/interpreter"
        private const val GOOGLE_DIRECTIONS_BASE = "https://maps.googleapis.com/maps/api/directions/json"
    }

    // Transport mode to Google Directions mode
    private fun TransportMode.toGoogleMode(): String = when (this) {
        TransportMode.CAR, TransportMode.TAXI, TransportMode.RICKSHAW, TransportMode.AUTO -> "driving"
        TransportMode.BUS -> "driving"
        TransportMode.TRAIN, TransportMode.METRO -> "transit"
        TransportMode.BIKE -> "bicycling"
        TransportMode.WALK, TransportMode.FLIGHT -> "walking"
        TransportMode.OTHER -> "driving"
    }

    // Primary: Google Directions API (more reliable)
    suspend fun calculateRouteWithGoogleDirections(
        start: LocationPlace,
        end: LocationPlace,
        transportMode: TransportMode
    ): RouteResult? {
        val apiKey = googleMapsApiKey ?: return null
        
        return try {
            val mode = transportMode.toGoogleMode()
            val origin = "${start.latitude},${start.longitude}"
            val destination = "${end.latitude},${end.longitude}"
            val url = "$GOOGLE_DIRECTIONS_BASE?origin=$origin&destination=$destination&mode=$mode&key=$apiKey"
            
            Timber.d("🗺️ Fetching Google Directions: $mode")
            
            val response: GoogleDirectionsResponse = httpClient.get(url).body()
            
            if (response.status != "OK" || response.routes.isEmpty()) {
                Timber.w("⚠️ Google Directions failed: ${response.status}")
                return null
            }
            
            val route = response.routes.first()
            val leg = route.legs.first()
            val distanceKm = leg.distance.value / 1000.0
            val durationMinutes = leg.duration.value / 60
            
            // Decode polyline
            val polyline = decodePolyline(route.overview_polyline.points)
            
            Timber.d("✅ Google route: ${distanceKm.round(2)} km, $durationMinutes min")
            
            RouteResult(
                distanceKm = distanceKm.round(2),
                durationMinutes = durationMinutes,
                routePolyline = polyline
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ Google Directions API failed")
            null
        }
    }

    // Decode Google encoded polyline - simplified
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        
        while (index < encoded.length) {
            var shift = 0
            var result: Int = 0
            do {
                val c = encoded[index++].code - 63
                result = result or ((c and 0x1f) shl shift)
                shift += 5
            } while (c >= 0x20)
            lat += if ((result and 1) != 0) -(result shr 1) else result shr 1
            
            shift = 0
            result = 0
            do {
                val c = encoded[index++].code - 63
                result = result or ((c and 0x1f) shl shift)
                shift += 5
            } while (c >= 0x20)
            lng += if ((result and 1) != 0) -(result shr 1) else result shr 1
            
            poly.add(LatLng(lat / 1_000_000.0, lng / 1_000_000.0))
        }
        
        return poly
    }

    suspend fun calculateRouteDistance(
        start: LocationPlace,
        end: LocationPlace,
        transportMode: TransportMode
    ): Double {
        return when (transportMode) {
            TransportMode.CAR,
            TransportMode.TAXI,
            TransportMode.RICKSHAW,
            TransportMode.AUTO -> calculateRoadDistance(start, end, "car")

            TransportMode.BIKE -> calculateRoadDistance(start, end, "bike")

            TransportMode.BUS -> calculateRoadDistance(start, end, "car")

            TransportMode.TRAIN,
            TransportMode.METRO -> calculateRailDistance(start, end)

            TransportMode.FLIGHT -> calculateFlightDistance(start, end)
            
            TransportMode.WALK -> calculateRoadDistance(start, end, "foot")
            TransportMode.OTHER -> calculateRoadDistance(start, end, "car")
        }
    }

    suspend fun calculateRouteWithGeometry(
        start: LocationPlace,
        end: LocationPlace,
        transportMode: TransportMode
    ): RouteResult {
        Timber.d("🗺️ calculateRouteWithGeometry called with mode: $transportMode")

        // Try Google Directions first if API key is available
        calculateRouteWithGoogleDirections(start, end, transportMode)?.let { googleResult ->
            return googleResult
        }

        // Fallback to OSRM
        return when (transportMode) {
            TransportMode.CAR,
            TransportMode.TAXI,
            TransportMode.RICKSHAW,
            TransportMode.AUTO -> {
                Timber.d("🚗 Using car routing profile")
                calculateRoadRouteWithGeometry(start, end, "car")
            }

            TransportMode.BIKE -> {
                Timber.d("🚴 Using bike routing profile")
                calculateRoadRouteWithGeometry(start, end, "bike")
            }

            TransportMode.BUS -> {
                Timber.d("🚌 Using bus routing (car profile + time adjustment)")
                calculateRoadRouteWithGeometry(start, end, "car").let { result ->
                    result.copy(durationMinutes = (result.durationMinutes * 1.4).toInt())
                }
            }

            TransportMode.TRAIN,
            TransportMode.METRO -> {
                Timber.d("🚂 Using TRAIN/METRO routing with fallback calculation")
                calculateTrainRouteWithFallback(start, end)
            }

            TransportMode.FLIGHT -> {
                Timber.d("✈️ Using flight routing")
                calculateFlightRouteWithGeometry(start, end)
            }
            
            TransportMode.WALK -> {
                Timber.d("🚶 Using walking routing")
                calculateRoadRouteWithGeometry(start, end, "foot")
            }
            
            TransportMode.OTHER -> {
                Timber.d("🚗 Using other (car profile)")
                calculateRoadRouteWithGeometry(start, end, "car")
            }
        }
    }

    // ✅ SIMPLIFIED: No Google API check, just station validation
    suspend fun validateTransportMode(
        start: LocationPlace,
        end: LocationPlace,
        transportMode: TransportMode
    ): Pair<Boolean, String?> {
        return when (transportMode) {
            TransportMode.TRAIN, TransportMode.METRO -> {
                val distance = calculateStraightLineDistance(start, end)

                // Check distance first (quick check)
                when {
                    distance < 1.0 -> {
                        return false to "Train not recommended for very short distances under 1 KM. Try Bus, Rickshaw, or Bike instead."
                    }
                    distance > 500.0 -> {
                        return false to "Distance too long for train calculation (${String.format("%.0f", distance)} KM). Consider Flight mode."
                    }
                }

                // ✅ ONLY check if stations exist - that's sufficient
                try {
                    Timber.d("🚂 Checking for railway stations near locations...")
                    val (startStation, endStation) = checkNearbyTrainStationsDetailed(start, end)

                    if (startStation == null) {
                        return false to "No railway station found within 10 KM of your start location. Try Bus or Rickshaw instead."
                    }

                    if (endStation == null) {
                        return false to "No railway station found within 10 KM of destination. Try Bus or Rickshaw instead."
                    }

                    Timber.d("✅ Found stations: $startStation → $endStation")
                    Timber.d("✅ Train mode validated based on station proximity")

                } catch (e: Exception) {
                    Timber.e(e, "❌ Station check failed")
                    return false to "Unable to verify railway stations. Please check your connection or try another mode."
                }

                true to null
            }

            TransportMode.FLIGHT -> {
                val distance = calculateStraightLineDistance(start, end)
                if (distance < 200.0) {
                    false to "Flight mode is only available for distances over 200 KM (current: ${String.format("%.0f", distance)} KM)."
                } else {
                    true to null
                }
            }

            else -> true to null
        }
    }

    private suspend fun checkNearbyTrainStationsDetailed(
        start: LocationPlace,
        end: LocationPlace
    ): Pair<String?, String?> {
        val startStation = findNearestStation(start.latitude, start.longitude, "start")
        val endStation = findNearestStation(end.latitude, end.longitude, "end")

        Timber.d("🚉 Station check: start='$startStation', end='$endStation'")
        return Pair(startStation, endStation)
    }

    private suspend fun findNearestStation(
        lat: Double,
        lon: Double,
        locationName: String
    ): String? {
        val maxRetries = 2
        var lastException: Exception? = null

        // ✅ Retry up to 2 times if it fails
        repeat(maxRetries + 1) { attempt ->
            try {
                val radius = 10000  // 10 KM radius for station search

                val query = """
                    [out:json][timeout:25];
                    (
                      node["railway"~"station|halt"](around:$radius,$lat,$lon);
                      way["railway"~"station|halt"](around:$radius,$lat,$lon);
                      rel["railway"~"station|halt"](around:$radius,$lat,$lon);
                    );
                    out center;
                """.trimIndent()

                if (attempt > 0) {
                    Timber.d("🔄 Retry attempt $attempt for $locationName...")
                    delay(1000L * attempt) // Backoff: 1s, 2s
                } else {
                    Timber.d("🔍 Searching stations near $locationName...")
                }

                val response: OverpassResponse = httpClient.get(OVERPASS_API) {
                    parameter("data", query)
                    timeout {
                        requestTimeoutMillis = 30_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 30_000
                    }
                }.body()

                if (response.elements.isEmpty()) {
                    Timber.w("⚠️ No railway stations found within 10km of $locationName")
                    return null
                }

                val nearestStation = response.elements.first()
                val stationName = nearestStation.tags?.get("name") ?: "Unknown Station"

                Timber.d("✅ Found station near $locationName: $stationName")
                return stationName

            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    Timber.w("⚠️ Attempt ${attempt + 1} failed for $locationName, retrying...")
                } else {
                    Timber.e(e, "❌ All attempts failed for $locationName")
                }
            }
        }

        return null
    }

    private suspend fun calculateRoadDistance(
        start: LocationPlace,
        end: LocationPlace,
        profile: String
    ): Double {
        return try {
            val url = "$OSRM_BASE/route/v1/$profile/${start.longitude},${start.latitude};${end.longitude},${end.latitude}"

            val response: OsrmResponse = httpClient.get(url) {
                parameter("overview", "false")
                parameter("steps", "false")
            }.body()

            val distanceMeters = response.routes.firstOrNull()?.distance ?: 0.0
            (distanceMeters / 1000.0).round(2)

        } catch (e: Exception) {
            Timber.e(e, "❌ OSRM routing failed, using straight-line")
            calculateStraightLineDistance(start, end)
        }
    }

    private suspend fun calculateRoadRouteWithGeometry(
        start: LocationPlace,
        end: LocationPlace,
        profile: String
    ): RouteResult {
        return try {
            val url = "$OSRM_BASE/route/v1/$profile/${start.longitude},${start.latitude};${end.longitude},${end.latitude}"

            Timber.d("🗺️ Fetching $profile route from OSRM...")

            val response: OsrmResponse = httpClient.get(url) {
                parameter("overview", "full")
                parameter("geometries", "geojson")
                parameter("steps", "false")
            }.body()

            val route = response.routes.firstOrNull()
            val distanceKm = (route?.distance ?: 0.0) / 1000.0
            val durationMinutes = ((route?.duration ?: 0.0) / 60.0).toInt()

            val polyline = route?.geometry?.coordinates?.map { coord ->
                LatLng(coord[1], coord[0])
            } ?: emptyList()

            val adjustedDuration = when (profile) {
                "car" -> (durationMinutes * 1.3).toInt()
                "bike" -> (durationMinutes * 1.5).toInt()
                else -> durationMinutes
            }

            Timber.d("✅ $profile route: ${distanceKm.round(2)} km, $adjustedDuration min")

            RouteResult(
                distanceKm = distanceKm.round(2),
                durationMinutes = adjustedDuration,
                routePolyline = polyline
            )

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to get $profile route from OSRM")
            val straightLine = calculateStraightLineDistance(start, end)
            val estimatedTime = estimateTravelTime(straightLine, profile)

            RouteResult(
                distanceKm = straightLine,
                durationMinutes = estimatedTime,
                routePolyline = listOf(
                    LatLng(start.latitude, start.longitude),
                    LatLng(end.latitude, end.longitude)
                )
            )
        }
    }

    private fun estimateTravelTime(distanceKm: Double, profile: String): Int {
        val avgSpeed = when (profile) {
            "car" -> 35.0
            "bike" -> 25.0
            else -> 30.0
        }
        return ((distanceKm / avgSpeed) * 60).toInt()
    }

    private suspend fun calculateRailDistance(
        start: LocationPlace,
        end: LocationPlace
    ): Double {
        val straightLine = calculateStraightLineDistance(start, end)
        return (straightLine * 1.2).round(2)
    }

    // ✅ NEW: Simplified train route calculation without Google API
    private suspend fun calculateTrainRouteWithFallback(
        start: LocationPlace,
        end: LocationPlace
    ): RouteResult {
        val straightLine = calculateStraightLineDistance(start, end)

        // ✅ For trains, use 1.3x multiplier (tracks aren't perfectly straight)
        val trainDistance = (straightLine * 1.3).round(2)

        // ✅ Estimate based on average train speed (50-60 km/h for local/suburban)
        val avgSpeedKmh = 55.0
        val estimatedMinutes = ((trainDistance / avgSpeedKmh) * 60).toInt()

        // ✅ Add buffer for stops (2 min per 10km for local trains)
        val stopTimeMinutes = (trainDistance / 10.0 * 2).toInt()
        val totalMinutes = estimatedMinutes + stopTimeMinutes

        Timber.d("🚂 Train calculation: $trainDistance km, ~$totalMinutes min (includes stops)")

        return RouteResult(
            distanceKm = trainDistance,
            durationMinutes = totalMinutes,
            routePolyline = listOf(
                LatLng(start.latitude, start.longitude),
                LatLng(end.latitude, end.longitude)
            )
        )
    }

    private suspend fun calculateFlightRouteWithGeometry(
        start: LocationPlace,
        end: LocationPlace
    ): RouteResult {
        val straightLine = calculateStraightLineDistance(start, end)
        val flightHours = straightLine / 800.0
        val totalMinutes = ((flightHours + 2.0) * 60).toInt()

        Timber.d("✈️ Flight calculation: $straightLine km, ~$totalMinutes min total")

        return RouteResult(
            distanceKm = straightLine,
            durationMinutes = totalMinutes,
            routePolyline = listOf(
                LatLng(start.latitude, start.longitude),
                LatLng(end.latitude, end.longitude)
            )
        )
    }

    private suspend fun calculateFlightDistance(
        start: LocationPlace,
        end: LocationPlace
    ): Double {
        return calculateStraightLineDistance(start, end)
    }

    private fun calculateStraightLineDistance(
        start: LocationPlace,
        end: LocationPlace
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude,
            results
        )
        return (results[0] / 1000.0).round(2)
    }

    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}

// OSRM Response DTOs
@Serializable
data class OsrmResponse(
    val routes: List<OsrmRoute>,
    val code: String
)

@Serializable
data class OsrmRoute(
    val distance: Double,
    val duration: Double,
    val geometry: OsrmGeometry? = null
)

@Serializable
data class OsrmGeometry(
    val coordinates: List<List<Double>>,
    val type: String = "LineString"
)

// Overpass API DTOs
@Serializable
data class OverpassResponse(
    val elements: List<OverpassElement>
)

@Serializable
data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val tags: Map<String, String>? = null
)

// Google Directions API DTOs
@Serializable
data class GoogleDirectionsResponse(
    val routes: List<GoogleRoute> = emptyList(),
    val status: String = ""
)

@Serializable
data class GoogleRoute(
    val legs: List<GoogleLeg> = emptyList(),
    val overview_polyline: GooglePolyline = GooglePolyline("")
)

@Serializable
data class GoogleLeg(
    val distance: GoogleDistance = GoogleDistance(0.0),
    val duration: GoogleDuration = GoogleDuration(0)
)

@Serializable
data class GoogleDistance(
    val value: Double = 0.0
)

@Serializable
data class GoogleDuration(
    val value: Int = 0
)

@Serializable
data class GooglePolyline(
    val points: String = ""
)
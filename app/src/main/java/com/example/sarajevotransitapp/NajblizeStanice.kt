package com.example.sarajevotransitapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sarajevotransitapp.database.entities.stops
import com.example.sarajevotransitapp.database.functions.allstations
import com.example.sarajevotransitapp.functions.GeocodingService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NajblizeStanice : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap

    private lateinit var stationList: List<Station>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(R.layout.closest_locations)


        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Retrieve the station list from the stoplist data class function
        stationList = retrieveStationList()
    }

    private fun retrieveStationList(): List<Station> {
        val stopsList: List<stops> = allstations.stoplist()

        // Create a list of Station objects from the stops list
        return stopsList.map { stop ->
            Station(stop.stop_name, LatLng(stop.stop_lat, stop.stop_lon))
        }
    }

    private fun findClosestStation(userLocation: LatLng): Station? {
        var closestStation: Station? = null
        var closestDistance = Float.MAX_VALUE

        for (station in stationList) {
            val stationLocation = station.location
            val distance = FloatArray(1)
            Location.distanceBetween(
                userLocation.latitude, userLocation.longitude,
                stationLocation.latitude, stationLocation.longitude, distance
            )
            if (distance[0] < closestDistance) {
                closestDistance = distance[0]
                closestStation = station
            }
        }

        return closestStation
    }

    private fun zoomToClosestStation(station: Station) {
        val closestStationLocation = station.location
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(closestStationLocation, 15f))
    }

    override fun onMapReady(gMap: GoogleMap) {
        googleMap = gMap

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        googleMap.isMyLocationEnabled = true

        for (station in stationList) {
            googleMap.addMarker(MarkerOptions().position(station.location).title(station.name))
        }

        val streetName = intent.getStringExtra("streetName")

        // launch a new coroutine in background and continue
        GlobalScope.launch {
            val latLng = GeocodingService(this@NajblizeStanice).getLatLongFromAddress(streetName)

            // communicate with main thread
            withContext(Dispatchers.Main) {
                if (latLng != null) {
                    val userLocation = latLng
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f))

                    // Find the closest station based on the user's location
                    val closestStation = findClosestStation(userLocation)
                    closestStation?.let {
                        zoomToClosestStation(it)
                    }
                }
            }
        }
    }

    data class Station(val name: String, val location: LatLng)

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 123
    }

}


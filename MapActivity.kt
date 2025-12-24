package com.example.akillikampus.view

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.asLiveData
import com.example.akillikampus.R
import com.example.akillikampus.databinding.ActivityMapBinding
import com.example.akillikampus.model.Report
import com.example.akillikampus.viewmodel.MapState
import com.example.akillikampus.viewmodel.MapViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.util.HashMap

class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var binding: ActivityMapBinding
    private lateinit var map: GoogleMap
    private val viewModel: MapViewModel by viewModels()
    private var selectedReportId: String? = null
    private val markerMap = HashMap<Marker, Report>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnDetail.setOnClickListener {
            selectedReportId?.let { id ->
                val intent = Intent(this, ReportDetailActivity::class.java)
                intent.putExtra("REPORT_ID", id)
                startActivity(intent)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        
        with(map.uiSettings) {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = true
            isMapToolbarEnabled = false
        }

        map.setOnMarkerClickListener(this)
        
        map.setOnMapClickListener {
            binding.infoCard.visibility = View.GONE
            selectedReportId = null
        }

        // Varsayılan konum: Erzurum
        val erzurumLocation = LatLng(39.9055, 41.2658)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(erzurumLocation, 12f))

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.mapState.asLiveData().observe(this) { state ->
            when (state) {
                is MapState.Loading -> {
                    // Loading...
                }
                is MapState.Success -> {
                    addMarkers(state.reports)
                }
                is MapState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun addMarkers(reports: List<Report>) {
        map.clear()
        markerMap.clear()

        // Çakışan konumları takip etmek için sayaç
        val locationCounts = HashMap<String, Int>()

        for (report in reports) {
            if (report.latitude != 0.0 && report.longitude != 0.0) {
                var lat = report.latitude
                var lng = report.longitude
                
                // Konum anahtarı (virgülden sonra 5 hane hassasiyet)
                val key = "${String.format("%.5f", lat)}_${String.format("%.5f", lng)}"
                
                val count = locationCounts.getOrDefault(key, 0)
                if (count > 0) {
                    // Konum çakışıyorsa işaretçiyi hafifçe kaydır (yaklaşık 20-30 metre)
                    // Böylece üst üste binmezler ve hepsi görünür olur
                    val offsetMultiplier = (count + 1) / 2 
                    val offsetDistance = 0.00025 * offsetMultiplier
                    
                    // Her seferinde farklı bir yöne kaydır
                    val direction = count % 4
                    when (direction) {
                        0 -> { lat += offsetDistance; lng += offsetDistance } 
                        1 -> { lat -= offsetDistance; lng -= offsetDistance }
                        2 -> { lat += offsetDistance; lng -= offsetDistance }
                        3 -> { lat -= offsetDistance; lng += offsetDistance }
                    }
                }
                locationCounts[key] = count + 1

                val position = LatLng(lat, lng)
                
                val iconResource = when (report.type) {
                    "Arıza" -> R.drawable.ic_repair
                    "Şikayet" -> R.drawable.ic_complaint
                    "Güvenlik" -> R.drawable.ic_security
                    "Temizlik" -> R.drawable.ic_cleaning
                    else -> R.drawable.ic_default_marker
                }

                val markerIcon = bitmapDescriptorFromVector(iconResource)

                val markerOptions = MarkerOptions()
                    .position(position)
                    .title(report.title)
                
                if (markerIcon != null) {
                    markerOptions.icon(markerIcon)
                }

                val marker = map.addMarker(markerOptions)
                if (marker != null) {
                    markerMap[marker] = report
                }
            }
        }
    }

    private fun bitmapDescriptorFromVector(vectorResId: Int): BitmapDescriptor? {
        return try {
            val vectorDrawable = ContextCompat.getDrawable(this, vectorResId) ?: return null
            vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
            val bitmap = Bitmap.createBitmap(
                vectorDrawable.intrinsicWidth,
                vectorDrawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            vectorDrawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val report = markerMap[marker]
        if (report != null) {
            showReportInfo(report)
        }
        return false 
    }

    private fun showReportInfo(report: Report) {
        binding.tvTitle.text = report.title
        binding.chipStatus.text = report.status
        binding.tvTime.text = DateUtils.getRelativeTimeSpanString(
            report.timestamp.toDate().time,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
        
        selectedReportId = report.id
        binding.infoCard.visibility = View.VISIBLE
    }
}
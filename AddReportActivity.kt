package com.example.akillikampus.view

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.akillikampus.databinding.ActivityAddReportBinding
import com.example.akillikampus.viewmodel.ReportViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth

class AddReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddReportBinding
    private val reportViewModel: ReportViewModel by viewModels()
    private var selectedImageUri: Uri? = null
    
    // Erzurum Koordinatları
    private val ERZURUM_LAT = 39.9055
    private val ERZURUM_LNG = 41.2658

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            binding.ivReportImage.setImageURI(it)
            binding.tvAddPhoto.visibility = View.GONE
        }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            getCurrentLocationAndSubmit()
        } else {
            Toast.makeText(this, "Konum izni gerekli, varsayılan konum (Erzurum) kullanılacak", Toast.LENGTH_SHORT).show()
            // İzin verilmezse Erzurum olarak kaydet
            sendReportToViewModel(ERZURUM_LAT, ERZURUM_LNG)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val types = arrayOf("Genel", "Arıza", "Şikayet", "Güvenlik", "Temizlik", "Öneri", "Kayıp Eşya")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        (binding.tilType.editText as? AutoCompleteTextView)?.setAdapter(adapter)

        binding.ivReportImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        binding.cvImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnSubmit.setOnClickListener {
            submitReport()
        }
    }

    private fun submitReport() {
        val title = binding.etTitle.text.toString()
        val description = binding.etDescription.text.toString()

        if (title.isEmpty() || description.isEmpty()) {
            Toast.makeText(this, "Lütfen başlık ve açıklama girin", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        if (binding.switchLocation.isChecked) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                getCurrentLocationAndSubmit()
            }
        } else {
            // Konum paylaşımı kapalıysa varsayılan olarak Erzurum
            sendReportToViewModel(ERZURUM_LAT, ERZURUM_LNG)
        }
    }

    private fun getCurrentLocationAndSubmit() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            // Emülatör tespiti (Genişletilmiş kontrol)
                            val isEmulator = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                                    || Build.FINGERPRINT.startsWith("generic")
                                    || Build.FINGERPRINT.startsWith("unknown")
                                    || Build.HARDWARE.contains("goldfish")
                                    || Build.HARDWARE.contains("ranchu")
                                    || Build.MODEL.contains("google_sdk")
                                    || Build.MODEL.contains("Emulator")
                                    || Build.MODEL.contains("Android SDK built for x86")
                                    || Build.MANUFACTURER.contains("Genymotion")
                                    || Build.PRODUCT.contains("sdk")
                                    || Build.PRODUCT.contains("google_sdk")
                                    || Build.PRODUCT.contains("emulator")

                            // Konum ABD içindeyse (Kullanıcı Erzurum'da olduğunu belirttiği için bu bir hatadır/emülatördür)
                            // ABD kabaca: Enlem 25-50, Boylam -125 ile -65 arası
                            val isInUS = (location.latitude > 25.0 && location.latitude < 50.0 && 
                                          location.longitude < -65.0 && location.longitude > -125.0)

                            if (isEmulator || isInUS) {
                                Toast.makeText(this, "Emülatör veya ABD konumu algılandı. Rapor Erzurum'a sabitleniyor.", Toast.LENGTH_LONG).show()
                                sendReportToViewModel(ERZURUM_LAT, ERZURUM_LNG)
                            } else {
                                sendReportToViewModel(location.latitude, location.longitude)
                            }
                        } else {
                            Toast.makeText(this, "Konum alınamadı, varsayılan konum (Erzurum) kullanılıyor.", Toast.LENGTH_LONG).show()
                            sendReportToViewModel(ERZURUM_LAT, ERZURUM_LNG)
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Konum hatası, varsayılan konum (Erzurum) kullanılıyor.", Toast.LENGTH_SHORT).show()
                        sendReportToViewModel(ERZURUM_LAT, ERZURUM_LNG)
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.progressBar.visibility = View.GONE
            binding.btnSubmit.isEnabled = true
        }
    }

    private fun sendReportToViewModel(lat: Double, lng: Double) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val title = binding.etTitle.text.toString()
        val description = binding.etDescription.text.toString()
        
        var type = binding.actType.text.toString()
        if (type.isEmpty()) {
            type = "Genel"
        }

        reportViewModel.addReport(
            title,
            description,
            type,
            lat,
            lng,
            selectedImageUri,
            userId
        ) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Rapor başarıyla gönderildi!", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
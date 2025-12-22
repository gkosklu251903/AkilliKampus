package com.example.akillikampus.view

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.akillikampus.R
import com.example.akillikampus.adapter.ReportAdapter
import com.example.akillikampus.databinding.ActivityHomeBinding
import com.example.akillikampus.viewmodel.HomeUiState
import com.example.akillikampus.viewmodel.HomeViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.random.Random

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var reportAdapter: ReportAdapter
    private val auth = FirebaseAuth.getInstance()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // İzin verildi
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkNotificationPermission()
        subscribeToTopics()
        listenForAnnouncements()
        listenForReportChanges() // Bildirimleri ve durum güncellemelerini dinle

        checkAdminUser()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun subscribeToTopics() {
        FirebaseMessaging.getInstance().subscribeToTopic("announcements")
    }

    // Duyuruları Dinle
    private fun listenForAnnouncements() {
        val db = FirebaseFirestore.getInstance()
        db.collection("announcements")
            .whereGreaterThan("timestamp", Timestamp.now())
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                snapshots?.documentChanges?.forEach { dc ->
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val title = dc.document.getString("title") ?: "Duyuru"
                        val message = dc.document.getString("message") ?: "Yeni bir duyuru var"
                        showNotification(title, message)
                    }
                }
            }
    }

    // Raporları ve Durum Değişikliklerini Dinle
    private fun listenForReportChanges() {
        val db = FirebaseFirestore.getInstance()
        val prefs = getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)
        val startTime = Timestamp.now()
        // Rapor ID -> Status eşleşmesini tutarak değişiklikleri takip et
        val statusMap = mutableMapOf<String, String>()

        db.collection("reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50) // Son 50 raporu dinle (performans için limitli)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                snapshots?.documentChanges?.forEach { dc ->
                    val doc = dc.document
                    val id = doc.id
                    val type = doc.getString("type") ?: "Genel"
                    val title = doc.getString("title") ?: "Bildirim"
                    val currentStatus = doc.getString("status") ?: "Açık"
                    val timestamp = doc.getTimestamp("timestamp")

                    when (dc.type) {
                        DocumentChange.Type.ADDED -> {
                            statusMap[id] = currentStatus
                            // Uygulama açıldıktan sonra gelen YENİ raporlar
                            if (timestamp != null && timestamp > startTime) {
                                val userId = doc.getString("userId") ?: ""
                                if (userId != auth.currentUser?.uid) {
                                    val isEnabled = prefs.getBoolean("pref_notify_$type", true)
                                    if (isEnabled) {
                                        showNotification("Yeni $type: $title", doc.getString("description") ?: "")
                                    }
                                }
                            }
                        }
                        DocumentChange.Type.MODIFIED -> {
                            val oldStatus = statusMap[id]
                            // Eğer durum değiştiyse ve eski durumu biliyorsak
                            if (oldStatus != null && oldStatus != currentStatus) {
                                statusMap[id] = currentStatus // Güncelle

                                // Kullanıcı bu tür bildirimleri almak istiyor mu?
                                val isEnabled = prefs.getBoolean("pref_notify_$type", true)
                                if (isEnabled) {
                                    showNotification("Durum Güncellendi: $currentStatus", "$title başlıklı bildirimin durumu güncellendi.")
                                }
                            } else {
                                statusMap[id] = currentStatus
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            statusMap.remove(id)
                        }
                    }
                }
            }
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "akilli_kampus_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Kampüs Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(Random.nextInt(), builder.build())
    }

    private fun checkAdminUser() {
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.email == "admin@akillikampus.com") {
            binding.btnAdminPanel.visibility = View.VISIBLE
        } else {
            binding.btnAdminPanel.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadReports()
    }

    private fun setupRecyclerView() {
        reportAdapter = ReportAdapter { report ->
            val intent = Intent(this, ReportDetailActivity::class.java)
            intent.putExtra("REPORT_ID", report.id)
            intent.putExtra("REPORT_TITLE", report.title)
            intent.putExtra("REPORT_DESC", report.description)
            intent.putExtra("REPORT_IMAGE", report.imageUrl)
            intent.putExtra("REPORT_STATUS", report.status)
            startActivity(intent)
        }

        binding.rvReports.apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = reportAdapter
        }
    }

    private fun setupListeners() {
        binding.fabAddReport.setOnClickListener {
            startActivity(Intent(this, AddReportActivity::class.java))
        }

        binding.btnOpenMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
        
        binding.btnAdminPanel.setOnClickListener {
            startActivity(Intent(this, AdminPanelActivity::class.java))
        }

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.etSearch.clearFocus()
                true
            } else {
                false
            }
        }

        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
             if (checkedIds.isNotEmpty()) {
                val filter = when (checkedIds[0]) {
                    binding.chipOpen.id -> "Açık"
                    binding.chipFollowing.id -> "Takip Ettiklerim"
                    binding.chipMyReports.id -> "Bildirimlerim"
                    binding.chipFault.id -> "Arıza"
                    binding.chipComplaint.id -> "Şikayet"
                    binding.chipSecurity.id -> "Güvenlik"
                    binding.chipCleaning.id -> "Temizlik"
                    binding.chipSuggestion.id -> "Öneri"
                    else -> "Tümü"
                }
                viewModel.setFilter(filter)
             } else {
                 viewModel.setFilter("Tümü")
             }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is HomeUiState.Loading -> {
                        binding.progressBarHome.visibility = View.VISIBLE
                        binding.tvEmptyState.visibility = View.GONE
                    }
                    is HomeUiState.Success -> {
                        binding.progressBarHome.visibility = View.GONE
                        reportAdapter.submitList(state.reports)

                        if (state.reports.isEmpty()) {
                            binding.tvEmptyState.visibility = View.VISIBLE
                            binding.tvEmptyState.text = if (binding.etSearch.text.isNullOrEmpty()) 
                                "Henüz bildirim yok.\nİlk bildirimi sen oluştur!" 
                            else 
                                "Aradığınız kriterlere uygun bildirim bulunamadı."
                        } else {
                            binding.tvEmptyState.visibility = View.GONE
                        }
                    }
                    is HomeUiState.Error -> {
                        binding.progressBarHome.visibility = View.GONE
                        Toast.makeText(this@HomeActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}

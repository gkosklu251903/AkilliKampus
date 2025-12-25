package com.example.akillikampus.view

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import coil.load
import com.example.akillikampus.R
import com.example.akillikampus.databinding.ActivityReportDetailBinding
import com.example.akillikampus.model.Report
import com.example.akillikampus.model.User
import com.example.akillikampus.viewmodel.AuthViewModel
import com.example.akillikampus.viewmodel.ReportViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

class ReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportDetailBinding
    private val reportViewModel: ReportViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()

    private var currentReportId: String? = null
    private var isFollowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar Geri Butonu Ayarı
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        currentReportId = intent.getStringExtra("REPORT_ID")
        if (currentReportId == null) {
            Toast.makeText(this, "Bildirim bulunamadı.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupListeners()
        observeData()
    }

    private fun setupListeners() {
        binding.btnFollow.setOnClickListener {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

            if (currentUserId == null) {
                Toast.makeText(this, "Takip etmek için giriş yapmalısınız.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isFollowing = !isFollowing
            updateFollowButton()

            // Firestore güncellemesi
            reportViewModel.toggleFollow(currentReportId!!, currentUserId, isFollowing)

            // Bildirim Aboneliği Yönetimi
            val topic = "report_${currentReportId}"
            if (isFollowing) {
                FirebaseMessaging.getInstance().subscribeToTopic(topic)
            } else {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            }
        }

        // Yeni Durum Değiştirme Yapısı
        binding.btnChangeStatus.setOnClickListener {
            val options = arrayOf("İnceleniyor", "Çözüldü", "Açık")
            AlertDialog.Builder(this)
                .setTitle("Durum Seçin")
                .setItems(options) { _, which ->
                    updateStatus(options[which])
                }
                .show()
        }

        binding.btnDeleteReport.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Bildirimi Sil")
            .setMessage("Bu bildirimi kalıcı olarak silmek istediğinize emin misiniz? Bu işlem geri alınamaz.")
            .setPositiveButton("Evet, Sil") { _, _ ->
                currentReportId?.let { id ->
                    reportViewModel.deleteReport(id,
                        onSuccess = {
                            Toast.makeText(this, "Bildirim başarıyla silindi", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onError = { error ->
                            Toast.makeText(this, "Hata: $error", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
            .setNegativeButton("İptal") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun observeData() {
        // Hata mesajlarını dinle
        reportViewModel.errorMessage.asLiveData().observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
        
        // Raporları gözlemliyoruz
        reportViewModel.reports.asLiveData().observe(this) { reports ->
            val report = reports.find { it.id == currentReportId }
            val currentUser = authViewModel.currentUser.value
            if (report != null) {
                updateReportDetails(report, currentUser)
            }
        }

        authViewModel.currentUser.asLiveData().observe(this) { currentUser ->
            updateAdminPanel(currentUser)
            // Kullanıcı değiştiğinde rapor detaylarını tekrar güncelle (takip durumu için)
            val reports = reportViewModel.reports.value
            val report = reports.find { it.id == currentReportId }
            if (report != null) {
                updateReportDetails(report, currentUser)
            }
        }
    }

    private fun updateReportDetails(report: Report, currentUser: User?) {
        binding.tvTitle.text = report.title
        binding.chipStatus.text = report.status
        binding.chipType.text = report.type
        binding.tvDescription.text = report.description
        
        // Tarih Bilgisi
        binding.tvDate.text = DateUtils.getRelativeTimeSpanString(
            report.timestamp.toDate().time,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )

        // Kullanıcı Adı
        binding.tvUser.text = getString(R.string.reported_by, report.userName)

        if (report.imageUrl.isNotEmpty()) {
            binding.ivReportImage.visibility = View.VISIBLE
            binding.ivReportImage.load(report.imageUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_dialog_alert)
            }
        } else {
            binding.ivReportImage.visibility = View.GONE
        }

        val userId = currentUser?.id ?: FirebaseAuth.getInstance().currentUser?.uid
        
        isFollowing = userId?.let { report.followers.contains(it) } ?: false
        updateFollowButton()
    }

    private fun updateAdminPanel(currentUser: User?) {
        val authUser = FirebaseAuth.getInstance().currentUser
        val isAdminEmail = authUser?.email.equals("admin@akillikampus.com", ignoreCase = true)
        val isRoleAdmin = currentUser?.role == "admin"

        if (isRoleAdmin || isAdminEmail) {
            binding.layoutAdminActions.visibility = View.VISIBLE
        } else {
            binding.layoutAdminActions.visibility = View.GONE
        }
    }

    private fun updateFollowButton() {
        if (isFollowing) {
            binding.btnFollow.text = getString(R.string.unfollow)
        } else {
            binding.btnFollow.text = getString(R.string.follow)
        }
    }

    private fun updateStatus(status: String) {
        currentReportId?.let { id ->
            reportViewModel.updateStatus(id, status)
            Toast.makeText(this, "Durum güncellendi: $status", Toast.LENGTH_SHORT).show()
        }
    }
}

package com.example.akillikampus.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.akillikampus.adapter.ReportAdapter
import com.example.akillikampus.databinding.ActivityProfileBinding
import com.example.akillikampus.viewmodel.ProfileUiState
import com.example.akillikampus.viewmodel.ProfileViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var reportAdapter: ReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "" // Siyah "Profilim" yazısını kaldırmak için başlığı boş yapıyoruz
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        reportAdapter = ReportAdapter { report ->
            val intent = Intent(this, ReportDetailActivity::class.java)
            intent.putExtra("REPORT_ID", report.id)
            startActivity(intent)
        }

        binding.rvFollowing.apply {
            layoutManager = LinearLayoutManager(this@ProfileActivity)
            adapter = reportAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupListeners() {
        binding.btnLogout.setOnClickListener {
            viewModel.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.layoutNotificationsHeader.setOnClickListener {
            val isVisible = binding.layoutNotificationTypes.visibility == View.VISIBLE
            if (isVisible) {
                binding.layoutNotificationTypes.visibility = View.GONE
                binding.ivExpandArrow.animate().rotation(0f).setDuration(200).start()
            } else {
                binding.layoutNotificationTypes.visibility = View.VISIBLE
                binding.ivExpandArrow.animate().rotation(180f).setDuration(200).start()
            }
        }
        
        setupNotificationSwitches()
    }

    private fun setupNotificationSwitches() {
        val prefs = getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)
        val switches = mapOf(
            binding.switchGeneral to "Genel",
            binding.switchLostAndFound to "Kayıp Eşya",
            binding.switchFault to "Arıza",
            binding.switchComplaint to "Şikayet",
            binding.switchSecurity to "Güvenlik",
            binding.switchCleaning to "Temizlik",
            binding.switchSuggestion to "Öneri"
        )

        switches.forEach { (switchBtn, type) ->
            switchBtn.isChecked = prefs.getBoolean("pref_notify_$type", true)
            switchBtn.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("pref_notify_$type", isChecked).apply()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is ProfileUiState.Loading -> {
                        binding.tvUserName.text = "Yükleniyor..."
                    }
                    is ProfileUiState.Success -> {
                        binding.tvUserName.text = state.name
                        binding.tvUserEmail.text = state.email
                        binding.tvUserRole.text = state.role
                        
                        reportAdapter.submitList(state.followedReports)

                        if (state.followedReports.isEmpty()) {
                            binding.tvNoFollowing.visibility = View.VISIBLE
                            binding.rvFollowing.visibility = View.GONE
                        } else {
                            binding.tvNoFollowing.visibility = View.GONE
                            binding.rvFollowing.visibility = View.VISIBLE
                        }
                    }
                    is ProfileUiState.Error -> {
                        Toast.makeText(this@ProfileActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
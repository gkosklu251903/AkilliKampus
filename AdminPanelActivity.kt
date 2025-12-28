package com.example.akillikampus.view

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.akillikampus.adapter.ReportAdapter
import com.example.akillikampus.databinding.ActivityAdminPanelBinding
import com.example.akillikampus.viewmodel.AdminUiState
import com.example.akillikampus.viewmodel.AdminViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AdminPanelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminPanelBinding
    private val viewModel: AdminViewModel by viewModels()
    private lateinit var reportAdapter: ReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
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

        binding.rvAdminReports.apply {
            layoutManager = LinearLayoutManager(this@AdminPanelActivity)
            adapter = reportAdapter
        }

        // Swipe to delete functionality (Kaydırarak Sil)
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            
            // Görsel Geri Bildirim: Kaydırırken Arkada Kırmızı Renk ve Çöp Kutusu İkonu Çıksın
            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val background = ColorDrawable(Color.RED)
                // Android'in standart çöp kutusu ikonu
                val deleteIcon = ContextCompat.getDrawable(this@AdminPanelActivity, android.R.drawable.ic_menu_delete)
                val iconMargin = (itemView.height - (deleteIcon?.intrinsicHeight ?: 0)) / 2

                if (dX > 0) { // Sağa Kaydırma
                    background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    deleteIcon?.setBounds(
                        itemView.left + iconMargin,
                        itemView.top + iconMargin,
                        itemView.left + iconMargin + (deleteIcon.intrinsicWidth ?: 0),
                        itemView.bottom - iconMargin
                    )
                } else if (dX < 0) { // Sola Kaydırma
                    background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    deleteIcon?.setBounds(
                        itemView.right - iconMargin - (deleteIcon.intrinsicWidth ?: 0),
                        itemView.top + iconMargin,
                        itemView.right - iconMargin,
                        itemView.bottom - iconMargin
                    )
                } else {
                    background.setBounds(0, 0, 0, 0)
                }

                background.draw(c)
                if (dX != 0f) deleteIcon?.draw(c)

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val report = reportAdapter.currentList[position]

                AlertDialog.Builder(this@AdminPanelActivity)
                    .setTitle("Bildirimi Sil")
                    .setMessage("'${report.title}' başlıklı raporu kalıcı olarak silmek istediğinize emin misiniz?")
                    .setPositiveButton("Evet, Sil") { _, _ ->
                        viewModel.deleteReport(report.id,
                            onSuccess = {
                                Toast.makeText(this@AdminPanelActivity, "Bildirim başarıyla silindi", Toast.LENGTH_SHORT).show()
                            },
                            onError = { error ->
                                Toast.makeText(this@AdminPanelActivity, "Hata: $error", Toast.LENGTH_LONG).show()
                                reportAdapter.notifyItemChanged(position) // Hata durumunda listeye geri yükle
                            }
                        )
                    }
                    .setNegativeButton("İptal") { dialog, _ ->
                        dialog.dismiss()
                        reportAdapter.notifyItemChanged(position) // İptal edilirse listeye geri yükle
                    }
                    .setCancelable(false)
                    .show()
            }
        }

        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.rvAdminReports)
    }

    private fun setupListeners() {
        binding.btnEmergency.setOnClickListener {
            showAnnouncementDialog()
        }
    }

    private fun showAnnouncementDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Acil Durum Duyurusu")

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val etTitle = EditText(this)
        etTitle.hint = "Başlık (Örn: Su Kesintisi)"
        layout.addView(etTitle)

        val etMessage = EditText(this)
        etMessage.hint = "Mesajınız..."
        layout.addView(etMessage)

        builder.setView(layout)

        builder.setPositiveButton("Yayınla") { _, _ ->
            val title = etTitle.text.toString().trim()
            val message = etMessage.text.toString().trim()

            if (title.isNotEmpty() && message.isNotEmpty()) {
                viewModel.sendAnnouncement(title, message,
                    onSuccess = {
                        Toast.makeText(this, "Duyuru yayınlandı", Toast.LENGTH_SHORT).show()
                    },
                    onError = {
                        Toast.makeText(this, "Hata: $it", Toast.LENGTH_LONG).show()
                    }
                )
            } else {
                Toast.makeText(this, "Başlık ve mesaj boş olamaz", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("İptal") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is AdminUiState.Loading -> {
                        // Progress eklenebilir
                    }
                    is AdminUiState.Success -> {
                        reportAdapter.submitList(state.reports)
                    }
                    is AdminUiState.Error -> {
                        Toast.makeText(this@AdminPanelActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
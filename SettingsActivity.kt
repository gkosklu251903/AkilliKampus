package com.example.akillikampus.view

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.akillikampus.databinding.ActivitySettingsBinding
import com.google.firebase.messaging.FirebaseMessaging

/**
 * SettingsActivity: Kullanıcının bildirim tercihlerini (kategori bazlı) yönetmesini sağlayan ekrandır.
 * Bu sınıf, activity_settings.xml arayüzündeki Switch bileşenleri ile Firebase Cloud Messaging (FCM)
 * aboneliklerini senkronize eder.
 */
class SettingsActivity : AppCompatActivity() {

    // activity_settings.xml içerisindeki Switch'lere ve butonlara erişim sağlayan binding nesnesi
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // XML layout dosyasını aktiviteye bağlar
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Yerel hafızadaki (SharedPreferences) kayıtlı tercihleri yükle ve Switch'leri güncelle
        loadPreferences()
        // Kullanıcı Switch'leri açıp kapattığında tetiklenecek olayları tanımla
        setupListeners()
    }

    /**
     * XML üzerindeki etkileşimli bileşenlerin (Switch ve Geri Butonu) dinleyicilerini ayarlar.
     */
    private fun setupListeners() {
        // btnBack: Ayarlar sayfasından çıkıp bir önceki ekrana dönmeyi sağlar
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Aşağıdaki Switch dinleyicileri, kullanıcı bir kategoriyi açtığında veya kapattığında
        // ilgili Firebase konusuna (topic) abone olur veya abonelikten çıkar.

        // switchFault: "Arıza" bildirimleri için
        binding.switchFault.setOnCheckedChangeListener { _, isChecked ->
            updateSubscription("Arıza", isChecked)
        }
        // switchComplaint: "Şikayet" bildirimleri için
        binding.switchComplaint.setOnCheckedChangeListener { _, isChecked ->
            updateSubscription("Şikayet", isChecked)
        }
        // switchSecurity: "Güvenlik" bildirimleri için
        binding.switchSecurity.setOnCheckedChangeListener { _, isChecked ->
            updateSubscription("Güvenlik", isChecked)
        }
        // switchCleaning: "Temizlik" bildirimleri için
        binding.switchCleaning.setOnCheckedChangeListener { _, isChecked ->
            updateSubscription("Temizlik", isChecked)
        }
        // switchSuggestion: "Öneri" bildirimleri için
        binding.switchSuggestion.setOnCheckedChangeListener { _, isChecked ->
            updateSubscription("Öneri", isChecked)
        }
        // switchLostFound: "Kayıp Eşya" bildirimleri için
        binding.switchLostFound.setOnCheckedChangeListener { _, isChecked ->
            updateSubscription("Kayıp Eşya", isChecked)
        }
    }

    /**
     * Uygulama açıldığında veya Ayarlar ekranına girildiğinde, daha önce kaydedilmiş
     * bildirim tercihlerini "NotificationPrefs" dosyasından okur ve Switch'lerin
     * konumunu (Açık/Kapalı) ayarlar.
     */
    private fun loadPreferences() {
        val sharedPref = getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)

        // getBoolean ikinci parametresi 'true' olduğu için varsayılan olarak tüm bildirimler açıktır.
        binding.switchFault.isChecked = sharedPref.getBoolean("Arıza", true)
        binding.switchComplaint.isChecked = sharedPref.getBoolean("Şikayet", true)
        binding.switchSecurity.isChecked = sharedPref.getBoolean("Güvenlik", true)
        binding.switchCleaning.isChecked = sharedPref.getBoolean("Temizlik", true)
        binding.switchSuggestion.isChecked = sharedPref.getBoolean("Öneri", true)
        binding.switchLostFound.isChecked = sharedPref.getBoolean("Kayıp Eşya", true)
    }

    /**
     * Firebase Messaging (FCM) aboneliklerini günceller ve tercihi yerel hafızaya kaydeder.
     * @param topicName Kategorinin adı (Örn: "Arıza")
     * @param isSubscribed Switch'in durumu (true ise abone ol, false ise abonelikten çık)
     */
    private fun updateSubscription(topicName: String, isSubscribed: Boolean) {
        // FCM konu isimlerinde Türkçe karakter ve boşluk kabul etmediği için normalize edilir.
        val topic = normalizeTopic(topicName)

        if (isSubscribed) {
            // Kullanıcı Switch'i açtıysa ilgili konuya abone yap
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Toast.makeText(this, "Abonelik hatası", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            // Kullanıcı Switch'i kapattıysa ilgili konudan aboneliği kaldır
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Toast.makeText(this, "Abonelikten çıkma hatası", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Kullanıcının yaptığı bu tercihi (Açık/Kapalı) SharedPreferences'a kaydet
        val sharedPref = getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean(topicName, isSubscribed)
            apply()
        }
    }

    /**
     * Firebase topic (konu) kuralları gereği metindeki Türkçe karakterleri
     * İngilizce karşılıklarıyla değiştirir ve boşlukları siler.
     * Örn: "Kayıp Eşya" -> "KayipEsya"
     */
    private fun normalizeTopic(topic: String): String {
        return topic.replace(" ", "")
            .replace("ı", "i")
            .replace("İ", "I")
            .replace("ğ", "g")
            .replace("Ğ", "G")
            .replace("ü", "u")
            .replace("Ü", "U")
            .replace("ş", "s")
            .replace("Ş", "S")
            .replace("ö", "o")
            .replace("Ö", "O")
            .replace("ç", "c")
            .replace("Ç", "C")
    }
}

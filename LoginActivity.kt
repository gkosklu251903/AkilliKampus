package com.example.akillikampus.view

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.TransformationMethod
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.asLiveData
import com.example.akillikampus.databinding.ActivityLoginBinding
import com.example.akillikampus.viewmodel.AuthState
import com.example.akillikampus.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Uygulama her açıldığında temiz bir oturum başlaması için
        FirebaseAuth.getInstance().signOut()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Şifre alanında karakterlerin '•' olarak görünmesi için
        binding.etPassword.transformationMethod = NoEchoTransformationMethod()

        setupEmailAutocomplete()
        setupListeners()
        observeViewModel()
        setupErrorResetting()
    }

    private fun setupEmailAutocomplete() {
        val sharedPref = getPreferences(MODE_PRIVATE)
        val savedEmails = sharedPref.getStringSet("saved_emails", null)?.toMutableSet() ?: mutableSetOf<String>()

        // Test amaçlı sabit e-postalar
        val devEmails = listOf(
            "admin@akillikampus.com",
            "aemin@gmail.com",
            "goktug.kosklu@gmail.com"
        )
        savedEmails.addAll(devEmails)

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, savedEmails.toList())
        binding.etEmail.setAdapter(adapter)

        binding.etEmail.setOnClickListener { binding.etEmail.showDropDown() }
        binding.etEmail.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.etEmail.showDropDown() }

        val lastEmail = sharedPref.getString("last_email", "")
        if (!lastEmail.isNullOrEmpty()) {
            binding.etEmail.setText(lastEmail)
            binding.etEmail.setSelection(lastEmail.length)
            binding.etEmail.dismissDropDown()
        }
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            binding.tilEmail.error = null
            binding.tilPassword.error = null

            if (email.isNotEmpty() && password.isNotEmpty()) {
                authViewModel.login(email, password)
            } else {
                if (email.isEmpty()) binding.tilEmail.error = "Lütfen e-posta girin."
                if (password.isEmpty()) binding.tilPassword.error = "Lütfen şifre girin."
            }
        }

        binding.tvGoToRegister.setOnClickListener {
            // Unresolved reference hatasını önlemek için tam paket yolu kullanıyoruz
            val intent = Intent(this, com.example.akillikampus.view.RegisterActivity::class.java)
            startActivity(intent)
        }

        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isNotEmpty()) {
                authViewModel.resetPassword(email)
            } else {
                binding.tilEmail.error = "Lütfen e-posta adresinizi girin."
            }
        }
    }

    private fun observeViewModel() {
        authViewModel.authState.asLiveData().observe(this) { state ->
            when (state) {
                is AuthState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is AuthState.Success -> {
                    saveEmail(binding.etEmail.text.toString())
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    // Unresolved reference hatasını önlemek için tam paket yolu kullanıyoruz
                    val intent = Intent(this, com.example.akillikampus.view.HomeActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                is AuthState.PasswordResetEmailSent -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(this, "Şifre sıfırlama e-postası gönderildi.", Toast.LENGTH_LONG).show()
                }
                is AuthState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    val message = if (state.message.contains("INVALID_LOGIN_CREDENTIALS", true)) {
                        "E-posta veya şifre hatalı."
                    } else {
                        "Bir hata oluştu: ${state.message}"
                    }
                    binding.tilPassword.error = message
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                }
            }
        }
    }
    
    private fun setupErrorResetting() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.tilEmail.error = null
                binding.tilPassword.error = null
            }
        }
        binding.etEmail.addTextChangedListener(watcher)
        binding.etPassword.addTextChangedListener(watcher)
    }

    private fun saveEmail(email: String) {
        val sharedPref = getPreferences(MODE_PRIVATE)
        val currentSet = sharedPref.getStringSet("saved_emails", null)?.toMutableSet() ?: mutableSetOf<String>()
        currentSet.add(email)
        sharedPref.edit {
            putString("last_email", email)
            putStringSet("saved_emails", currentSet)
        }
    }
}

class NoEchoTransformationMethod : TransformationMethod {
    override fun getTransformation(source: CharSequence, view: View): CharSequence = PasswordCharSequence(source)
    override fun onFocusChanged(v: View?, s: CharSequence?, f: Boolean, d: Int, r: android.graphics.Rect?) {}

    private class PasswordCharSequence(private val source: CharSequence) : CharSequence {
        override val length: Int get() = source.length
        override fun get(index: Int): Char = '•'
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = 
            PasswordCharSequence(source.subSequence(startIndex, endIndex))
    }
}

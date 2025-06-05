package com.example.ens492frontend

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.example.ens492frontend.models.LoginRequest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var instantMonitoringButton: AppCompatButton
    private lateinit var signupPromptText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login2)

        // Initialize UI components
        emailEditText = findViewById(R.id.emailText3)
        passwordEditText = findViewById(R.id.passwordText1)
        loginButton = findViewById(R.id.signInButton3)
        instantMonitoringButton = findViewById(R.id.instantMonitoringButton2)
        signupPromptText = findViewById(R.id.signupPromptText)

        loginButton.setOnClickListener {
            performLogin()
        }


        instantMonitoringButton.setOnClickListener {
            val intent = Intent(this, EcgActivity::class.java)
            startActivity(intent)
        }

        // Set click listener for signup prompt text
        signupPromptText.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performLogin() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            return
        }
        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            return
        }

        val loginRequest = LoginRequest(email, password)

        lifecycleScope.launch {
            try {
                val response = UserApi.login(loginRequest)

                if (response.success == true) {
                    // Save userId in SharedPreferences
                    val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        response.userId?.let { putLong("userId", it) }
                        apply()
                    }

                    // SET ACTIVE ECG USER RIGHT AFTER LOGIN
                    response.userId?.let { userId ->
                        setActiveEcgUser(userId)
                    }

                    Toast.makeText(
                        this@LoginActivity,
                        "Login successful: ${response.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    Log.d("LoginActivity", "UserId to save: ${response.userId}")

                    // Navigate to main activity
                    val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "Login failed: ${response.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Log.e("LoginActivity", "Login failed", e)
                Toast.makeText(
                    this@LoginActivity,
                    "Login failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Simple function to set active ECG user on backend
    private suspend fun setActiveEcgUser(userId: Long) {
        try {
            UserApi.setActiveEcgUser(userId)
            Log.d("LoginActivity", "Set active ECG user: $userId")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Failed to set active ECG user", e)
        }
    }

    fun signInClicked(view: View) {
        performLogin()
    }
}
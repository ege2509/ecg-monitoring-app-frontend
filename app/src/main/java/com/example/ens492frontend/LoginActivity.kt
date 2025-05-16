package com.example.ens492frontend

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login2)

        // Initialize UI components
        emailEditText = findViewById(R.id.emailText3)
        passwordEditText = findViewById(R.id.passwordText1)
        loginButton = findViewById(R.id.signInButton3)
        instantMonitoringButton = findViewById(R.id.instantMonitoringButton2)

        // Set click listener for login button
        loginButton.setOnClickListener {
            performLogin()
        }

        // Set the OnClickListener to start a new activity
        instantMonitoringButton.setOnClickListener {
            // Create an Intent to start the new Activity
            val intent = Intent(this, EcgActivity::class.java)
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

                    Toast.makeText(
                        this@LoginActivity,
                        "Login successful: ${response.message}",
                        Toast.LENGTH_SHORT
                    ).show()

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


    // This is the method referenced in your XML for signup button
    fun signInClicked(view: View) {
        // This is currently being used for login button
        performLogin()
    }

}
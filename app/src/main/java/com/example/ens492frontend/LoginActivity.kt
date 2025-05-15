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
import com.yourapp.api.models.LoginRequest
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

        // Validate input fields
        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            return
        }

        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            return
        }

        // Create login request
        val loginRequest = LoginRequest(email, password)

        // Make API call using Ktor client
        lifecycleScope.launch {
            try {
                val response = UserApi.login(loginRequest)

                // Handle successful response
                Toast.makeText(
                    this@LoginActivity,
                    "Login successful: ${response.message}",
                    Toast.LENGTH_SHORT
                ).show()

                // Save user session or token if needed
                // If your API returns a token, you would use it like:
                // response.token?.let { token ->
                //     SessionManager.saveAuthSession(this@LoginActivity, token, userId)
                // }

                // Navigate to main activity
                val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                // Handle error
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
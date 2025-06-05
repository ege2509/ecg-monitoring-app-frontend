package com.example.ens492frontend

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ens492frontend.models.RegisterRequest
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private lateinit var firstnameEditText: EditText
    private lateinit var surnameEditText: EditText
    private lateinit var ageEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var genderEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var reenterPasswordEditText: EditText
    private lateinit var signUpButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        // Initialize UI components
        firstnameEditText = findViewById(R.id.firstnameText)
        surnameEditText = findViewById(R.id.surnameText)
        ageEditText = findViewById(R.id.ageText)
        emailEditText = findViewById(R.id.emailText)
        genderEditText = findViewById(R.id.genderText)
        passwordEditText = findViewById(R.id.passwordText)
        reenterPasswordEditText = findViewById(R.id.passwordText2)
        signUpButton = findViewById(R.id.signUpButton2)


        // Set click listener for sign up button
        signUpButton.setOnClickListener {
            performSignUp()
        }
    }


    private fun performSignUp() {
        val firstname = firstnameEditText.text.toString().trim()
        val surname = surnameEditText.text.toString().trim()
        val ageStr = ageEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val gender = genderEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val reenterPassword = reenterPasswordEditText.text.toString().trim()

        // Validation
        if (firstname.isEmpty()) {
            firstnameEditText.error = "First name is required"
            return
        }
        if (surname.isEmpty()) {
            surnameEditText.error = "Surname is required"
            return
        }
        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            return
        }
        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            return
        }
        if (reenterPassword.isEmpty()) {
            reenterPasswordEditText.error = "Please re-enter your password"
            return
        }

        // Check if passwords match
        if (password != reenterPassword) {
            reenterPasswordEditText.error = "Passwords do not match"
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        // Parse age (optional since it's nullable in RegisterRequest)
        val age: Int? = if (ageStr.isNotEmpty()) {
            try {
                val ageValue = ageStr.toInt()
                if (ageValue <= 0 || ageValue > 150) {
                    ageEditText.error = "Please enter a valid age"
                    return
                }
                ageValue
            } catch (e: NumberFormatException) {
                ageEditText.error = "Please enter a valid age"
                return
            }
        } else {
            null // Age is optional
        }

        // Validate email format
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Please enter a valid email address"
            return
        }

        // Combine firstname and surname for name field
        val fullName = "$firstname $surname"

        // Create registration request (age and gender are optional)
        val registerRequest = RegisterRequest(
            name = fullName,
            email = email,
            password = password,
            age = age,
            gender = if (gender.isNotEmpty()) gender else null
        )

        // Perform registration
        lifecycleScope.launch {
            try {
                val response = UserApi.register(registerRequest)

                if (response.success == true) {
                    Toast.makeText(
                        this@SignUpActivity,
                        "Registration successful: ${response.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate back to login activity
                    val intent = Intent(this@SignUpActivity, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(
                        this@SignUpActivity,
                        "Registration failed: ${response.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Log.e("SignUpActivity", "Registration failed", e)
                Toast.makeText(
                    this@SignUpActivity,
                    "Registration failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // This method is referenced in your XML onClick attribute
    fun signUpClicked(view: View) {
        performSignUp()
    }
}
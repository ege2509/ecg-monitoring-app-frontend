package com.example.ens492frontend

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ens492frontend.databinding.ActivityProfileBinding
import com.example.ens492frontend.models.User
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private var currentUser: User? = null
    private var userId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get userId from the correct SharedPreferences
        val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        userId = sharedPref.getLong("userId", -1L)  // Update the class property, not a local variable

        Log.d("ProfileActivity", "UserId from prefs: $userId")

        if (userId == -1L) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
        loadUserProfile()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            onBackPressed()
        }


        binding.saveButton.setOnClickListener {
            saveUserProfile()
        }
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val user = UserApi.getUserProfile(userId)
                currentUser = user
                populateUserData(user)
            } catch (e: Exception) {
                showError("Failed to load profile: ${e.message}")
                Log.e("ProfileActivity", "Failed to load profile", e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun populateUserData(user: User) {
        binding.apply {
            profileName.text = user.name


            NameInput.setText(user.name)
            ageInput.setText(user.age?.toString() ?: "")
            genderInput.setText(user.gender ?: "")
            emailInput.setText(user.email)

            // Leave password empty for security
            passwordInput.setText("")

            // Optional: Load profile picture if available
            user.profilePicture?.let { pictureUrl ->
                // Uncomment and add Glide/Coil setup if you want to support profile pics
                // Glide.with(this@ProfileActivity)
                //     .load(pictureUrl)
                //     .placeholder(R.drawable.default_profile_avatar)
                //     .into(profileImage)
            }
        }
    }

    private fun saveUserProfile() {
        if (!validateInputs()) return

        lifecycleScope.launch {
            try {
                showLoading(true)
                val updatedUser = currentUser?.copy(
                    name = binding.NameInput.text.toString().trim(),
                    email = binding.emailInput.text.toString().trim(),
                    gender = binding.genderInput.text.toString().trim().takeIf { it.isNotEmpty() },
                    age = binding.ageInput.text.toString().trim().toInt()
                ) ?: return@launch

                val result = UserApi.updateUserProfile(userId, updatedUser)
                currentUser = result
                showSuccess("Profile updated successfully")
                // Update the header name
                binding.profileName.text = result.name ?: "User"

            } catch (e: Exception) {
                showError("Failed to update profile: ${e.message}")
                Log.e("ProfileActivity", "Failed to update profile", e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun validateInputs(): Boolean {
        binding.apply {
            if (NameInput.text.toString().trim().isEmpty()) {
                NameInput.error = "Name is required"
                return false
            }

            if (emailInput.text.toString().trim().isEmpty()) {
                emailInput.error = "Email is required"
                return false
            }

            if (!isValidEmail(emailInput.text.toString().trim())) {
                emailInput.error = "Please enter a valid email"
                return false
            }

            // Validate age if provided
            val ageText = ageInput.text.toString().trim()
            if (ageText.isNotEmpty()) {
                val age = ageText.toIntOrNull()
                if (age == null || age < 1 || age > 150) {
                    ageInput.error = "Please enter a valid age (1-150)"
                    return false
                }
            }

            return true
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun showLoading(show: Boolean) {
        binding.apply {
            saveButton.isEnabled = !show
            saveButton.text = if (show) "Saving..." else "Save"

            // Disable input fields while loading
            NameInput.isEnabled = !show
            ageInput.isEnabled = !show
            genderInput.isEnabled = !show
            emailInput.isEnabled = !show
            passwordInput.isEnabled = !show
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
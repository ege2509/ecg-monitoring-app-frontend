package com.example.ens492frontend

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.ens492frontend.models.User
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var textViewUsername: TextView
    private lateinit var recordingsContainer: LinearLayout
    private lateinit var layoutMedicalInfo: CardView
    private lateinit var layoutWarning: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        textViewUsername = findViewById(R.id.textViewUsername)
        recordingsContainer = findViewById(R.id.recordingsContainer)
        layoutMedicalInfo = findViewById(R.id.viewMedicalInfoCard)
        layoutWarning =  findViewById(R.id.viewPreviousWarningsCard)

        // Setup back button functionality
        setupBackButton()

        val navProfile = findViewById<LinearLayout>(R.id.navProfile)
        navProfile.setOnClickListener {
            val intent = Intent(this@HomeActivity, ProfileActivity::class.java)
            startActivity(intent)
        }

        // Set click listener for medical info layout
        layoutMedicalInfo.setOnClickListener {
            val intent = Intent(this@HomeActivity, MedicalInfoActivity::class.java)
            startActivity(intent)
        }

        layoutWarning.setOnClickListener {
            val intent = Intent(this@HomeActivity, WarningsListActivity::class.java)
            startActivity(intent)
        }

        val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val userId = sharedPref.getLong("userId", -1L)

        Log.d("HomeActivity", "UserId from prefs: $userId")

        if (userId == -1L) {
            textViewUsername.text = "Unknown User"
            // Don't try to load recordings if userId is invalid
            Log.w("HomeActivity", "Cannot load recordings - invalid userId")
        } else {
            // Load both user profile and recordings only when userId is valid
            lifecycleScope.launch {
                try {
                    // Load user profile
                    val user = UserApi.getUserProfile(userId)
                    textViewUsername.text = user.name

                    // Load recordings using medical info
                    loadUserRecordings(userId)

                } catch (e: Exception) {
                    Log.e("HomeActivity", "Failed to load user data", e)
                    textViewUsername.text = "Failed to load user"
                }
            }
        }

        val connectEcgButton = findViewById<Button>(R.id.connectEcgButton)
        connectEcgButton.setOnClickListener {
            val intent = Intent(this@HomeActivity, EcgActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupBackButton() {
        val backButton = findViewById<ImageView>(R.id.backButton)
        backButton.setOnClickListener {
            val intent = Intent(this, OgMainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private suspend fun loadUserRecordings(userId: Long) {
        try {
            // First get the medical info to get the medicalInfoId
            val medicalInfoService = MedicalInfoService()
            val medicalInfo = medicalInfoService.getMedicalInfo(userId)

            if (medicalInfo?.id == null) {
                Log.w("HomeActivity", "No medical info found for user $userId")
                return
            }

            Log.d("HomeActivity", "Found medical info with ID: ${medicalInfo.id}")

            // Now get recordings using the medicalInfoId
            val recordings = UserApi.getUserRecordingsByMedicalInfoId(medicalInfo.id).reversed() // latest first

            recordings.forEachIndexed { index, recording ->
                val itemView = layoutInflater.inflate(R.layout.item_ecg_reading, recordingsContainer, false)

                val title = itemView.findViewById<TextView>(R.id.recordingTitle)
                val diagnosis = itemView.findViewById<TextView>(R.id.recordingDiagnosis)
                val date = itemView.findViewById<TextView>(R.id.recordingDate)

                title.text = "Recording ${index + 1}"
                diagnosis.text = recording.diagnosis
                date.text = "Date: ${recording.recordingDate}"

                itemView.setOnClickListener {
                    val intent = Intent(this@HomeActivity, RecordingViewActivity::class.java).apply {
                        putExtra("RECORDING_ID", recording.id)
                    }
                    startActivity(intent)
                }

                recordingsContainer.addView(itemView)
            }
        } catch (e: Exception) {
            Log.e("HomeActivity", "Failed to load recordings", e)
        }
    }
}
package com.example.ens492frontend

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.ens492frontend.models.User
class HomeActivity : AppCompatActivity() {

    private lateinit var textViewUsername: TextView
    private lateinit var recordingsContainer: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        textViewUsername = findViewById(R.id.textViewUsername)

        val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val userId = sharedPref.getLong("userId", -1L)

        if (userId == -1L) {
            textViewUsername.text = "Unknown User"
        } else {
            lifecycleScope.launch {
                try {
                    val user = UserApi.getUserProfile(userId)
                    textViewUsername.text = "${user.name}"
                } catch (e: Exception) {
                    textViewUsername.text = "Failed to load user"
                }
            }
        }
        recordingsContainer = findViewById(R.id.recordingsContainer)

        lifecycleScope.launch {
            try {
                val recordings = UserApi.getUserRecordings(userId).reversed() // latest first
                recordings.forEachIndexed { index, recording ->
                    // Inflate layout just once per recording
                    val itemView = layoutInflater.inflate(R.layout.item_ecg_reading, recordingsContainer, false)

                    // Find views
                    val title = itemView.findViewById<TextView>(R.id.recordingTitle)
                    val diagnosis = itemView.findViewById<TextView>(R.id.recordingDiagnosis)
                    val date = itemView.findViewById<TextView>(R.id.recordingDate)

                    // Set data
                    title.text = "Recording ${index + 1}"
                    diagnosis.text = "${recording.diagnosis}"
                    date.text = "Date: ${recording.recordingDate}"

                    // Set click listener
                    itemView.setOnClickListener {
                        val intent = Intent(this@HomeActivity, RecordingViewActivity::class.java).apply {
                            putExtra("RECORDING_ID", recording.id)
                        }
                        startActivity(intent)
                    }

                    // Add view to container (only once)
                    recordingsContainer.addView(itemView)
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "Failed to load recordings", e)
            }
        }

        val connectEcgButton = findViewById<Button>(R.id.connectEcgButton)
        connectEcgButton.setOnClickListener {
            val intent = Intent(this@HomeActivity, EcgActivity::class.java)
            startActivity(intent)
        }
    }

}
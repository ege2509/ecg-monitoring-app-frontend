package com.example.ens492frontend

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.widget.AppCompatButton

class OgMainActivity : AppCompatActivity() {


    private lateinit var getStartedButton: AppCompatButton
    private lateinit var instantMonitoringButton: AppCompatButton
        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_og_main)


        getStartedButton = findViewById(R.id.getStartedButton)
        instantMonitoringButton = findViewById(R.id.instantMonitoringButton)


        // Set the OnClickListener to start a new activity
        getStartedButton.setOnClickListener {
            // Create an Intent to start the new Activity
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }


        // Set the OnClickListener to start a new activity
        instantMonitoringButton.setOnClickListener {
            // Create an Intent to start the new Activity
            val intent = Intent(this, EcgActivity::class.java)
            startActivity(intent)
        }
    }
}
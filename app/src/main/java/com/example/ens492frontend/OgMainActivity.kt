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


        getStartedButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }


        instantMonitoringButton.setOnClickListener {
            val intent = Intent(this, EcgActivity::class.java)
            startActivity(intent)
        }
    }
}
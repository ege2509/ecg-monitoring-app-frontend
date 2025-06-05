package com.example.ens492frontend

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ens492frontend.R
import com.example.ens492frontend.UserApi
import com.example.ens492frontend.WarningsAdapter
import kotlinx.coroutines.launch

class WarningsListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var warningsAdapter: WarningsAdapter
    private var userId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warnings_list)

        // Get userId from intent
        userId = intent.getLongExtra("USER_ID", 0)

        if (userId == 0L) {
            Toast.makeText(this, "Invalid user ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        loadWarnings()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewWarnings)
        warningsAdapter = WarningsAdapter()

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@WarningsListActivity)
            adapter = warningsAdapter
        }
    }

    private fun loadWarnings() {
        lifecycleScope.launch {
            try {
                val warnings = UserApi.getWarningsByUserId(userId)

                if (warnings.isNotEmpty()) {
                    warningsAdapter.updateWarnings(warnings)
                } else {
                    Toast.makeText(
                        this@WarningsListActivity,
                        "No warnings found for this user",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@WarningsListActivity,
                    "Error loading warnings: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
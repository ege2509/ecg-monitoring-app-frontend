package com.example.ens492frontend

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.ens492frontend.models.User
class HomeActivity : AppCompatActivity() {

    private lateinit var textViewUsername: TextView
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
                    textViewUsername.text = "${user.name}!"
                } catch (e: Exception) {
                    textViewUsername.text = "Failed to load user"
                }
            }
        }
    }

}
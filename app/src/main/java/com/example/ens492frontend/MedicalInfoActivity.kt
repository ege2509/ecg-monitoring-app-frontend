package com.example.ens492frontend

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.ens492frontend.models.UserMedicalInfo
import com.example.ens492frontend.MedicalInfoService

class MedicalInfoActivity : AppCompatActivity() {

    // UI Components
    private lateinit var recyclerViewFiles: RecyclerView
    private lateinit var recyclerViewConditions: RecyclerView
    private lateinit var layoutEmptyFiles: LinearLayout
    private lateinit var layoutEmptyConditions: LinearLayout
    private lateinit var layoutEmptyBloodType: LinearLayout
    private lateinit var layoutBloodTypeContent: LinearLayout
    private lateinit var textBloodType: TextView
    private lateinit var btnSetBloodType: Button
    private lateinit var btnEditConditions: Button
    private lateinit var btnViewAllFiles: Button

    private val medicalInfoService = MedicalInfoService()
    private var userId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical_info)

        // Get userId from SharedPreferences
        val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        userId = sharedPref.getLong("userId", 0L)

        setupBackButton()

        if (userId == 0L) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        fetchMedicalInfo()
        setupClickListeners()
    }

    private fun initViews() {
        recyclerViewFiles = findViewById(R.id.recyclerViewFiles)
        recyclerViewConditions = findViewById(R.id.recyclerViewConditions)
        layoutEmptyFiles = findViewById(R.id.layoutEmptyFiles)
        layoutEmptyConditions = findViewById(R.id.layoutEmptyConditions)
        layoutEmptyBloodType = findViewById(R.id.layoutEmptyBloodType)
        layoutBloodTypeContent = findViewById(R.id.layoutBloodTypeContent)
        textBloodType = findViewById(R.id.textBloodType)
        btnSetBloodType = findViewById(R.id.btnSetBloodType)
        btnEditConditions = findViewById(R.id.btnEditConditions)
        btnViewAllFiles = findViewById(R.id.btnViewAllFiles)

        // Set up RecyclerView layout managers
        recyclerViewConditions.layoutManager = LinearLayoutManager(this)
        recyclerViewFiles.layoutManager = LinearLayoutManager(this)
    }

    private fun fetchMedicalInfo() {
        lifecycleScope.launch {
            val medicalInfo = medicalInfoService.getMedicalInfo(userId)
            if (medicalInfo != null) {
                updateUI(medicalInfo)
            } else {
                Toast.makeText(this@MedicalInfoActivity, "Failed to load medical info", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        btnSetBloodType.setOnClickListener {
            showBloodTypeDialog()
        }

        btnEditConditions.setOnClickListener {
            showConditionsDialog()
        }
    }

    private fun setupBackButton() {
        val backButton = findViewById<ImageView>(R.id.back_icon)
        backButton.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }


    private fun showBloodTypeDialog() {
        val bloodTypes = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Select Blood Type")
        builder.setItems(bloodTypes) { _, which ->
            val selectedBloodType = bloodTypes[which]
            updateBloodType(selectedBloodType)
        }
        builder.show()
    }

    private fun showConditionsDialog() {
        lifecycleScope.launch {
            // Get current allergies and medications
            val currentAllergies = medicalInfoService.getAllergies(userId) ?: ""
            val currentMedications = medicalInfoService.getMedications(userId) ?: ""

            runOnUiThread {
                val builder = androidx.appcompat.app.AlertDialog.Builder(this@MedicalInfoActivity)
                val dialogLayout = LinearLayout(this@MedicalInfoActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(50, 50, 50, 50)
                }

                // Allergies section
                val allergiesLabel = TextView(this@MedicalInfoActivity).apply {
                    text = "Allergies:"
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                val allergiesInput = android.widget.EditText(this@MedicalInfoActivity).apply {
                    setText(currentAllergies)
                    hint = "Enter allergies (comma separated)"
                    minLines = 2
                }

                // Medications section
                val medicationsLabel = TextView(this@MedicalInfoActivity).apply {
                    text = "Medications:"
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                val medicationsInput = android.widget.EditText(this@MedicalInfoActivity).apply {
                    setText(currentMedications)
                    hint = "Enter medications (comma separated)"
                    minLines = 2
                }

                dialogLayout.addView(allergiesLabel)
                dialogLayout.addView(allergiesInput)
                dialogLayout.addView(medicationsLabel)
                dialogLayout.addView(medicationsInput)

                builder.setTitle("Edit Medical Conditions")
                    .setView(dialogLayout)
                    .setPositiveButton("Save") { _, _ ->
                        val newAllergies = allergiesInput.text.toString().trim()
                        val newMedications = medicationsInput.text.toString().trim()

                        if (newAllergies != currentAllergies) {
                            updateAllergies(newAllergies)
                        }
                        if (newMedications != currentMedications) {
                            updateMedications(newMedications)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun updateBloodType(bloodType: String) {
        lifecycleScope.launch {
            val updatedInfo = medicalInfoService.setBloodType(userId, bloodType)
            if (updatedInfo != null) {
                updateUI(updatedInfo)
                Toast.makeText(this@MedicalInfoActivity, "Blood type updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MedicalInfoActivity, "Failed to update blood type", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateAllergies(allergies: String) {
        lifecycleScope.launch {
            val result = medicalInfoService.updateAllergies(userId, allergies)
            if (result != null) {
                fetchMedicalInfo() // Refresh the data
                Toast.makeText(this@MedicalInfoActivity, "Allergies updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MedicalInfoActivity, "Failed to update allergies", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMedications(medications: String) {
        lifecycleScope.launch {
            val result = medicalInfoService.updateMedications(userId, medications)
            if (result != null) {
                fetchMedicalInfo() // Refresh the data
                Toast.makeText(this@MedicalInfoActivity, "Medications updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MedicalInfoActivity, "Failed to update medications", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(data: UserMedicalInfo) {
        val hasFiles = false // Replace with actual files check when implemented
        if (!hasFiles) {
            recyclerViewFiles.visibility = View.GONE
            layoutEmptyFiles.visibility = View.VISIBLE
            btnViewAllFiles.visibility = View.GONE
        } else {
            recyclerViewFiles.visibility = View.VISIBLE
            layoutEmptyFiles.visibility = View.GONE
            btnViewAllFiles.visibility = View.VISIBLE
        }

        val conditions = mutableListOf<String>()

        // Add allergies if not empty
        if (!data.allergies.isNullOrEmpty() && data.allergies.trim() != "No allergies") {
            data.allergies.split(",").forEach { allergy ->
                val trimmed = allergy.trim()
                if (trimmed.isNotEmpty()) {
                    conditions.add("Allergy: $trimmed")
                }
            }
        }

        // Add medications if not empty
        if (!data.medications.isNullOrEmpty() && data.medications.trim() != "No medications") {
            data.medications.split(",").forEach { medication ->
                val trimmed = medication.trim()
                if (trimmed.isNotEmpty()) {
                    conditions.add("Medication: $trimmed")
                }
            }
        }

        if (conditions.isEmpty()) {
            recyclerViewConditions.visibility = View.GONE
            layoutEmptyConditions.visibility = View.VISIBLE
            btnEditConditions.text = "Add"
        } else {
            recyclerViewConditions.visibility = View.VISIBLE
            layoutEmptyConditions.visibility = View.GONE
            btnEditConditions.text = "Edit"

            // Set up adapter for conditions
            setupConditionsAdapter(conditions)
        }

        // Update Blood Type
        if (data.bloodType.isNullOrEmpty()) {
            layoutBloodTypeContent.visibility = View.GONE
            layoutEmptyBloodType.visibility = View.VISIBLE
            btnSetBloodType.text = "Set"
        } else {
            layoutBloodTypeContent.visibility = View.VISIBLE
            layoutEmptyBloodType.visibility = View.GONE
            textBloodType.text = data.bloodType
            btnSetBloodType.text = "Edit"
        }
    }

    private fun setupConditionsAdapter(conditions: List<String>) {
        val adapter = SimpleTextAdapter(conditions)
        recyclerViewConditions.adapter = adapter
    }
}

// Simple adapter for displaying conditions
class SimpleTextAdapter(private val items: List<String>) :
    RecyclerView.Adapter<SimpleTextAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = items[position]
        holder.textView.setPadding(32, 16, 32, 16)
    }

    override fun getItemCount() = items.size
}
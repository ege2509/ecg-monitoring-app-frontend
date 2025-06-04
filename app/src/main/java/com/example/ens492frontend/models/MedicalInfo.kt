package com.example.ens492frontend.models
import kotlinx.serialization.Serializable
@Serializable
data class MedicalInfoRequest(
    val bloodType: String? = null,
    val allergies: String? = null,
    val medications: String? = null
)

@Serializable
data class UserMedicalInfo(
    val id: Long? = null,
    val userId: Long,
    val bloodType: String? = null,
    val allergies: String? = null,
    val medications: String? = null
) {
    // Helper properties for UI logic
    val files: List<String> get() = emptyList() // You'll need to implement file handling
    val conditions: List<String> get() = listOfNotNull(allergies, medications).filter { it.isNotBlank() }
}
package com.example.ens492frontend.models
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long? = null,
    val name: String,
    val email: String,
    val password: String? = null,
    val age: Int,
    val gender: String? = null,
    val profilePicture: String? = null
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val age: Int? = null,
    val gender: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class BasicResponse(
    val success: Boolean?=null,
    val message: String,
    val userId: Long? = null,
    val token: String? = null
)
@Serializable
data class EcgRecordingResponse(
    val id: Long,
    val heartRate: Double?,
    val recordingDate: String,
    val diagnosis: String
)
@Serializable
data class EcgRecording(
    val id: Long,
    val medicalInfoId: Long,
    val heartRate: Int,
    val diagnosis: String? = null,
    val sampleRate: Int,
    val numLeads: Int,
    val recordingDate: String,
    val processedData: String
)
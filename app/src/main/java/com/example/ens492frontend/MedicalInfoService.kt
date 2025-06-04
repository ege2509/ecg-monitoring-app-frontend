package com.example.ens492frontend


import com.example.ens492frontend.ApiClient
import com.example.ens492frontend.models.MedicalInfoRequest
import com.example.ens492frontend.models.UserMedicalInfo
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class MedicalInfoService {

    suspend fun getMedicalInfo(userId: Long): UserMedicalInfo? {
        return try {
            ApiClient.client.get("${ApiClient.baseUrl}/users/$userId/medical-info").body()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun createMedicalInfo(userId: Long, request: MedicalInfoRequest): UserMedicalInfo? {
        return try {
            ApiClient.client.post("${ApiClient.baseUrl}/users/$userId/medical-info") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getBloodType(userId: Long): String? {
        return try {
            ApiClient.client.get("${ApiClient.baseUrl}/users/blood_type") {
                parameter("userId", userId)
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun setBloodType(userId: Long, bloodType: String): UserMedicalInfo? {
        return try {
            ApiClient.client.post("${ApiClient.baseUrl}/users/blood_type") {
                parameter("userId", userId)
                parameter("bloodType", bloodType)
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getAllergies(userId: Long): String? {
        return try {
            ApiClient.client.get("${ApiClient.baseUrl}/users/allergies") {
                parameter("userId", userId)
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updateAllergies(userId: Long, allergies: String): String? {
        return try {
            ApiClient.client.post("${ApiClient.baseUrl}/users/allergies") {
                parameter("userId", userId)
                contentType(ContentType.Application.Json)
                setBody(allergies)
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getMedications(userId: Long): String? {
        return try {
            ApiClient.client.get("${ApiClient.baseUrl}/users/medications") {
                parameter("userId", userId)
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updateMedications(userId: Long, medications: String): String? {
        return try {
            ApiClient.client.post("${ApiClient.baseUrl}/users/medications") {
                parameter("userId", userId)
                contentType(ContentType.Application.Json)
                setBody(medications)
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
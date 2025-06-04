package com.example.ens492frontend

import android.util.Log
import com.example.ens492frontend.ApiClient.baseUrl
import com.example.ens492frontend.ApiClient.client
import com.example.ens492frontend.models.BasicResponse
import com.example.ens492frontend.models.EcgRecording
import com.example.ens492frontend.models.EcgRecordingResponse
import com.example.ens492frontend.models.LoginRequest
import com.example.ens492frontend.models.RegisterRequest
import com.example.ens492frontend.models.User
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object UserApi {
    suspend fun register(request: RegisterRequest): BasicResponse =
        ApiClient.client.post("${ApiClient.baseUrl}/users/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun login(request: LoginRequest): BasicResponse =
        ApiClient.client.post("${ApiClient.baseUrl}/users/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun getUserProfile(userId: Long): User =
        ApiClient.client.get("${ApiClient.baseUrl}/users/$userId").body()

    suspend fun updateUserProfile(userId: Long, userDetails: User): User =
        ApiClient.client.put("${ApiClient.baseUrl}/users/$userId") {
            contentType(ContentType.Application.Json)
            setBody(userDetails)
        }.body()

    suspend fun getUser(userId: Long): HttpResponse {
        return client.get("${ApiClient.baseUrl}/users/$userId")
    }


    suspend fun setActiveEcgUser(userId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.client.post("${ApiClient.baseUrl}/apiUser/set-active-ecg-user") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("userId" to userId))
                }

                if (!response.status.isSuccess()) {
                    Log.w("UserApi", "Failed to set active ECG user: ${response.status.value}")
                } else {

                }

            } catch (e: Exception) {
                Log.e("UserApi", "Error setting active ECG user", e)
            }
        }
    }

    suspend fun getUserRecordings(userId: Long): List<EcgRecordingResponse> =
        ApiClient.client.get("${ApiClient.baseUrl}/api/ecg/recordings") {
            parameter("userId", userId)
        }.body()
    suspend fun getRecording(recordingId: Long): EcgRecording =
        ApiClient.client.get("${ApiClient.baseUrl}/api/ecg/recording/$recordingId").body()
}
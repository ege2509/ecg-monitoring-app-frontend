package com.example.ens492frontend

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

    suspend fun getUserRecordings(userId: Long): List<EcgRecordingResponse> =
        ApiClient.client.get("${ApiClient.baseUrl}/api/ecg/recordings") {
            parameter("userId", userId)
        }.body()
    suspend fun getRecording(recordingId: Long): EcgRecording =
        ApiClient.client.get("${ApiClient.baseUrl}/api/ecg/recording/$recordingId").body()
}
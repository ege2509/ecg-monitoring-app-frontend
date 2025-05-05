package com.yourapp.api.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long? = null,
    val email: String,
    val password: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val profilePicture: String? = null
)

@Serializable
data class RegisterRequest(
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
    val success: Boolean,
    val message: String,
    val userId: Long? = null,
    val token: String? = null
)

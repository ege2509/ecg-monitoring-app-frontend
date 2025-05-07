package com.example.ens492frontend.models

import java.util.UUID

/**
 * Data class representing an ECG reading
 */
data class EcgReading(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val heartRate: Int,
    val abnormalities: Map<String, Float>,
    val leadData: Map<Int, List<Float>> = emptyMap()
)
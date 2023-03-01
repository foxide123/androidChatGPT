package com.example.audiorecordsample.models

data class SpeechToTextResponse(
    val requestId: Long,
    val results: List<Result>,
    val totalBilledTime: String
)
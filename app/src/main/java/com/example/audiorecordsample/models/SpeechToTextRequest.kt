package com.example.audiorecordsample.models

data class SpeechToTextRequest (
    val config: ConfigAPI,
    val audio: AudioAPI
)
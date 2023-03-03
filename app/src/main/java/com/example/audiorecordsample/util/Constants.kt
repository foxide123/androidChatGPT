package com.example.audiorecordsample.util

import android.media.AudioFormat
import android.media.AudioRecord

class Constants {
    companion object{
        const val BASE_URL = "https://speech.googleapis.com/"
        const val OAUTH_CLIENT_ID = "453045987930-ci205apban1bmkuvubuk9e9tt32ctr6r.apps.googleusercontent.com"
        const val OAUTH_CLIENT_SECRET = "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"
        const val CHAT_API_KEY = "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"

        //AUDIO
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val RC_AUTH = 100
    }
}
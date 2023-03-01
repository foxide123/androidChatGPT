package com.example.audiorecordsample.api

import android.content.ContentValues.TAG
import android.util.Log
import com.example.audiorecordsample.models.AudioAPI
import com.example.audiorecordsample.models.ConfigAPI
import com.example.audiorecordsample.models.SpeechToTextRequest

class TestCall {
    companion object{
        fun sendAudio(content: String) {
            val apiService = RestService()
            val speechToTextRequest = SpeechToTextRequest(
                config = ConfigAPI("en-US","LINEAR16",41000),
                audio = AudioAPI(content)
               // audio = AudioAPI("gs://vocie-recognition/newestaudio.wav")
            )

            apiService.sendAudio(speechToTextRequest) {response->
                if (response != null && response.requestId != 0L) {
                    // it = newly added user parsed as response
                    // it?.id = newly added user ID
                    Log.d("RESULT!!!!!!!!!!!!!!!!!!", response.results[0].alternatives[0].toString())
                    print(response.results[0])
                } else {
                    Log.d("Error: ","Error registering new user")
                }
            }
        }
    }
}
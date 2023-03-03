package com.example.audiorecordsample.api

import android.util.Log
import com.example.audiorecordsample.models.speechToText.SpeechToTextRequest
import com.example.audiorecordsample.models.speechToText.SpeechToTextResponse
import com.example.audiorecordsample.util.ServiceBuilder
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RestService {
    fun sendAudio(request: SpeechToTextRequest, onResult: (SpeechToTextResponse?) -> Unit){
        val retrofit = ServiceBuilder.buildService(ChatAPI::class.java)
        retrofit.sendAudio(request).enqueue(
            object: Callback<SpeechToTextResponse> {
                override fun onFailure(call: Call<SpeechToTextResponse>, t: Throwable) {
                    onResult(null)
                    Log.e("API Error", "Failed to send audio: ${t.message}")
                }
                override fun onResponse(call: Call<SpeechToTextResponse>, response: Response<SpeechToTextResponse>) {
                    if (response.isSuccessful) {
                        val addedUser = response.body()
                        onResult(addedUser)
                    } else {
                        Log.e("API Error", "Failed to send audio: ${response.code()} ${response.message()}")
                    }
                }
            }
        )
    }


}
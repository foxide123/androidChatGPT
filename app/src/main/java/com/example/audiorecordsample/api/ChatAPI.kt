package com.example.audiorecordsample.api

import com.example.audiorecordsample.models.SpeechToTextRequest
import com.example.audiorecordsample.models.SpeechToTextResponse
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface ChatAPI {
    @Headers("Content-Type: application/json",
        "Authorization: Bearer ya29.a0AVvZVspCPhbVgzfUbNFWa4jtIt9Gk4VaWhnHhlwp2CGVVi9T-lI-fS0CBEmZMc0cdnmSjKAqMZrlGbcPGwe3Oi0qTIvN-7SNHWjA3F5dZWxjjl3ALric418194d2NiO0y9EwZ9iIA05p7r9HXXWHmPa-RHZoaCgYKAdMSARISFQGbdwaIHsJLw9vF8xqTU3j-rC25Jg0163")
    @POST("v1/speech:recognize/")
    fun sendAudio(@Body speechToTextRequest: SpeechToTextRequest): Call<SpeechToTextResponse>
}
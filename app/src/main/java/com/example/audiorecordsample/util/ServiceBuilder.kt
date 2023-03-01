package com.example.audiorecordsample.util

import com.example.audiorecordsample.api.ChatAPI
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ServiceBuilder {
    val logging = HttpLoggingInterceptor()

    private val client = OkHttpClient.Builder().
    addInterceptor(logging).build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(Constants.BASE_URL) //
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()

    fun<T> buildService(service: Class<T>): T{
        return retrofit.create(service)
    }
}
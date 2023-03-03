package com.example.audiorecordsample

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.service.controls.ControlsProviderService
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.audiorecordsample.models.*
import com.example.audiorecordsample.models.speechToText.*
import com.example.audiorecordsample.util.Constants
import com.example.audiorecordsample.util.Constants.Companion.CHAT_API_KEY
import com.example.audiorecordsample.util.Constants.Companion.OAUTH_CLIENT_ID
import com.example.audiorecordsample.util.Constants.Companion.RC_AUTH
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*


class MainViewModel(application: Application) :AndroidViewModel(application) {

    var isRecordingAudio = false
    protected var audioRecord: AudioRecord? = null
    private var mAuthService: AuthorizationService? = AuthorizationService(getApplication())
    private var mStateManager: AuthStateManager? =  AuthStateManager.getInstance(getApplication())
    val BUFFER_SIZE_RECORDING =
        AudioRecord.getMinBufferSize(
            Constants.SAMPLE_RATE,
            Constants.CHANNEL_CONFIG_IN,
            Constants.AUDIO_FORMAT
        )
    private lateinit var fileAudio: File
    var fileNameAudio: String? = null
    private var recordingThread: Thread? = null
     var fileInBase64: String? = null;
    // val fileInBase64 = MutableLiveData<String>()
    val jsonBody: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }



    fun authenticationSetup(): AuthorizationRequest? {
        if (mStateManager?.current?.isAuthorized!!) {
            Log.d("Auth", "Done")
            mStateManager?.current?.performActionWithFreshTokens(
                mAuthService!!
            ) { accessToken, idToken, exception ->
                CoroutineScope(Dispatchers.IO).launch {
                    doInBackground(accessToken!!)
                }
            }
            return null
        } else {
            val serviceConfig = AuthorizationServiceConfiguration(
                Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"), // authorization endpoint
                Uri.parse("https://www.googleapis.com/oauth2/v4/token") // token endpoint
            )

            val clientId = OAUTH_CLIENT_ID
            val redirectUri = Uri.parse("com.example.audiorecordsample:/oauth2callback")
            val builder = AuthorizationRequest.Builder(
                serviceConfig,
                clientId,
                ResponseTypeValues.CODE,
                redirectUri
            )
            builder.setScope("https://www.googleapis.com/auth/cloud-platform")

            val authRequest = builder.build()
            return authRequest


            /*  val intentBuilder = mAuthService?.createCustomTabsIntentBuilder(authRequest.toUri())
            intentBuilder?.setToolbarColor(ContextCompat.getColor(this, R.color.colorAccent))
            customIntent = intentBuilder?.build()*/

        }
    }

    fun onResult(requestCode: Int, resultCode: Int, data: Intent?): String? {
        var accessToken: String? = null

        if (requestCode == RC_AUTH) {
            val resp = AuthorizationResponse.fromIntent(data!!)
            val ex = AuthorizationException.fromIntent(data)

            if (resp != null) {
                mAuthService = AuthorizationService(getApplication())
                mStateManager?.updateAfterAuthorization(resp, ex)

                mAuthService?.performTokenRequest(
                    resp.createTokenExchangeRequest()
                ) { resp, ex ->
                    if (resp != null) {

                        mStateManager?.updateAfterTokenResponse(resp, ex)
                        Log.d("accessToken", resp.accessToken!!)
                        accessToken= resp.accessToken
                    } else {
                        // authorization failed, check ex for more details
                    }
                }

                //Log.d("res",resp.accessToken)
                // authorization completed
            } else {
                // authorization failed, check ex for more details
            }
            // ... process the response or exception ...
        } else {
            // ...
        }
        if (mStateManager?.current?.isAuthorized!!) {
            Log.d("Auth", "Done")
            mStateManager?.current?.performActionWithFreshTokens(
                mAuthService!!
            ) { token, idToken, exception ->
                accessToken = token
            }

        }
        return accessToken
    }


     suspend fun doInBackground(token:String){
         Log.i("TAG", token);
         val requestConfig = SpeechToTextRequest(ConfigAPI("en-US","LINEAR16",44100), AudioAPI(fileInBase64!!))
         withContext(Dispatchers.IO) {
             val client = OkHttpClient()
             val request = Request.Builder()
                 .method("POST", Gson().toJson(requestConfig).toRequestBody("application/json".toMediaType()))
                 .url("https://speech.googleapis.com/v1/speech:recognize")
                 .addHeader("Authorization", "Bearer "+token)
                 .build()
             try {
                 val response = client.newCall(request).execute()
                 val responseInString = response.body.string()
                 //jsonBody.postValue(responseInString)
                 Log.i("LOG_TAG", String.format("User Info Response %s", responseInString))
                 val speechToTextResponse = mapResponse(responseInString)
                  jsonBody.postValue(speechToTextResponse.results[0].alternatives[0].transcript)
                // return@withContext JSONObject(jsonBody.value)
             } catch (exception: Exception) {
                 Log.w("LOG_TAG", exception)
             }
         }
    }

    suspend fun sendChatRequest(request: String){
        Log.i("VALUE_OF_EDITVIEW", request)


        val requestConfig = ChatRequest(listOf(Message(request, "user")), "gpt-3.5-turbo")
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .method("POST", Gson().toJson(requestConfig).toRequestBody("application/json".toMediaType()))
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer "+ CHAT_API_KEY)
                .build()
            try {
                val response = client.newCall(request).execute()
                val responseInString = response.body.string()
                //jsonBody.postValue(responseInString)
                Log.i("LOG_TAG", String.format("Chat response %s", responseInString))
                val speechToTextResponse = mapResponseChatGPT(responseInString)
                jsonBody.postValue(speechToTextResponse!!.choices[0].message.content)

            } catch (exception: Exception) {
                Log.w("LOG_TAG", exception)
            }
        }
    }





    fun mapResponseChatGPT(responseJson: String): ChatResponse? {
            val gson = Gson()
            val jsonObject = gson.fromJson(responseJson, JsonObject::class.java)
            val id = jsonObject.get("id").asString
            val `object` = jsonObject.get("object").asString
            val created = jsonObject.get("created").asInt
            val model = jsonObject.get("model").asString
            val choicesArray = jsonObject.getAsJsonArray("choices")
            val choices = choicesArray.map{choice->
                val index = choice.asJsonObject.get("index").asInt
                val finish_reason = choice.asJsonObject.get("finish_reason").asString
                val message = choice.asJsonObject.get("message").asJsonObject
                val role = message.get("role").asString
                val content = message.get("content").asString

                Choice(finish_reason, index, Message(content, role))
            }
            val usage = jsonObject.getAsJsonObject("usage")
            val prompt_tokens = usage.get("prompt_tokens").asInt
            val completion_tokens = usage.get("completion_tokens").asInt
            val total_tokens = usage.get("total_tokens").asInt

            return ChatResponse(choices, created, id, model, `object`, Usage(completion_tokens, prompt_tokens, total_tokens))
    }


    fun mapResponse(responseJson: String): SpeechToTextResponse {
        val gson = Gson()
        val jsonObject = gson.fromJson(responseJson, JsonObject::class.java)
        val resultsJsonArray = jsonObject.getAsJsonArray("results")
        val results = resultsJsonArray.map { resultJson ->
            val alternativesJsonArray = resultJson.asJsonObject.getAsJsonArray("alternatives")
            val alternatives = alternativesJsonArray.map { alternativeJson ->
                val transcript = alternativeJson.asJsonObject.get("transcript").asString
                val confidence = alternativeJson.asJsonObject.get("confidence").asDouble
                Alternative(confidence, transcript)
            }
            val languageCode = resultJson.asJsonObject.get("languageCode").asString
            val resultEndTime = resultJson.asJsonObject.get("resultEndTime").asString
            com.example.audiorecordsample.models.speechToText.Result(
                alternatives,
                languageCode,
                resultEndTime
            )
        }
        val totalBilledTime = jsonObject.get("totalBilledTime").asString
        val requestId = jsonObject.get("requestId").asString
        return SpeechToTextResponse(results, totalBilledTime, requestId)
    }


/*
    inner class ProfileTask : AsyncTask<String?, Void, JSONObject>() {
        override fun doInBackground(vararg tokens: String?): JSONObject? {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v3/userinfo")
                .addHeader("Authorization", String.format("Bearer %s", tokens[0]))
                .build()
            try {
                val response = client.newCall(request).execute()
                val jsonBody: String = response.body.string()
                Log.i("LOG_TAG", String.format("User Info Response %s", jsonBody))
                return JSONObject(jsonBody)
            } catch (exception: Exception) {
                Log.w("LOG_TAG", exception)
            }
            return null
        }
        override fun onPostExecute(userInfo: JSONObject?) {
            if (userInfo != null) {
                Log.d(ControlsProviderService.TAG, userInfo.toString())
            }
        }
    }

 */


    fun createFile(): File {
        fileNameAudio = "${getApplication<Application>().externalCacheDir?.absolutePath}/audiorecordtest.wav"
        fileAudio = File(fileNameAudio)
        if (!fileAudio.exists()) { // create empty files if needed
            try {
                fileAudio.createNewFile()
            } catch (e: IOException) {
                Log.d(ControlsProviderService.TAG, "could not create file " + e.toString())
                e.printStackTrace()
            }
        }
        return fileAudio;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startRecording() {
        if (audioRecord == null) { // safety check
            if (ActivityCompat.checkSelfPermission(
                    getApplication(),
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(getApplication(), arrayOf(Manifest.permission.RECORD_AUDIO), 200)
            }
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                Constants.SAMPLE_RATE,
                Constants.CHANNEL_CONFIG_IN,
                Constants.AUDIO_FORMAT,
                BUFFER_SIZE_RECORDING
            )
            if (audioRecord!!.state != AudioRecord.STATE_INITIALIZED) { // check for proper initialization
                Log.e(ControlsProviderService.TAG, "error initializing AudioRecord")
                return
            }
            audioRecord!!.startRecording()
            Log.d(ControlsProviderService.TAG, "recording started with AudioRecord")
            isRecordingAudio = true
            recordingThread = Thread { writeAudioDataToFile() }
            recordingThread!!.start()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun writeAudioDataToFile() { // called inside Runnable of recordingThread
        val data =
            ByteArray(BUFFER_SIZE_RECORDING / 2) // assign size so that bytes are read in in chunks inferior to AudioRecord internal buffer size
        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(fileNameAudio)
        } catch (e: FileNotFoundException) {
            // handle error
            Log.e(ControlsProviderService.TAG, "file not found for file name " + fileNameAudio + ", " + e.toString())
            return
        }
        while (isRecordingAudio) {
            val read = audioRecord!!.read(data, 0, data.size)
            try {
                outputStream.write(data, 0, read)
            } catch (e: IOException) {
                Log.d(ControlsProviderService.TAG, "IOException while recording with AudioRecord, $e")
                e.printStackTrace()
            }
        }
        try { // clean up file writing operations
            Log.d("Base64 byte array", Base64.getEncoder().encodeToString(data))
            outputStream.flush()
            outputStream.close()
        } catch (e: IOException) {
            Log.e(ControlsProviderService.TAG, "exception while closing output stream $e")
            e.printStackTrace()
        }
        audioRecord!!.stop()
        audioRecord!!.release()
        audioRecord = null
        recordingThread = null

/*
        val file = File(fileNameAudio)
        val bytes = fileAudio.readBytes()
        val base64String = Base64.getEncoder().encodeToString(bytes)
        Log.d("base64String", base64String)
        println("base64 println"+base64String)

        Log.d("Path", fileAudio.absolutePath)

 */
    }
}
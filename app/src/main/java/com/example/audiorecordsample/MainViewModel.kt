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
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.example.audiorecordsample.models.*
import com.example.audiorecordsample.models.speechToText.*
import com.example.audiorecordsample.repository.Repository
import com.example.audiorecordsample.util.Constants
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
import retrofit2.awaitResponse
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*


class MainViewModel(application: Application, val repository: Repository) :AndroidViewModel(application) {

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

            val clientId = BuildConfig.OAUTH_CLIENT_ID
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
         val requestConfig = SpeechToTextRequest(ConfigAPI("en-US","LINEAR16",44100), AudioAPI(fileInBase64!!))
         val speechToTextResponse = repository.getTextFromAudio(requestConfig, token).execute();
         if(speechToTextResponse.isSuccessful){
             jsonBody.postValue(speechToTextResponse.body()?.results?.get(0)?.alternatives?.get(0)?.transcript)
         }
         else{
             Exception("problem with http call")
         }

    }

    suspend fun sendChatRequest(request: String){
        Log.i("VALUE_OF_EDITVIEW", request)
        val requestConfig = ChatRequest(listOf(Message(request, "user")), "gpt-3.5-turbo")

        try{
            val chatResponse = repository.getCompletion(requestConfig).execute();
            jsonBody.postValue(chatResponse!!.body()?.choices?.get(0)?.message?.content?.trimStart())
        }catch(exception:Exception){
            Log.w("LOG_TAG", exception)
        }
    }


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
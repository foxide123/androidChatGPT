package com.example.audiorecordsample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.net.Uri
import android.net.UrlQuerySanitizer
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.service.controls.ControlsProviderService.TAG
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bumptech.glide.Glide
import com.example.audiorecordsample.api.TestCall
import com.example.audiorecordsample.util.Constants
import com.google.android.material.snackbar.Snackbar
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow
import com.google.api.client.auth.oauth2.TokenResponse
import net.openid.appauth.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.*
import java.util.*


class MainActivity : AppCompatActivity() {

    var SAMPLE_RATE = 44100 // supported on all devices
    val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT // not supported on all devices
    val BUFFER_SIZE_RECORDING =
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)

    protected var audioRecord: AudioRecord? = null


    var recordAudioRecord: Button? = null
    var playAudioTrack: Button? = null
    var sendAudioRecord: Button? = null
    private lateinit var authenticateButton: Button;

    var isRecordingAudio = false

    var fileNameAudio: String? = null

    private var recordingThread: Thread? = null

    private lateinit var fileAudio: File

    private val RC_AUTH = 100

    private var mAuthService: AuthorizationService? = null
    private var mStateManager: AuthStateManager? = null


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playAudioTrack = findViewById<Button>(R.id.play_audiotrack);
        recordAudioRecord = findViewById(R.id.record_audiorecord);
        sendAudioRecord = findViewById(R.id.send_audiotrack);
        authenticateButton = findViewById<Button>(R.id.authenticate);
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val fileName = "testfile.wav"
        //fileNameAudio = "$downloadsDir/$fileName"
      //  fileNameAudio = filesDir.path + "/testfile" + ".wav"

        fileNameAudio = "${externalCacheDir?.absolutePath}/audiorecordtest.wav"


         fileAudio = File(fileNameAudio)
        if (!fileAudio.exists()) { // create empty files if needed
            try {
                fileAudio.createNewFile()
            } catch (e: IOException) {
                Log.d(TAG, "could not create file " + e.toString())
                e.printStackTrace()
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) { // get permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
        }

        setListeners()

        mStateManager = AuthStateManager.getInstance(this)
        mAuthService = AuthorizationService(this)

        if (mStateManager?.current?.isAuthorized!!) {
            Log.d("Auth", "Done")
            authenticateButton.setText("Logout")
            mStateManager?.current?.performActionWithFreshTokens(
                mAuthService!!
            ) { accessToken, idToken, exception ->
                ProfileTask().execute(accessToken)
            }

        }

        authenticateButton.setOnClickListener {
            if (mStateManager?.current?.isAuthorized!!) {

            } else {
                val serviceConfig = AuthorizationServiceConfiguration(
                    Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"), // authorization endpoint
                    Uri.parse("https://www.googleapis.com/oauth2/v4/token") // token endpoint
                )

                val clientId =
                    "453045987930-ci205apban1bmkuvubuk9e9tt32ctr6r.apps.googleusercontent.com"
                val redirectUri = Uri.parse("com.example.audiorecordsample:/oauth2callback")
                val builder = AuthorizationRequest.Builder(
                    serviceConfig,
                    clientId,
                    ResponseTypeValues.CODE,
                    redirectUri
                )
                builder.setScopes("profile")

                val authRequest = builder.build()


                /*  val intentBuilder = mAuthService?.createCustomTabsIntentBuilder(authRequest.toUri())
                intentBuilder?.setToolbarColor(ContextCompat.getColor(this, R.color.colorAccent))
                customIntent = intentBuilder?.build()*/

                val authService = AuthorizationService(this)
                val authIntent = authService.getAuthorizationRequestIntent(authRequest)
                startActivityForResult(authIntent, RC_AUTH)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_AUTH) {
            val resp = AuthorizationResponse.fromIntent(data!!)
            val ex = AuthorizationException.fromIntent(data)

            if (resp != null) {
                mAuthService = AuthorizationService(this)
                mStateManager?.updateAfterAuthorization(resp, ex)

                mAuthService?.performTokenRequest(
                    resp.createTokenExchangeRequest()
                ) { resp, ex ->
                    if (resp != null) {

                        mStateManager?.updateAfterTokenResponse(resp, ex)
                        authenticateButton.setText("Logout")
                        Log.d("accessToken", resp.accessToken!!)
                        ProfileTask().execute(resp.accessToken)
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
            authenticateButton.text = "Logout"
            mStateManager?.current?.performActionWithFreshTokens(
                mAuthService!!
            ) { accessToken, idToken, exception ->
                ProfileTask().execute(accessToken)
            }

        }
    }

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
                Log.d(TAG, userInfo.toString())
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun setListeners() {
        recordAudioRecord!!.setOnClickListener {
            if (!isRecordingAudio) {
                startRecording()
            } else {
                stopRecording()
            }
        }

        sendAudioRecord!!.setOnClickListener{
            try{
                val bytes = fileAudio.readBytes()
                TestCall.Companion.sendAudio(Base64.getEncoder().encodeToString(bytes));
            }catch(exception: IOException){
                Log.d(TAG, "problem sending request")
            }
        }


/*
        playAudioTrack!!.setOnClickListener{
            if (!isPlayingAudio) {
                startPlaying();
            } else {
                stopPlaying();
            }
        }

 */
    }

 @RequiresApi(Build.VERSION_CODES.O)
 fun startRecording() {
    if (audioRecord == null) { // safety check
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT,
            BUFFER_SIZE_RECORDING
        )
        if (audioRecord!!.state != AudioRecord.STATE_INITIALIZED) { // check for proper initialization
            Log.e(TAG, "error initializing AudioRecord")
            return
        }
        audioRecord!!.startRecording()
        Log.d(TAG, "recording started with AudioRecord")
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
            Log.e(TAG, "file not found for file name " + fileNameAudio + ", " + e.toString())
            return
        }
        while (isRecordingAudio) {
            val read = audioRecord!!.read(data, 0, data.size)
            try {
                outputStream.write(data, 0, read)
            } catch (e: IOException) {
                Log.d(TAG, "IOException while recording with AudioRecord, $e")
                e.printStackTrace()
            }
        }
        try { // clean up file writing operations
            Log.d("Base64 byte array", Base64.getEncoder().encodeToString(data))
            outputStream.flush()
            outputStream.close()
        } catch (e: IOException) {
            Log.e(TAG, "exception while closing output stream $e")
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



    @RequiresApi(Build.VERSION_CODES.O)
    private fun stopRecording() {
        if (audioRecord != null) {
            isRecordingAudio = false; // triggers recordingThread to exit while loop
        }
       // downloadFile(fileAudio)
    }

















/*
    @RequiresApi(Build.VERSION_CODES.O)
    private fun downloadFile(file: File) {
        // Get the Download directory on external storage
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        // Create the file in the downloads directory
        val outputFile = File(downloadsDir, file.name)

        // Copy the file to the external storage
        val inputStream = FileInputStream(file)
        val outputStream = FileOutputStream(outputFile)
        inputStream.copyTo(outputStream)


        val file = File(fileNameAudio)
        val bytes = fileAudio.readBytes()
        val base64String = Base64.getEncoder().encodeToString(bytes)
        Log.d("base64StringIn downloadFile method", base64String)
        // Notify the MediaScanner about the new file
        MediaScannerConnection.scanFile(
            applicationContext,
            arrayOf(file.absolutePath),
            null,
            null
        )

        // Show a toast message to confirm that the download is complete
        Toast.makeText(
            applicationContext,
            "File downloaded to ${file.absolutePath}",
            Toast.LENGTH_SHORT
        ).show()
    }

 */





}
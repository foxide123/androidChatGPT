package com.example.audiorecordsample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.os.Bundle
import android.service.controls.ControlsProviderService
import android.service.controls.ControlsProviderService.TAG
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.audiorecordsample.repository.Repository
import com.example.audiorecordsample.util.Constants
import com.example.audiorecordsample.util.Constants.Companion.AUDIO_FORMAT
import com.example.audiorecordsample.util.Constants.Companion.CHANNEL_CONFIG_IN
import com.example.audiorecordsample.util.Constants.Companion.SAMPLE_RATE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.openid.appauth.*
import java.io.*
import java.util.*


class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {


    val BUFFER_SIZE_RECORDING =
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)

    protected var audioRecord: AudioRecord? = null


    var recordAudioRecord: Button? = null
    var playAudioTrack: Button? = null
    var sendAudioRecord: Button? = null
    private lateinit var authenticateButton: Button;
    var textView: TextView? = null

    var isRecordingAudio = false

    var fileNameAudio: String? = null

    private var recordingThread: Thread? = null

    private lateinit var fileAudio: File


    lateinit var viewModel: MainViewModel
    private var tts: TextToSpeech? = null
    private var speak: Boolean = false;


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val repository = Repository()
        val viewModelProviderFactory = MainViewModelProvider(this.application, repository)
        viewModel = ViewModelProvider(this, viewModelProviderFactory).get(MainViewModel::class.java)


        playAudioTrack = findViewById<Button>(R.id.play_audiotrack);
        recordAudioRecord = findViewById(R.id.record_audiorecord);
        sendAudioRecord = findViewById(R.id.send_audiotrack);
        authenticateButton = findViewById<Button>(R.id.authenticate);
        textView = findViewById<EditText>(R.id.textView)
        tts = TextToSpeech(this, this)

        fileNameAudio = "${externalCacheDir?.absolutePath}/audiorecordtest.wav"
        fileAudio = File(fileNameAudio)
        if (!fileAudio.exists()) { // create empty files if needed
            try {
                fileAudio.createNewFile()
            } catch (e: IOException) {
                Log.d(ControlsProviderService.TAG, "could not create file " + e.toString())
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


    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS","The Language not supported!")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val accessToken = viewModel.onResult(requestCode, resultCode, data)
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.doInBackground(accessToken!!)
        }
    }



    @RequiresApi(Build.VERSION_CODES.O)
    fun setListeners() {
        viewModel.jsonBody.observe(this) { newName ->
            // Update the UI, in this case, a TextView.
            textView?.text = newName
            if(speak) {
                speakOut()
                speak=false
            }
        }

        recordAudioRecord!!.setOnClickListener {
            if (!isRecordingAudio) {
                startRecording()
            } else {
                stopRecording()
            }
        }

        sendAudioRecord!!.setOnClickListener{
            try{
                val request = textView?.text.toString()
                textView?.text = null
                lifecycleScope.launch(Dispatchers.IO) {
                    speak = true
                    viewModel.sendChatRequest(request)
                }
                speakOut()
            }catch(exception: IOException){
                Log.d(TAG, "problem sending request")
            }
        }
        authenticateButton.setOnClickListener {
            val authRequest = viewModel.authenticationSetup()
            val authService = AuthorizationService(this)
            if(authRequest != null){
                val authIntent = authService.getAuthorizationRequestIntent(authRequest)
                startActivityForResult(authIntent, Constants.RC_AUTH)
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

    private fun speakOut() {
        val text = textView!!.text.toString()
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null,"")
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



        val bytes = fileAudio.readBytes()
        val base64String = Base64.getEncoder().encodeToString(bytes)
        Log.d("base64String", base64String)
        println("base64 println"+base64String)
        viewModel.fileInBase64 = base64String

        Log.d("Path", fileAudio.absolutePath)

    }



    @RequiresApi(Build.VERSION_CODES.O)
    private fun stopRecording() {
        if (audioRecord != null) {
            isRecordingAudio = false; // triggers recordingThread to exit while loop
        }
       // downloadFile(fileAudio)
    }


    public override fun onDestroy() {
        // Shutdown TTS when
        // activity is destroyed
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        super.onDestroy()
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
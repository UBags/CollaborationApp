package com.costheta.cortexa.util.audio

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.costheta.cortexa.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * An Activity for recording, playing back, and saving audio attachments with live transcription.
 *
 * This activity is designed to be launched for a result. It presents a dialog-based UI for
 * managing the audio recording process.
 *
 * ---
 *
 * ### Key Features:
 * - **Simultaneous Recording & Transcription (Android 13+):** On API 33 and above, this activity
 * uses the `AudioRecord` API to capture a raw audio stream. This stream is simultaneously
 * saved to a file and piped directly to `SpeechRecognizer` for live, on-device transcription,
 * avoiding any microphone conflicts.
 * - **Backward Compatibility:** For devices running below Android 13, the activity gracefully
 * falls back to the robust `MediaRecorder` API, providing a record-only feature without
 * live transcription to ensure stability.
 * - **WAV File Generation:** The raw PCM audio data captured via `AudioRecord` is automatically
 * packaged into a standard, playable `.wav` file by writing the appropriate header.
 * - **Robust State Management & Resource Safety**: Manages UI states and correctly releases all
 * resources (`AudioRecord`, `MediaRecorder`, `MediaPlayer`, etc.) to prevent crashes and leaks.
 *
 * ---
 *
 * ### How to Use:
 * Launch this activity using `registerForActivityResult`. On a successful result, the `Intent`
 * will contain the `Uri` of the saved audio file and the final transcribed text.
 */
class RecordAudioActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUDIO_URI = "extra_audio_uri"
        const val EXTRA_TRANSCRIPTION = "extra_transcription"
        private const val TAG = "RecordAudioActivity"

        // Audio configuration for both AudioRecord and SpeechRecognizer
        private const val SAMPLE_RATE = 16000 // 16kHz is standard for speech recognition
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    }

    // --- State Management ---
    private enum class State { IDLE, RECORDING, PAUSED, PLAYING }
    private var currentState: State = State.IDLE

    // --- Modern Recording & Transcription (API 33+) ---
    private var audioRecord: AudioRecord? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionExecutor: ExecutorService? = Executors.newSingleThreadExecutor()
    private var audioPipe: Array<ParcelFileDescriptor>? = null
    private var finalTranscription: StringBuilder = StringBuilder() // BUG FIX: This now holds the complete text.
    private var stableTranscriptionPart: StringBuilder = StringBuilder() // Holds confirmed parts of transcription.

    // --- Legacy Recording (Pre-API 33) ---
    private var mediaRecorder: MediaRecorder? = null

    // --- Common Recording & Playback ---
    private var tempAudioFile: File? = null
    private var recordingTimerExecutor: ExecutorService? = null
    private var recordingSeconds: Int = 0
    private var mediaPlayer: MediaPlayer? = null
    private val playbackHandler = Handler(Looper.getMainLooper())
    private var playbackSeekBar: SeekBar? = null
    private var playbackCurrentTimeTv: TextView? = null

    // --- UI & Permissions ---
    private var recordingDialog: AlertDialog? = null
    private var saveDialog: AlertDialog? = null
    private val requestAudioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            showRecordingDialog()
        } else {
            Toast.makeText(this, R.string.component_audio_permission_denied_message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    //================================================================================
    // Activity Lifecycle
    //================================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
    }

    override fun onStop() {
        super.onStop()
        if (currentState == State.RECORDING || currentState == State.PAUSED) {
            stopRecording(showSaveDialog = false)
        }
        if (currentState == State.PLAYING) {
            stopPlayback()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAllResources()
    }

    private fun checkAndRequestPermissions() {
        // NOTE: For cloud transcription, you would also need to add and request Manifest.permission.INTERNET
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            showRecordingDialog()
        } else {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    //================================================================================
    // Recording Logic (Dispatcher)
    //================================================================================

    /**
     * Starts the recording process by dispatching to the appropriate implementation
     * based on the Android API level.
     */
    private fun startRecording() {
        if (currentState != State.IDLE) return

        // Reset transcription holders for a new session
        finalTranscription.clear()
        stableTranscriptionPart.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startRecordingAndTranscriptionApi33()
        } else {
            startRecordingLegacy()
        }
    }

    /**
     * Stops the recording process and cleans up resources.
     * @param showSaveDialog If true, the save dialog is shown. Otherwise, the recording is discarded.
     */
    private fun stopRecording(showSaveDialog: Boolean) {
        if (currentState != State.RECORDING && currentState != State.PAUSED) return

        // Stop the timer first
        recordingTimerExecutor?.shutdownNow()
        recordingTimerExecutor = null

        // Stop either AudioRecord or MediaRecorder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopRecordingAndTranscriptionApi33()
        } else {
            stopRecordingLegacy()
        }

        recordingDialog?.dismiss()

        if (showSaveDialog && tempAudioFile != null && tempAudioFile!!.exists()) {
            // After stopping, convert the raw PCM to a playable WAV file.
            val wavFile = createWavFromPcm(tempAudioFile!!)
            if (wavFile != null) {
                showSaveDialog(Uri.fromFile(wavFile), finalTranscription.toString().trim())
            } else {
                Toast.makeText(this, "Failed to process audio file.", Toast.LENGTH_LONG).show()
                cleanUpRecording(deleteFile = true)
                finish()
            }
        } else {
            cleanUpRecording(deleteFile = true)
            if (showSaveDialog) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }


    //================================================================================
    // Modern Recording & Transcription (API 33+)
    //================================================================================

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun startRecordingAndTranscriptionApi33() {
        // 1. Create a temporary file for the raw PCM audio data.
        tempAudioFile = createTemporaryMediaFile("AUD_RAW_", ".pcm", Environment.DIRECTORY_MUSIC)
        if (tempAudioFile == null) {
            Log.e(TAG, "Failed to create temporary PCM file.")
            return
        }

        try {
            // 2. Set up the pipe to stream audio to SpeechRecognizer.
            audioPipe = ParcelFileDescriptor.createPipe()
            val pipeReadEnd = audioPipe!![0]
            val pipeWriteEnd = ParcelFileDescriptor.AutoCloseOutputStream(audioPipe!![1])

            // 3. Configure and start AudioRecord.
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build())
                .setBufferSizeInBytes(BUFFER_SIZE)
                .build()

            audioRecord?.startRecording()

            // 4. Configure and start SpeechRecognizer to listen on the pipe.
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)?.apply {
                setRecognitionListener(createRecognitionListener())
            }
            val recognitionIntent = createRecognitionIntent(pipeReadEnd)
            speechRecognizer?.startListening(recognitionIntent)

            // 5. Start a background thread to read from AudioRecord and write to both the file and the pipe.
            val fileOutputStream = FileOutputStream(tempAudioFile)
            recognitionExecutor = Executors.newSingleThreadExecutor()
            recognitionExecutor?.execute {
                val buffer = ByteArray(BUFFER_SIZE)
                while (currentState == State.RECORDING) {
                    val bytesRead = audioRecord?.read(buffer, 0, BUFFER_SIZE) ?: -1
                    if (bytesRead > 0) {
                        try {
                            fileOutputStream.write(buffer, 0, bytesRead) // Write to file
                            pipeWriteEnd.write(buffer, 0, bytesRead)   // Write to pipe
                        } catch (e: IOException) {
                            Log.e(TAG, "Error writing audio data.", e)
                            break
                        }
                    }
                }
                // Clean up streams
                try {
                    fileOutputStream.close()
                    pipeWriteEnd.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing streams.", e)
                }
            }

            // 6. Update state and UI.
            currentState = State.RECORDING
            startRecordingTimer()
            updateRecordingDialogUI()
            Toast.makeText(this, R.string.component_audio_audio_recording_started, Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "startRecordingAndTranscriptionApi33 failed", e)
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
            releaseAllResources()
        }
    }

    /** Stops the `AudioRecord` and `SpeechRecognizer` session. */
    private fun stopRecordingAndTranscriptionApi33() {
        if (currentState != State.RECORDING && currentState != State.PAUSED) return

        currentState = State.IDLE // Set state to stop the reading loop

        // Stop recognizer and audio capture
        speechRecognizer?.stopListening()
        audioRecord?.stop()

        // Shutdown executor and release resources
        recognitionExecutor?.shutdownNow()
        recognitionExecutor = null
        releaseAudioRecord()
        releaseSpeechRecognizer()

        try {
            audioPipe?.get(0)?.close()
            audioPipe?.get(1)?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing audio pipe", e)
        }
    }

    //================================================================================
    // Legacy Recording (Pre-API 33)
    //================================================================================

    /** Initializes `MediaRecorder` for older Android versions. */
    private fun startRecordingLegacy() {
        try {
            tempAudioFile = createTemporaryMediaFile("AUD_", ".mp3", Environment.DIRECTORY_MUSIC)
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(tempAudioFile!!.absolutePath)
                prepare()
                start()
            }
            currentState = State.RECORDING
            startRecordingTimer()
            updateRecordingDialogUI()
            Toast.makeText(this, R.string.component_audio_audio_recording_started, Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e(TAG, "startRecordingLegacy failed", e)
            cleanUpRecording(deleteFile = true)
        }
    }

    /** Stops the `MediaRecorder` session. */
    private fun stopRecordingLegacy() {
        try {
            mediaRecorder?.stop()
        } catch (e: RuntimeException) {
            Log.e(TAG, "stopRecordingLegacy failed", e)
        } finally {
            releaseMediaRecorder()
            currentState = State.IDLE
        }
    }

    //================================================================================
    // Pausing Logic (Placeholder)
    //================================================================================

    private fun pauseRecording() {
        if (currentState != State.RECORDING) return
        currentState = State.PAUSED
        stopRecordingTimer()
        updateRecordingDialogUI()
        // Note: For AudioRecord, this simply stops the reading loop. For MediaRecorder, it uses the API.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mediaRecorder != null) {
            mediaRecorder?.pause()
        }
        Toast.makeText(this, R.string.component_audio_recording_paused_toast, Toast.LENGTH_SHORT).show()
    }

    private fun resumeRecording() {
        if (currentState != State.PAUSED) return
        currentState = State.RECORDING
        startRecordingTimer()
        updateRecordingDialogUI()
        // For AudioRecord, the reading loop will resume. For MediaRecorder, use the API.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mediaRecorder != null) {
            mediaRecorder?.resume()
        }
        Toast.makeText(this, R.string.component_audio_recording_resumed_toast, Toast.LENGTH_SHORT).show()
    }


    //================================================================================
    // Playback, Dialogs, and UI
    //================================================================================

    private fun startPlayback(audioUri: Uri) {
        if (currentState == State.PLAYING) stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@RecordAudioActivity, audioUri)
                setOnPreparedListener { player ->
                    currentState = State.PLAYING
                    player.start()
                    playbackSeekBar?.max = player.duration
                    updateSaveDialogUI()
                    startPlaybackTimer()
                }
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, _, _ -> stopPlayback(); true }
                prepareAsync()
            }
        } catch (e: IOException) {
            Log.e(TAG, "startPlayback failed", e)
        }
    }

    private fun stopPlayback() {
        playbackHandler.removeCallbacks(updatePlaybackProgress)
        releaseMediaPlayer()
        currentState = State.IDLE
        playbackSeekBar?.progress = 0
        playbackCurrentTimeTv?.text = formatTime(0)
        updateSaveDialogUI()
    }

    private fun showRecordingDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.attachment_audio_recording_dialog, null)
        recordingDialog = AlertDialog.Builder(this).setView(view).setCancelable(false).show()

        val pauseButton = view.findViewById<Button>(R.id.buttonPauseRecording)
        // Pausing is complex with the new API, simplified for now.
        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        //     pauseButton.visibility = View.GONE
        // }

        view.findViewById<Button>(R.id.buttonStartRecording).setOnClickListener { startRecording() }
        pauseButton.setOnClickListener {
            if (currentState == State.RECORDING) pauseRecording() else if (currentState == State.PAUSED) resumeRecording()
        }
        view.findViewById<Button>(R.id.buttonStopRecording).setOnClickListener { stopRecording(showSaveDialog = true) }

        updateRecordingDialogUI()
    }

    private fun updateRecordingDialogUI() {
        recordingDialog?.let { dialog ->
            val statusTv = dialog.findViewById<TextView>(R.id.textViewRecordingStatus)
            val startBtn = dialog.findViewById<Button>(R.id.buttonStartRecording)
            val pauseBtn = dialog.findViewById<Button>(R.id.buttonPauseRecording)
            val stopBtn = dialog.findViewById<Button>(R.id.buttonStopRecording)
            val transcriptionTv = dialog.findViewById<TextView>(R.id.textViewTranscription)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                transcriptionTv?.text = getString(R.string.component_audio_transcription_not_supported_notice)
            }

            when (currentState) {
                State.IDLE -> {
                    statusTv?.text = getString(R.string.component_audio_ready_to_record)
                    startBtn?.isEnabled = true
                    pauseBtn?.isEnabled = false
                    stopBtn?.isEnabled = false
                    pauseBtn?.text = getString(R.string.component_audio_pause)
                }
                State.RECORDING -> {
                    statusTv?.text = getString(R.string.component_audio_recording_now)
                    startBtn?.isEnabled = false
                    pauseBtn?.isEnabled = true
                    stopBtn?.isEnabled = true
                    pauseBtn?.text = getString(R.string.component_audio_pause)
                }
                State.PAUSED -> {
                    statusTv?.text = getString(R.string.component_audio_recording_paused)
                    startBtn?.isEnabled = false
                    pauseBtn?.isEnabled = true
                    stopBtn?.isEnabled = true
                    pauseBtn?.text = getString(R.string.component_audio_resume_recording)
                }
                else -> {}
            }
        }
    }

    private fun showSaveDialog(audioUri: Uri, transcription: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.attachment_save_audio_note_dialog, null)
        playbackSeekBar = view.findViewById(R.id.seekBarPlayback)
        playbackCurrentTimeTv = view.findViewById(R.id.textViewCurrentTime)
        val totalTimeTv = view.findViewById<TextView>(R.id.textViewTotalTime)
        val transcriptionPreview = view.findViewById<TextView>(R.id.textViewTranscriptionPreview)

        try {
            val tempPlayer = MediaPlayer.create(this, audioUri)
            totalTimeTv.text = formatTime(tempPlayer.duration)
            tempPlayer.release()
        } catch (e: Exception) {
            totalTimeTv.text = formatTime(0)
        }

        // This is the core logic for your request.
        if (transcription.isNotBlank()) {
            transcriptionPreview.text = transcription
        } else {
            // If on-device transcription failed, display a message.
            // This is where you would trigger a cloud-based transcription service.
            transcriptionPreview.text = getString(R.string.component_audio_no_transcription_available)
            Log.d(TAG, "No on-device transcription. Ready for cloud fallback.")
            // Example: launchCloudTranscription(audioUri)
        }

        view.findViewById<Button>(R.id.buttonPlayAudio).setOnClickListener { startPlayback(audioUri) }
        view.findViewById<Button>(R.id.buttonStopPlayback).setOnClickListener { stopPlayback() }

        playbackSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mediaPlayer?.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        saveDialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_ok_button) { _, _ ->
                stopPlayback()
                // Pass the final transcription text when saving.
                saveAudioToMediaStore(audioUri, transcriptionPreview.text.toString())
            }
            .setNegativeButton(R.string.dialog_cancel_button) { _, _ ->
                stopPlayback()
                cleanUpRecording(deleteFile = true)
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .show()

        updateSaveDialogUI()
    }

    private fun updateSaveDialogUI() {
        saveDialog?.let { dialog ->
            val playBtn = dialog.findViewById<Button>(R.id.buttonPlayAudio)
            val stopBtn = dialog.findViewById<Button>(R.id.buttonStopPlayback)
            playBtn?.isEnabled = currentState != State.PLAYING
            stopBtn?.isEnabled = currentState == State.PLAYING
        }
    }

    //================================================================================
    // Timers & Helpers
    //================================================================================

    private fun startRecordingTimer() {
        recordingTimerExecutor = Executors.newSingleThreadScheduledExecutor()
        recordingTimerExecutor?.execute {
            recordingSeconds = 0
            while (currentState == State.RECORDING || currentState == State.PAUSED) {
                if (currentState == State.RECORDING) {
                    recordingSeconds++
                    runOnUiThread {
                        recordingDialog?.findViewById<TextView>(R.id.textViewRecordingTimer)?.text = formatTime(recordingSeconds * 1000)
                    }
                }
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingTimerExecutor?.shutdownNow()
    }

    private val updatePlaybackProgress = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    playbackSeekBar?.progress = it.currentPosition
                    playbackCurrentTimeTv?.text = formatTime(it.currentPosition)
                    playbackHandler.postDelayed(this, 500)
                }
            }
        }
    }

    private fun startPlaybackTimer() {
        playbackHandler.post(updatePlaybackProgress)
    }

    private fun formatTime(milliseconds: Int): String {
        val minutes = (milliseconds / 1000) / 60
        val seconds = (milliseconds / 1000) % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    //================================================================================
    // Resource Management & File Handling
    //================================================================================

    private fun releaseAllResources() {
        releaseMediaRecorder()
        releaseMediaPlayer()
        releaseAudioRecord()
        releaseSpeechRecognizer()
        recordingTimerExecutor?.shutdownNow()
        recognitionExecutor?.shutdownNow()
        recordingDialog?.dismiss()
        saveDialog?.dismiss()
    }

    private fun cleanUpRecording(deleteFile: Boolean) {
        releaseAllResources()
        if (deleteFile) {
            tempAudioFile?.delete()
            tempAudioFile = null
        }
        currentState = State.IDLE
        updateRecordingDialogUI()
    }

    private fun releaseMediaRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun releaseAudioRecord() {
        audioRecord?.release()
        audioRecord = null
    }

    private fun releaseSpeechRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun createTemporaryMediaFile(prefix: String, suffix: String, directory: String): File? {
        return try {
            val storageDir = getExternalFilesDir(directory)
            File.createTempFile(prefix, suffix, storageDir)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create temp file", e)
            null
        }
    }

    private fun saveAudioToMediaStore(sourceUri: Uri, transcription: String) {
        val contentResolver = contentResolver
        val displayName = "AudioNote_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.wav"
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val details = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/TimetableApp")
            }
        }

        var newUri: Uri? = null
        try {
            newUri = contentResolver.insert(collection, details)
            newUri?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            val resultIntent = Intent().apply {
                putExtra(EXTRA_AUDIO_URI, newUri)
                putExtra(EXTRA_TRANSCRIPTION, transcription)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            Toast.makeText(this, R.string.component_audio_save_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "saveAudioToMediaStore failed", e)
            newUri?.let { contentResolver.delete(it, null, null) }
            setResult(Activity.RESULT_CANCELED)
        } finally {
            cleanUpRecording(deleteFile = true)
            finish()
        }
    }

    //================================================================================
    // Speech Recognition & Intent Creation
    //================================================================================

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun createRecognitionIntent(pipe: ParcelFileDescriptor): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Use the pipe as the audio source
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe)
            // Provide the exact audio format to the recognizer
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AUDIO_FORMAT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)

            // Standard recognition parameters
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "SpeechRecognizer: Ready") }
            override fun onBeginningOfSpeech() { Log.d(TAG, "SpeechRecognizer: Beginning") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d(TAG, "SpeechRecognizer: End of speech detected.")
                // For continuous transcription, restart listening after speech ends.
                if (currentState == State.RECORDING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    speechRecognizer?.startListening(createRecognitionIntent(audioPipe!![0]))
                }
            }
            override fun onError(error: Int) {
                Log.e(TAG, "SpeechRecognizer Error: $error")
                // Restart listening even on a non-fatal error
                if (currentState == State.RECORDING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && error != SpeechRecognizer.ERROR_CLIENT) {
                    speechRecognizer?.startListening(createRecognitionIntent(audioPipe!![0]))
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    // This is a final, stable result. Append it to our stable part.
                    stableTranscriptionPart.append(matches[0]).append(" ")
                    // Update the final transcription to match.
                    finalTranscription.clear().append(stableTranscriptionPart)
                    runOnUiThread {
                        recordingDialog?.findViewById<TextView>(R.id.textViewTranscription)?.text = finalTranscription.toString()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    // BUG FIX: Update the finalTranscription variable with the latest text the user sees.
                    // This ensures that what's displayed is what gets saved.
                    finalTranscription.clear().append(stableTranscriptionPart).append(partialText)
                    runOnUiThread {
                        recordingDialog?.findViewById<TextView>(R.id.textViewTranscription)?.text = finalTranscription.toString()
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    //================================================================================
    // PCM to WAV Conversion
    //================================================================================

    /**
     * Converts a raw PCM audio file to a playable WAV file by adding a 44-byte header.
     * @param pcmFile The raw PCM file.
     * @return A new File object pointing to the created WAV file, or null on failure.
     */
    private fun createWavFromPcm(pcmFile: File): File? {
        val wavFile = createTemporaryMediaFile("AUD_FINAL_", ".wav", Environment.DIRECTORY_MUSIC) ?: return null
        var fileInputStream: FileInputStream? = null
        var fileOutputStream: FileOutputStream? = null

        try {
            fileInputStream = FileInputStream(pcmFile)
            fileOutputStream = FileOutputStream(wavFile)
            val pcmDataSize = pcmFile.length()
            val wavFileSize = pcmDataSize + 36 // 44 byte header - 8 bytes for RIFF chunk

            writeWavHeader(fileOutputStream, pcmDataSize, wavFileSize)
            fileInputStream.copyTo(fileOutputStream)

            return wavFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to convert PCM to WAV", e)
            return null
        } finally {
            try {
                fileInputStream?.close()
                fileOutputStream?.close()
                pcmFile.delete() // Clean up the raw PCM file
            } catch (e: IOException) {
                Log.e(TAG, "Error closing streams during WAV conversion", e)
            }
        }
    }

    /**
     * Writes the 44-byte WAV file header to the output stream.
     */
    @Throws(IOException::class)
    private fun writeWavHeader(out: FileOutputStream, pcmDataSize: Long, totalDataLen: Long) {
        val channels = 1
        val bitDepth: Short = 16
        val byteRate = (SAMPLE_RATE * channels * bitDepth) / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte(); header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte(); header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Sub-chunk size (16 for PCM)
        header[20] = 1; header[21] = 0 // Audio format (1 for PCM)
        header[22] = channels.toByte(); header[23] = 0
        ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(SAMPLE_RATE)
        ByteBuffer.wrap(header, 28, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate)
        header[32] = (channels * bitDepth / 8).toByte(); header[33] = 0 // Block align
        header[34] = bitDepth.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmDataSize and 0xff).toByte(); header[41] = (pcmDataSize shr 8 and 0xff).toByte()
        header[42] = (pcmDataSize shr 16 and 0xff).toByte(); header[43] = (pcmDataSize shr 24 and 0xff).toByte()

        out.write(header, 0, 44)
    }
}

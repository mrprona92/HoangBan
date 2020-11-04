package com.simplemobiletools.voicerecorder.services

import android.R.id
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.activities.SplashActivity
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.helpers.*
import com.simplemobiletools.voicerecorder.models.Events
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.*


class RecorderService : Service() {
    private val AMPLITUDE_UPDATE_MS = 75L

    var currFilePath = ""
    private var duration = 0
    private var isRecording = false
    private var durationTimer = Timer()
    private var amplitudeTimer = Timer()
    private var recorder: MediaRecorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent.action) {
            GET_RECORDER_INFO -> broadcastRecorderInfo()
            STOP_AMPLITUDE_UPDATE -> amplitudeTimer.cancel()
            GET_RECORDER_INFO1 -> startRecording("1")
            GET_RECORDER_INFO2 -> startRecording("2")
            GET_RECORDER_INFO3 -> startRecording("3")
            GET_RECORDER_INFO4 -> startRecording("4")
            GET_RECORDER_INFO5 -> startRecording("5")
            GET_RECORDER_INFO6 -> startRecording("6")
            GET_RECORDER_INFO7 -> startRecording("7")
            GET_RECORDER_INFO8 -> startRecording("8")
            GET_RECORDER_INFO9 -> startRecording("9")
            else -> startRecording(intent.action ?: "1")
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }

    // mp4 output format with aac encoding should produce good enough m4a files according to https://stackoverflow.com/a/33054794/1967672
    private fun startRecording(fileName: String) {
        val baseFolder = if (isQPlus()) {
            cacheDir
        } else {
            val defaultFolder = File(config.saveRecordingsFolder)
            if (!defaultFolder.exists()) {
                defaultFolder.mkdir()
            }

            defaultFolder.absolutePath
        }

        currFilePath = "$baseFolder/${fileName}.${config.getExtensionText()}"

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)

            try {
                if (!isQPlus() && isPathOnSD(currFilePath)) {
                    var document = getDocumentFile(currFilePath.getParentPath())
                    document = document?.createFile("", currFilePath.getFilenameFromPath())

                    val outputFileDescriptor =
                        contentResolver.openFileDescriptor(document!!.uri, "w")!!.fileDescriptor
                    setOutputFile(outputFileDescriptor)
                } else {
                    setOutputFile(currFilePath)
                }

                prepare()
                start()
                duration = 0
                isRecording = true
                broadcastRecorderInfo()
                startForeground(RECORDER_RUNNING_NOTIF_ID, showNotification())

                durationTimer = Timer()
                durationTimer.scheduleAtFixedRate(getDurationUpdateTask(), 1000, 1000)

                startAmplitudeUpdates()
            } catch (e: Exception) {
                showErrorToast(e)
                stopRecording()
            }
        }
    }

    private fun stopRecording() {
        durationTimer.cancel()
        amplitudeTimer.cancel()
        isRecording = false

        recorder?.apply {
            try {
                stop()
                release()

                ensureBackgroundThread {
                    if (isQPlus()) {
                        addFileInNewMediaStore()
                    } else {
                        addFileInLegacyMediaStore()
                    }
                    EventBus.getDefault().post(Events.RecordingCompleted())
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
        recorder = null
    }

    private fun broadcastRecorderInfo() {
        broadcastDuration()
        broadcastStatus()

        if (isRecording) {
            startAmplitudeUpdates()
        }
    }

    private fun startAmplitudeUpdates() {
        amplitudeTimer.cancel()
        amplitudeTimer = Timer()
        amplitudeTimer.scheduleAtFixedRate(getAmplitudeUpdateTask(), 0, AMPLITUDE_UPDATE_MS)
    }

    @SuppressLint("InlinedApi")
    private fun addFileInNewMediaStore() {
        val audioCollection = Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val storeFilename = currFilePath.getFilenameFromPath()
        val newSongDetails = ContentValues().apply {
            put(Media.DISPLAY_NAME, storeFilename)
            put(Media.TITLE, storeFilename)
            put(Media.MIME_TYPE, storeFilename.getMimeType())
        }

        val updateUris = getExistingImageUriOrNullQ(storeFilename)
        if (updateUris == null) {
            val newUri = contentResolver.insert(audioCollection, newSongDetails)
            if (newUri == null) {
                toast(R.string.unknown_error_occurred)
                return
            }

            val outputStream = contentResolver.openOutputStream(newUri)
            val inputStream = getFileInputStreamSync(currFilePath)
            inputStream!!.copyTo(outputStream!!, DEFAULT_BUFFER_SIZE)
            recordingSavedSuccessfully(true)
        } else {
            val outputStream = contentResolver.openOutputStream(updateUris)
            val inputStream = getFileInputStreamSync(currFilePath)
            inputStream!!.copyTo(outputStream!!, DEFAULT_BUFFER_SIZE)
            recordingSavedSuccessfully(true)
        }
    }

    private fun getExistingImageUriOrNullQ(fileName: String): Uri? {
        var imageUri: Uri?
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,   // unused (for verification use only)
            MediaStore.Audio.Media.RELATIVE_PATH,  // unused (for verification use only)
            MediaStore.Audio.Media.DATE_MODIFIED   //used to set signature for Glide
        )

        // take note of the / after OLArt
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME}='$fileName'"

        contentResolver.query(
            Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            projection, selection, null, null
        ).use { c ->
            if (c != null && c.count >= 1) {
                print("has cursor result")
                c.moveToFirst().let {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val displayName =
                        c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME))
                    val relativePath =
                        c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH))

                    val updatedSongDetails = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    }

                    // take note of the / after OLArt
                    val selection = "${MediaStore.Audio.Media._ID} = ?"
                    val selectionArgs = arrayOf(id.toString())
                    val tempUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )


                    contentResolver.update(
                        tempUri,
                        updatedSongDetails,
                        selection,
                        selectionArgs
                    )

                    return tempUri
                }
            }
        }
        print("image not created yet")
        return null
    }


    private fun addFileInLegacyMediaStore() {
        MediaScannerConnection.scanFile(
            this,
            arrayOf(currFilePath),
            arrayOf(currFilePath.getMimeType())
        ) { _, _ -> recordingSavedSuccessfully(false) }
    }

    private fun recordingSavedSuccessfully(showFilenameOnly: Boolean) {
        val title = if (showFilenameOnly) currFilePath.getFilenameFromPath() else currFilePath
        val msg = String.format(getString(R.string.recording_saved_successfully), title)
        toast(msg, Toast.LENGTH_LONG)
    }

    private fun getDurationUpdateTask() = object : TimerTask() {
        override fun run() {
            duration++
            broadcastDuration()
        }
    }

    private fun getAmplitudeUpdateTask() = object : TimerTask() {
        override fun run() {
            if (recorder != null) {
                EventBus.getDefault().post(Events.RecordingAmplitude(recorder!!.maxAmplitude))
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun showNotification(): Notification {
        val hideNotification = config.hideNotification
        val channelId = "simple_recorder"
        val label = getString(R.string.app_name)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (isOreoPlus()) {
            val importance =
                if (hideNotification) NotificationManager.IMPORTANCE_MIN else NotificationManager.IMPORTANCE_DEFAULT
            NotificationChannel(channelId, label, importance).apply {
                setSound(null, null)
                notificationManager.createNotificationChannel(this)
            }
        }

        var priority = Notification.PRIORITY_DEFAULT
        var icon = R.drawable.ic_microphone_vector
        var title = label
        var text = getString(R.string.recording)
        var visibility = NotificationCompat.VISIBILITY_PUBLIC

        if (hideNotification) {
            priority = Notification.PRIORITY_MIN
            icon = R.drawable.ic_empty
            title = ""
            text = ""
            visibility = NotificationCompat.VISIBILITY_SECRET
        }

        val builder = NotificationCompat.Builder(this)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(icon)
            .setContentIntent(getOpenAppIntent())
            .setPriority(priority)
            .setVisibility(visibility)
            .setSound(null)
            .setOngoing(true)
            .setAutoCancel(true)
            .setChannelId(channelId)

        return builder.build()
    }

    private fun getOpenAppIntent(): PendingIntent {
        val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
        return PendingIntent.getActivity(
            this,
            RECORDER_RUNNING_NOTIF_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun broadcastDuration() {
        EventBus.getDefault().post(Events.RecordingDuration(duration))
    }

    private fun broadcastStatus() {
        EventBus.getDefault().post(Events.RecordingStatus(isRecording))
    }
}

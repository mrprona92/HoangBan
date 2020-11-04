package com.simplemobiletools.voicerecorder.helpers

import android.annotation.SuppressLint
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import com.simplemobiletools.commons.helpers.isQPlus

const val RECORDER_RUNNING_NOTIF_ID = 10000

private const val PATH = "com.simplemobiletools.voicerecorder.action."
const val GET_RECORDER_INFO = PATH + "GET_RECORDER_INFO"
const val STOP_AMPLITUDE_UPDATE = PATH + "STOP_AMPLITUDE_UPDATE"

const val EXTENSION_M4A = 0
const val EXTENSION_MP3 = 1

// shared preferences
const val HIDE_NOTIFICATION = "hide_notification"
const val SAVE_RECORDINGS = "save_recordings"
const val EXTENSION = "extension"

@SuppressLint("InlinedApi")
fun getAudioFileContentUri(id: Long): Uri {
    val baseUri = if (isQPlus()) {
        Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        Media.EXTERNAL_CONTENT_URI
    }

    return ContentUris.withAppendedId(baseUri, id)
}


const val GET_RECORDER_INFO1 = PATH + "GET_RECORDER_INFO1"
const val GET_RECORDER_INFO2 = PATH + "GET_RECORDER_INFO2"
const val GET_RECORDER_INFO3 = PATH + "GET_RECORDER_INFO3"
const val GET_RECORDER_INFO4 = PATH + "GET_RECORDER_INFO4"
const val GET_RECORDER_INFO5 = PATH + "GET_RECORDER_INFO5"
const val GET_RECORDER_INFO6 = PATH + "GET_RECORDER_INFO6"
const val GET_RECORDER_INFO7 = PATH + "GET_RECORDER_INFO7"
const val GET_RECORDER_INFO8 = PATH + "GET_RECORDER_INF08"
const val GET_RECORDER_INFO9 = PATH + "GET_RECORDER_INFO9"

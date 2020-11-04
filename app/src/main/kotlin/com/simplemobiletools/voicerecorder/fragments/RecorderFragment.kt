package com.simplemobiletools.voicerecorder.fragments

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.extensions.config
import com.simplemobiletools.voicerecorder.helpers.*
import com.simplemobiletools.voicerecorder.models.Events
import com.simplemobiletools.voicerecorder.services.RecorderService
import com.skydoves.powerspinner.OnSpinnerItemSelectedListener
import com.vistrav.ask.Ask
import kotlinx.android.synthetic.main.fragment_recorder.*
import kotlinx.android.synthetic.main.fragment_recorder.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class RecorderFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment(context, attributeSet) {
    private var isRecording = false
    private var bus: EventBus? = null

    var currentMode: Int = 1


    override fun onResume() {
        setupColors()
    }

    override fun onDestroy() {
        bus?.unregister(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupColors()
        recorder_visualizer.recreate()
        bus = EventBus.getDefault()
        bus!!.register(this)

        updateRecordingDuration(0)
        toggle_recording_button.setOnClickListener {
            toggleRecording()
        }

        spinner.setOnSpinnerItemSelectedListener(object : OnSpinnerItemSelectedListener<String?> {
            override fun onItemSelected(position: Int, item: String?) {
                currentMode = (position + 1)
            }
        })

        Intent(context, RecorderService::class.java).apply {
            action = GET_RECORDER_INFO
            context.startService(this)
        }
    }

    private fun setupColors() {
        val adjustedPrimaryColor = context.getAdjustedPrimaryColor()
        toggle_recording_button.apply {
            setImageDrawable(getToggleButtonIcon())
            background.applyColorFilter(adjustedPrimaryColor)
        }

        recorder_visualizer.chunkColor = adjustedPrimaryColor
        recording_duration.setTextColor(context.config.textColor)
    }

    private fun updateRecordingDuration(duration: Int) {
        recording_duration.text = duration.getFormattedDuration()
    }

    private fun getToggleButtonIcon(): Drawable {
        val drawable =
            if (isRecording) R.drawable.ic_stop_vector else R.drawable.ic_microphone_vector
        return resources.getColoredDrawableWithColor(drawable, context.getFABIconColor())
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        toggle_recording_button.setImageDrawable(getToggleButtonIcon())

        if (isRecording) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    private fun startRecording() {
        Intent(context, RecorderService::class.java).apply {
            action = when (currentMode) {
                1 -> {
                    GET_RECORDER_INFO1
                }
                2 -> {
                    GET_RECORDER_INFO2
                }
                3 -> {
                    GET_RECORDER_INFO3
                }
                4 -> {
                    GET_RECORDER_INFO4
                }
                5 -> {
                    GET_RECORDER_INFO5
                }
                6 -> {
                    GET_RECORDER_INFO6
                }
                7 -> {
                    GET_RECORDER_INFO7
                }
                8 -> {
                    GET_RECORDER_INFO8
                }
                9 -> {
                    GET_RECORDER_INFO9
                }
                else -> {
                    GET_RECORDER_INFO1
                }
            }
            context.startService(this)
        }
    }

    private fun stopRecording() {
        Intent(context, RecorderService::class.java).apply {
            context.stopService(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun gotDurationEvent(event: Events.RecordingDuration) {
        updateRecordingDuration(event.duration)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun gotStatusEvent(event: Events.RecordingStatus) {
        isRecording = event.isRecording
        toggle_recording_button.setImageDrawable(getToggleButtonIcon())
        if (isRecording) {
            recorder_visualizer.recreate()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun gotAmplitudeEvent(event: Events.RecordingAmplitude) {
        val amplitude = event.amplitude
        recorder_visualizer.update(amplitude)
    }

    fun hideDialog() {
        spinner.dismiss()
    }
}

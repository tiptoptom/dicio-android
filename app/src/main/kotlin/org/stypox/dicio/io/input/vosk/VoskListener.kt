/*
 * Taken from /e/OS Assistant
 *
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.stypox.dicio.io.input.vosk

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.InputEventsModule
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.Collections


/**
 * This class is an implementation of [RecognitionListener] that listens to events generated by a
 * Vosk [SpeechService], pushes them to [inputEventsModule], and handles the listening state via
 * [VoskInputDevice].
 */
internal class VoskListener(
    private val voskInputDevice: VoskInputDevice,
    private val inputEventsModule: InputEventsModule,
    private val speechService: SpeechService,
) : RecognitionListener {

    /**
     * Called when partial recognition result is available.
     */
    override fun onPartialResult(s: String) {
        Log.d(TAG, "onPartialResult called with s = $s")

        // Try to obtain the partial result from the json object. Unlike in `onResult`, where more
        // than one alternative may be generated, in this case there will be only the alternative
        // with most confidence.
        var partialInput: String? = null
        try {
            partialInput = JSONObject(s).getString("partial")
        } catch (e: JSONException) {
            Log.e(TAG, "Can't obtain partial result from $s", e)
        }

        // emit a partial event
        partialInput?.also {
            if (it.isNotBlank()) {
                inputEventsModule.tryEmitEvent(InputEvent.Partial(it))
            }
        }
    }

    /**
     * Called after silence occurred.
     */
    @Suppress("ktlint:Style:NestedBlockDepth")
    override fun onResult(s: String) {
        Log.d(TAG, "onResult called with s = $s")

        // we only want to listen one sentence at a time
        voskInputDevice.stopListening(speechService, false)

        // collect all alternative user inputs generated by the STT model
        val inputs = try {
            val jsonResult = JSONObject(s)
            utterancesFromJson(jsonResult)
        } catch (e: JSONException) {
            Log.e(TAG, "Can't obtain result from $s", e)
            inputEventsModule.tryEmitEvent(InputEvent.Error(e))
            return
        }

        // emit the final event
        if (inputs.isEmpty()) {
            inputEventsModule.tryEmitEvent(InputEvent.None)
        } else {
            inputEventsModule.tryEmitEvent(InputEvent.Final(inputs))
        }
    }

    private fun utterancesFromJson(jsonResult: JSONObject): List<String> {
        if (jsonResult.has("alternatives")) {
            // the "alternatives" array will exist only if setMaxAlternatives is called when
            // creating `SpeechService`'s `Recognizer`
            val size: Int = jsonResult.getJSONArray("alternatives").length()
            val inputs = ArrayList<String>()
            for (i in 0 until size) {
                val text = jsonResult.getJSONArray("alternatives")
                    .getJSONObject(i).getString("text")
                if (text.isNotEmpty()) {
                    inputs.add(text)
                }
            }
            return inputs
        } else {
            // alternatives are disabled, so there is just one possible user input
            return Collections.singletonList(jsonResult.getString("text"))
        }
    }

    /**
     * Called after stream end. In our case, always called after silence occurred, since we call
     * stopListening in onResult.
     */
    override fun onFinalResult(s: String) {
        Log.d(TAG, "onFinalResult called with s = $s")
    }
    
    /**
     * Called when an error occurs.
     */
    override fun onError(e: Exception) {
        Log.e(TAG, "onError called", e)
        voskInputDevice.stopListening(speechService, false)
        inputEventsModule.tryEmitEvent(InputEvent.Error(e))
    }

    /**
     * Called after timeout expired
     */
    override fun onTimeout() {
        Log.d(TAG, "onTimeout called")
        voskInputDevice.stopListening(speechService, true)
    }

    companion object {
        private val TAG = VoskListener::class.simpleName
    }
}
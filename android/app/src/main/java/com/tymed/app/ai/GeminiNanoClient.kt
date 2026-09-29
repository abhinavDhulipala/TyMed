package com.tymed.app.ai

import android.os.Build
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.first

/** Seam between [AiOrchestrator] and the actual model backend, so tests can substitute a fake
 * without touching real ML Kit / AICore classes. */
interface AiClient {
    suspend fun generate(prompt: String): String
}

/**
 * Thin wrapper around ML Kit's GenAI Prompt API (com.google.mlkit.genai.prompt), which runs
 * Gemini Nano through the AICore system service — no tool-calling or JSON parsing here; all
 * of that lives in [Tools]/[AiOrchestrator] so business rules exist in exactly one place.
 *
 * `prepare()` collapses ML Kit's checkStatus() + download() into one call: it resolves once the
 * model is on-device and ready, false otherwise.
 */
class GeminiNanoClient : AiClient {
    // Retained for the process lifetime once prepared: generateContent() is a stateless
    // prompt->text call with no session of its own, and preparing is too expensive to repeat on
    // every turn.
    private var model: GenerativeModel? = null
    private var ready = false

    // ML Kit GenAI's own manifest requires API 26+ (see the <uses-sdk tools:overrideLibrary=.../>
    // entry in AndroidManifest.xml) — below that this just reports unavailable instead of
    // touching ML Kit classes at all.
    suspend fun prepare(): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        if (ready) return true

        val instance = model ?: Generation.getClient().also { model = it }

        return try {
            val status = instance.checkStatus()
            Log.i(TAG, "Gemini Nano featureStatus=$status")
            when (status) {
                FeatureStatus.AVAILABLE -> Unit
                // DOWNLOADING: download() attaches to the in-flight download and reports its outcome.
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    val outcome = instance.download().first {
                        it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed
                    }
                    if (outcome is DownloadStatus.DownloadFailed) {
                        Log.w(TAG, "Gemini Nano download failed (errorCode=${outcome.e.errorCode})", outcome.e)
                        return false
                    }
                }
                else -> {
                    Log.w(TAG, "Gemini Nano unavailable on this device (featureStatus=$status)")
                    return false
                }
            }
            // Loads the model into memory so the first chat turn isn't the slow one.
            instance.warmup()
            ready = true
            true
        } catch (e: GenAiException) {
            Log.w(TAG, "Gemini Nano prepare failed (errorCode=${e.errorCode})", e)
            false
        }
    }

    override suspend fun generate(prompt: String): String {
        val instance = model?.takeIf { ready } ?: error("GeminiNanoClient.generate called before a successful prepare()")
        return instance.generateContent(prompt).candidates.firstOrNull()?.text ?: ""
    }

    private companion object {
        const val TAG = "GeminiNanoClient"
    }
}

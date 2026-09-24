package expo.modules.tymedai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import java.io.File
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Thin wrapper around ML Kit's GenAI Prompt API (com.google.mlkit.genai.prompt), which runs
// Gemini Nano through the AICore system service — no tool-calling or JSON parsing here; all of
// that lives in TS (src/ai/*) so business rules exist in exactly one place.
//
// This replaced the experimental Google AI Edge SDK (com.google.ai.edge.aicore:0.0.1-exp02),
// which fails on current AICore builds (verified on a Pixel 10 Pro XL / Android 17) with
// "INFERENCE_ERROR / NOT_AVAILABLE: Required LLM feature not found" — ML Kit is Google's
// supported path for Gemini Nano on newer devices.
//
// prepare() collapses ML Kit's checkStatus() + download() into one call from JS's side: it
// resolves "available" once the model is on-device and ready, "unavailable" otherwise.
class TymedAiModule : Module() {
  // Retained for the process lifetime once prepared: generateContent() is a stateless
  // prompt->text call with no session of its own, and preparing is too expensive to repeat on
  // every turn.
  private var model: GenerativeModel? = null
  private var ready = false

  override fun definition() = ModuleDefinition {
    Name("TymedAi")

    // Explicit type argument: a zero-arg lambda is otherwise ambiguous between the Coroutine
    // overloads for `suspend () -> R` and `suspend (P0) -> R`.
    AsyncFunction("prepare").Coroutine<String> {
      prepare()
    }

    AsyncFunction("generate") Coroutine { prompt: String ->
      generate(prompt)
    }

    OnCreate {
      if (BuildConfig.AI_EVAL_BRIDGE) registerEvalBridge()
    }

    // AICore refuses inference for background apps, so an eval run dies the moment the screen
    // sleeps — eval builds keep it on while the app is open.
    OnActivityEntersForeground {
      if (BuildConfig.AI_EVAL_BRIDGE) {
        appContext.currentActivity?.let { activity ->
          activity.runOnUiThread { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
      }
    }

    OnDestroy {
      evalReceiver?.let { receiver -> appContext.reactContext?.unregisterReceiver(receiver) }
      evalReceiver = null
      model?.close()
      model = null
      ready = false
    }
  }

  // ML Kit GenAI's own manifest requires API 26+ (see plugins/withAiCoreManifestOverride.js for
  // the build-time manifest-merger override that makes that coexist with this app's minSdk 24)
  // — so this is the runtime half of that: below API 26 the feature just reports "unavailable"
  // instead of touching ML Kit classes at all.
  private suspend fun prepare(): String {
    if (Build.VERSION.SDK_INT < 26) return "unavailable"
    if (ready) return "available"

    val instance = model ?: Generation.getClient().also { model = it }

    return try {
      val status = instance.checkStatus()
      Log.i(TAG, "Gemini Nano featureStatus=$status")
      when (status) {
        FeatureStatus.AVAILABLE -> Unit
        // DOWNLOADING: download() attaches to the in-flight download and reports its outcome.
        FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
          val outcome = instance.download().first {
            when (it) {
              is DownloadStatus.DownloadStarted -> Log.i(TAG, "Gemini Nano download started")
              is DownloadStatus.DownloadProgress ->
                Log.i(TAG, "Gemini Nano downloaded ${it.totalBytesDownloaded} bytes")
              else -> Unit
            }
            it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed
          }
          if (outcome is DownloadStatus.DownloadFailed) {
            Log.w(TAG, "Gemini Nano download failed (errorCode=${outcome.e.errorCode})", outcome.e)
            return "unavailable"
          }
        }
        else -> {
          Log.w(TAG, "Gemini Nano unavailable on this device (featureStatus=$status)")
          return "unavailable"
        }
      }
      // Loads the model into memory so the first chat turn isn't the slow one.
      Log.i(TAG, "Gemini Nano warming up")
      instance.warmup()
      Log.i(TAG, "Gemini Nano ready")
      ready = true
      "available"
    } catch (e: GenAiException) {
      Log.w(TAG, "Gemini Nano prepare failed (errorCode=${e.errorCode})", e)
      "unavailable"
    }
  }

  private suspend fun generate(prompt: String): String {
    val instance = model?.takeIf { ready }
      ?: throw IllegalStateException("TymedAi.generate called before a successful prepare()")
    return instance.generateContent(prompt).candidates.firstOrNull()?.text ?: ""
  }

  // ---- eval bridge ------------------------------------------------------------------------
  // Lets the host-side eval suite (src/ai/__evals__, `npm run eval:ai`) run prompts through the
  // real on-device Gemini Nano, which has no off-device equivalent. Compiled in only when the app
  // is built with -PtymedAiEval=true, and only callable from adb: the receiver requires the
  // DUMP permission, which the adb shell holds and third-party apps can't get.
  private var evalReceiver: BroadcastReceiver? = null

  private fun registerEvalBridge() {
    val context = appContext.reactContext ?: return
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val prompt = String(Base64.decode(intent.getStringExtra("prompt_b64") ?: return, Base64.DEFAULT))
        val outDir = File(ctx.getExternalFilesDir(null), "ai-eval").apply { mkdirs() }
        // Result files are written whole and then renamed, so the host never reads a partial one.
        appContext.backgroundCoroutineScope.launch {
          val output = try {
            if (prepare() != "available") "ERROR: model unavailable" else "OK\n" + generate(prompt)
          } catch (e: Exception) {
            "ERROR: ${e.javaClass.simpleName}: ${e.message}"
          }
          val tmp = File(outDir, "$id.tmp").apply { writeText(output) }
          tmp.renameTo(File(outDir, "$id.txt"))
        }
      }
    }
    val filter = IntentFilter(EVAL_ACTION)
    if (Build.VERSION.SDK_INT >= 33) {
      context.registerReceiver(receiver, filter, android.Manifest.permission.DUMP, null, Context.RECEIVER_EXPORTED)
    } else {
      context.registerReceiver(receiver, filter, android.Manifest.permission.DUMP, null)
    }
    evalReceiver = receiver
    Log.i(TAG, "AI eval bridge enabled")
  }

  companion object {
    private const val TAG = "TymedAi"
    private const val EVAL_ACTION = "expo.modules.tymedai.EVAL"
  }
}

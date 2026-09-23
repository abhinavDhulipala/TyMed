package expo.modules.tymedai

import android.os.Build
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

// Thin wrapper around com.google.ai.edge.aicore.GenerativeModel — no tool-calling or JSON
// parsing here; all of that lives in TS (src/ai/*) so business rules exist in exactly one place.
// The real SDK has no checkStatus()/FeatureStatus query and no separate download() call (unlike
// the similarly-named ML Kit GenAI API) — prepareInferenceEngine() is the one call that both
// downloads the model (if needed) and prepares it, suspending until it's ready or throwing a
// GenerativeAIException. That collapses "check availability" and "download" into a single
// prepare() from JS's side.
class TymedAiModule : Module() {
  // Retained for the process lifetime once prepared: generateContent() is a stateless
  // prompt->text call with no session of its own, and prepareInferenceEngine() is too expensive
  // to repeat on every turn.
  private var model: GenerativeModel? = null

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
  }

  // AICore's own manifest requires API 31+ (see plugins/withAiCoreManifestOverride.js for the
  // build-time manifest-merger override that makes that coexist with this app's minSdk 24) — so
  // this is the runtime half of that: below API 31 the feature just reports "unavailable"
  // instead of touching AICore classes at all.
  private suspend fun prepare(): String {
    if (Build.VERSION.SDK_INT < 31) return "unavailable"
    val reactContext = appContext.reactContext ?: return "unavailable"

    val instance = model ?: GenerativeModel(
      generationConfig { context = reactContext },
      DownloadConfig()
    ).also { model = it }

    return try {
      instance.prepareInferenceEngine()
      "available"
    } catch (e: GenerativeAIException) {
      // Covers both "this device doesn't support Gemini Nano at all" and "the download failed" —
      // JS can't act differently on either today, so both just mean "not available right now".
      model = null
      "unavailable"
    }
  }

  private suspend fun generate(prompt: String): String {
    val instance = model ?: throw IllegalStateException("TymedAi.generate called before a successful prepare()")
    return instance.generateContent(prompt).text ?: ""
  }
}

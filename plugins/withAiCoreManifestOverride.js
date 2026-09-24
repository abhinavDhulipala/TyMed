const { withAndroidManifest, AndroidConfig } = require('@expo/config-plugins');

// ML Kit GenAI's AAR manifests (genai-prompt and its genai-common dependency) declare
// minSdkVersion 26 — above this app's minSdkVersion 24. Bumping the whole app's minSdk would
// drop support for existing users on older phones just for an optional AI feature that's already
// gated behind a runtime SDK_INT check anyway (see modules/tymed-ai's TymedAiModule.kt), so
// instead this tells the Android manifest merger the app knowingly accepts those dependencies'
// higher floor without raising its own — the standard tools:overrideLibrary escape hatch for
// exactly this situation.
function withAiCoreManifestOverride(config) {
  return withAndroidManifest(config, (config) => {
    const manifest = AndroidConfig.Manifest.ensureToolsAvailable(config.modResults);
    if (!manifest.manifest['uses-sdk']) {
      manifest.manifest['uses-sdk'] = [{ $: {} }];
    }
    manifest.manifest['uses-sdk'][0].$['tools:overrideLibrary'] =
      'com.google.mlkit.genai.prompt,com.google.mlkit.genai.common';
    return config;
  });
}

module.exports = withAiCoreManifestOverride;

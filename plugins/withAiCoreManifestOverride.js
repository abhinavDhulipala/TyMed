const { withAndroidManifest, AndroidConfig } = require('@expo/config-plugins');

// AICore's own AAR manifest declares minSdkVersion 31 (Android 12) — far above this app's
// minSdkVersion 24. Bumping the whole app's minSdk would drop support for existing users on
// older phones just for an optional AI feature that's already gated behind a runtime SDK_INT
// check anyway (see modules/tymed-ai's TymedAiModule.kt), so instead this tells the Android
// manifest merger the app knowingly accepts that one dependency's higher floor without raising
// its own — the standard tools:overrideLibrary escape hatch for exactly this situation.
function withAiCoreManifestOverride(config) {
  return withAndroidManifest(config, (config) => {
    const manifest = AndroidConfig.Manifest.ensureToolsAvailable(config.modResults);
    if (!manifest.manifest['uses-sdk']) {
      manifest.manifest['uses-sdk'] = [{ $: {} }];
    }
    manifest.manifest['uses-sdk'][0].$['tools:overrideLibrary'] = 'com.google.ai.edge.aicore';
    return config;
  });
}

module.exports = withAiCoreManifestOverride;

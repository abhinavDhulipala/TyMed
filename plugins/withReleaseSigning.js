const fs = require('fs');
const path = require('path');
const { withAppBuildGradle } = require('@expo/config-plugins');

// Injects a release signingConfig into android/app/build.gradle on every `expo prebuild` (that
// folder is gitignored and regenerated from scratch, both locally and in CI, so signing setup
// has to be re-applied here rather than hand-edited once). Reads from keystore.properties at
// the repo root, which is itself gitignored and never committed — locally you create it
// yourself (see keystores/README.md); in CI the release workflow writes it from GitHub Actions
// secrets before running prebuild. If that file is absent, release builds fall back to debug
// signing, so a plain checkout still builds for local development without any secrets.
const SIGNING_CONFIGS_ANCHOR = `    signingConfigs {
        debug {
            storeFile file('debug.keystore')
            storePassword 'android'
            keyAlias 'androiddebugkey'
            keyPassword 'android'
        }
    }`;

const RELEASE_BUILD_TYPE_ANCHOR = `        release {
            // Caution! In production, you need to generate your own keystore file.
            // see https://reactnative.dev/docs/signed-apk-android.
            signingConfig signingConfigs.debug`;

function withReleaseSigning(config) {
  return withAppBuildGradle(config, (config) => {
    const projectRoot = config.modRequest.projectRoot;
    const propsPath = path.join(projectRoot, 'keystore.properties');
    if (!fs.existsSync(propsPath)) {
      return config;
    }

    let contents = config.modResults.contents;

    if (!contents.includes(SIGNING_CONFIGS_ANCHOR)) {
      throw new Error(
        'withReleaseSigning: expected signingConfigs block not found in app/build.gradle — ' +
          'the Expo/React Native template changed, update plugins/withReleaseSigning.js to match.'
      );
    }
    if (!contents.includes(RELEASE_BUILD_TYPE_ANCHOR)) {
      throw new Error(
        'withReleaseSigning: expected release buildType block not found in app/build.gradle — ' +
          'the Expo/React Native template changed, update plugins/withReleaseSigning.js to match.'
      );
    }

    contents = contents.replace(
      SIGNING_CONFIGS_ANCHOR,
      `${SIGNING_CONFIGS_ANCHOR.slice(0, -6)}
        release {
            storeFile rootProject.file(keystoreProperties['RELEASE_STORE_FILE'])
            storePassword keystoreProperties['RELEASE_STORE_PASSWORD']
            keyAlias keystoreProperties['RELEASE_KEY_ALIAS']
            keyPassword keystoreProperties['RELEASE_KEY_PASSWORD']
        }
    }`
    );

    contents = contents.replace(
      RELEASE_BUILD_TYPE_ANCHOR,
      RELEASE_BUILD_TYPE_ANCHOR.replace('signingConfigs.debug', 'signingConfigs.release')
    );

    const loader = `def keystoreProperties = new Properties()
keystoreProperties.load(new FileInputStream(rootProject.file('../keystore.properties')))

`;
    contents = loader + contents;

    config.modResults.contents = contents;
    return config;
  });
}

module.exports = withReleaseSigning;

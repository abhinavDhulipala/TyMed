#!/usr/bin/env node
// Rewrites app.json's expo.version and expo.android.versionCode from a "vX.Y.Z" git tag, run by
// the release workflow before `expo prebuild`. This is what actually makes a new release
// install as an *update* (preserving all existing medications/history) rather than being
// silently rejected or treated as a fresh install — Android requires a strictly increasing
// versionCode, and without this every build defaulted to versionCode 1 forever.
const fs = require('fs');
const path = require('path');

const tag = process.argv[2];
if (!tag) {
  console.error('Usage: set-version-from-tag.js vX.Y.Z');
  process.exit(1);
}

const match = tag.match(/^v?(\d+)\.(\d+)\.(\d+)$/);
if (!match) {
  console.error(`Tag "${tag}" isn't of the form vX.Y.Z`);
  process.exit(1);
}

const versionName = `${match[1]}.${match[2]}.${match[3]}`;
// Room for 99 minor versions and 99 patch versions per major before the scheme needs revisiting
// — plenty for this app's pace, and versionCode only needs to keep increasing, never be "clean."
const versionCode = Number(match[1]) * 10000 + Number(match[2]) * 100 + Number(match[3]);

const appJsonPath = path.join(__dirname, '..', 'app.json');
const appJson = JSON.parse(fs.readFileSync(appJsonPath, 'utf8'));

appJson.expo.version = versionName;
appJson.expo.android = appJson.expo.android || {};
appJson.expo.android.versionCode = versionCode;

fs.writeFileSync(appJsonPath, JSON.stringify(appJson, null, 2) + '\n');
console.log(`Set version ${versionName} (versionCode ${versionCode}) from tag ${tag}`);

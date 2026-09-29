# Icon sources

SVG sources for the app's icon set, matching the mascot drawn in
`android/app/src/main/java/com/tymed/app/ui/components/Mascot.kt` (and animated in
`android/app/src/main/java/com/tymed/app/alarm/MascotView.kt`) and the warm palette in
`android/app/src/main/java/com/tymed/app/ui/theme/Theme.kt`. Edit these and re-render if the
mascot or theme colors change — the PNGs in `assets/` are generated output, not hand-edited.
Regenerating no longer copies them into `android/app/src/main/res/` automatically (that was an
Expo prebuild step, since removed) — copy the relevant output into the matching
`mipmap-*`/`drawable-*` folders by hand after regenerating.

| Source | Renders to | Notes |
|---|---|---|
| `icon.svg` | `assets/icon.png` | Full mascot on a warm gradient, 1024x1024. Used directly (iOS, web, store listing) — no safe-zone constraint. |
| `foreground.svg` | `assets/android-icon-foreground.png` | Mascot only, transparent background, scaled to fit Android's adaptive-icon safe zone (viewBox is 108 units representing the 108dp adaptive-icon canvas; mascot scaled to ~62% so it clears any launcher mask shape). |
| `background.svg` | `assets/android-icon-background.png` | Same gradient as `icon.svg`, no mascot — composited behind `foreground.svg` by Android. |
| `monochrome.svg` | `assets/android-icon-monochrome.png` | Solid white silhouette (pot + leafy body only, no face/color detail) on transparent — used for Android 13+ themed icons, which the OS tints to a single color. |
| `splash.svg` | `assets/splash-icon.png` | Mascot on transparent, shown via `Theme.Tymed.Splash` in `android/app/src/main/res/values/styles.xml` (which also sets the cream background color separately). |
| `favicon.svg` | `assets/favicon.png` | Small web favicon; only asset with a hard-edged (not gradient) background, since it renders small. |

## Regenerating

Needs `cairosvg` (`pip3 install --user cairosvg`) and the native `cairo` library
(`brew install cairo`) for `DYLD_FALLBACK_LIBRARY_PATH` to find it — macOS's built-in `qlmanage
-t` can render SVG to PNG too but flattens transparency to opaque white, which silently breaks
the adaptive-icon layers (foreground/monochrome must stay transparent outside the mascot shape).

```bash
export DYLD_FALLBACK_LIBRARY_PATH=/opt/homebrew/lib
python3 -c "
import cairosvg
specs = [
    ('icon.svg', '../../assets/icon.png', 1024, 1024),
    ('foreground.svg', '../../assets/android-icon-foreground.png', 1024, 1024),
    ('background.svg', '../../assets/android-icon-background.png', 1024, 1024),
    ('monochrome.svg', '../../assets/android-icon-monochrome.png', 1024, 1024),
    ('splash.svg', '../../assets/splash-icon.png', 600, 600),
    ('favicon.svg', '../../assets/favicon.png', 256, 256),
]
for src, out, w, h in specs:
    cairosvg.svg2png(url=src, write_to=out, output_width=w, output_height=h)
"
```

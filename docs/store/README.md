# Store listing sources

The store listing lives in [`fastlane/metadata/android`](../../fastlane/metadata/android) in the standard fastlane layout, which F-Droid also reads:

- `<locale>/title.txt`, `short_description.txt`, `full_description.txt`: listing texts for `es-ES` (default), `en-US` and `ca`.
- `<locale>/changelogs/<versionCode>.txt`: release notes for each version, up to 500 characters.
- `<locale>/images/`: `featureGraphic.png` and `phoneScreenshots/`. The app icon is in `en-US/images/icon.png` and is used for every locale.

Check the length limits (title 30, short description 80, full description 4000, release notes 500 characters) with:

```sh
python3 scripts/check_listing.py
```

This folder holds the sources for the images:

| File | Output | Play requirement |
| --- | --- | --- |
| `play-icon.svg` | `en-US/images/icon.png` | 512 × 512 px, 32-bit PNG, square without rounded corners |
| `feature-graphic-{es,en,ca}.svg` | `<locale>/images/featureGraphic.png` | 1024 × 500 px, 24-bit PNG or JPEG without transparency |
| `raw-screenshots/{es,en,ca}/` | `<locale>/images/phoneScreenshots/` | Long side at most twice the short one |

The artwork derives from the app icon in [`docs/branding/icon.svg`](../branding/icon.svg). The 512 px icon shows the same visible area as the adaptive launcher icon (the central 72 dp of the 108 dp canvas).

The raw screenshots are 1080 × 2400 phone captures with sample data in each language and the status bar demo mode. Play does not accept them as they are, so `scripts/frame_play_screenshots.py` scales each one onto a 1080 × 1920 canvas with a caption. The captions are in `CAPTIONS` in the script. Screenshots, in order:

1. `01-notificacion.png`: the grouped notification when leaving the car.
2. `02-lista.png`: reminders for each car.
3. `03-avisos.png`: open alerts.
4. `04-editor.png`: the reminder editor.
5. `05-estado.png`: the Status tab.
6. `06-ajustes.png`: Settings.

## Regenerate

```sh
M=fastlane/metadata/android
rsvg-convert docs/store/play-icon.svg -w 512 -h 512 -o $M/en-US/images/icon.png
rsvg-convert docs/store/feature-graphic-es.svg -o $M/es-ES/images/featureGraphic.png   # also en → en-US, ca → ca
python3 scripts/frame_play_screenshots.py es                                           # also en, ca
```

Then remove the alpha channel from the feature graphics (for example with Pillow: `Image.open(f).convert("RGB").save(f)`) and keep the icon as RGBA.

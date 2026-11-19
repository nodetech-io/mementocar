# Memento Car icon

The icon is an instant photo, tilted −7°, with the front of a car in the picture and an orange handwritten line (`#F2994A`) on the bottom margin. The idea comes from *Memento*, where memory relies on photos and handwritten notes: the app is the note you leave yourself in the car. The frame is white, the photo uses dark teal `#0A4B51` and the background uses the theme's `#0F7882`. There is no text, shadow, texture or gradient.

Modified builds must use a different name and icon; see [Forks](../../README.md#forks).

## Resources

- `app/src/main/res/drawable/memento_car_icon_foreground.xml`: vector artwork on a transparent background.
- `app/src/main/res/drawable/memento_car_icon_background.xml`: opaque vector background.
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`: frame, car and handwritten line as one white path, with the photo and line cut out, for Android themed icons.
- `app/src/main/res/drawable/ic_notification_reminder.xml`: 24 dp version for notifications.
- `app/src/main/res/drawable/ic_notification_car.xml`: 24 dp car used for vehicles in the UI and for the "New reminder" Quick Settings tile.
- [icon.svg](icon.svg) and [icon.png](icon.png) (432 × 432 px): previews of the composition. They are not Android resources.

The adaptive icon (normal and round) uses the foreground and background layers. The photo is scaled to 0.95 and centred, so its corners stay inside the 66 dp safe circle of the 108 dp canvas and no launcher mask crops it. The car's openings (windscreen, lights and grille) use reversed winding paths (`nonZero`), so the same geometry works for the monochrome layer.

## Notifications

Android draws small notification icons in a single colour, so the orange line is kept as a cut-out on the white margin, as in the monochrome launcher icon. The photo is larger in proportion and the car has no grille, so it stays readable in the status bar. Notifications use the app's teal as accent with the system's native layout.

## Regenerate the preview

```sh
rsvg-convert docs/branding/icon.svg -o docs/branding/icon.png
```

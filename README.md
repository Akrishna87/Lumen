# 🌅 Lumen for Android

A photo gallery for Android designed the way Apple designs Photos for iPhone:
iOS colours, layouts, controls and gestures, set in Inter, an open-source typeface
designed along the same lines as Apple's San Francisco. It shows the photos and
videos **already on your phone**, straight from storage. There's nothing to import,
and no copies, uploads, ads or account.

## Installing it

1. On your Android phone, open the
   [latest build](https://github.com/Akrishna87/lumen/releases/tag/lumen-latest)
   and tap **Lumen.apk** to download it.
2. Open the downloaded file. Android will ask you to allow installing apps from
   your browser (or Files app). Allow it, then tap **Install**. Google Play Protect
   may warn that it doesn't recognise the app, because it isn't from the Play Store.
   Tap **More details → Install anyway**.
3. Open **Lumen**, tap **Continue** and allow access to photos and videos.

To update, install a newer `Lumen.apk` the same way. It installs over the old one
and keeps your favourites.

## Features

- **Library**: every photo and video in one grid, oldest at the top and newest at
  the bottom, where it opens. The dates on screen show in bold at the top. Pinch to
  switch between 3, 5 and 7 photos across. Videos show their length and favourites
  a heart. A count sits under the last row.
  - **Years** and **Months**: big cards with a key photo, a strip of more photos
    and the count. Tap a year to see its months, and a month to jump to it in
    All Photos.
- **Filter**: the button next to **Select** shows only **Favorites**, **Photos**,
  **Videos**, **Edited** (copies saved by Lumen's editor) or **Screenshots**. The
  filter's name shows under the date; tap it to see everything again. Swiping in the
  viewer stays within the filter.
- **Undo**: after hiding or unhiding, favouriting several photos, or adding to or
  removing from an album, a banner offers **Undo** for a few seconds. Deleting has no
  Undo banner: deleted photos go to Recently Deleted, where they can be recovered.
- **Select**: tap **Select**, then tap photos, or drag your finger sideways across
  the grid to pick a whole run of them (it scrolls when you reach the top or bottom).
  Share, favourite or delete them from the toolbar.
- **Press and hold** a photo for a larger preview over the blurred app, with a menu:
  **Copy**, **Share**, **Favorite**, **Add to Album**, **Hide** and **Delete**. Tap the
  preview to open the photo.
- **Your own albums**: tap **+** on Albums to make one, then add photos from **•••**
  (in Select mode, in the viewer, or in the press-and-hold menu) → **Add to Album**.
  Like albums on iPhone they're lists inside Lumen, so no photo is copied. An album's
  **•••** renames or deletes it (deleting an album never deletes its photos), and
  **••• → Remove from Album** takes photos out.
- **Duplicates** (under Utilities): photos that are exact copies of each other (same
  size and same picture). **Merge** keeps the oldest, gives it any heart and albums the
  others had, and moves the rest to Recently Deleted (Android asks first). Near-copies,
  like a resized or re-saved photo, aren't found.
- **Hidden**: **••• → Hide** takes photos out of Lumen's Library, albums and Search
  into the **Hidden** album under Utilities, which opens only after your fingerprint,
  face or screen lock (on a phone with no screen lock it just opens). It locks again
  when you leave it or Lumen goes to the background. Hidden photos stay where they are
  on the phone, so **other apps (Google Photos, Files…) still show them**; this only
  hides them inside Lumen. They're also left out of Recently Deleted, which isn't
  locked.
- **The viewer**: a photo grows out of its thumbnail when you open it, and shrinks back
  into its place in the grid when you close it (the grid scrolls to the photo you
  swiped to). Swipe between photos, pinch or double-tap to zoom, and drag down
  to close. Tap once to hide the bars; the background turns black, as in Photos.
  The title shows when it was taken ("Today", "Yesterday", "Monday", "March 3").
  A filmstrip of thumbnails sits above the toolbar: **Share**, **Favorite**,
  **Info** and **Delete**.
  - **Edit** (photos only, not GIFs, Recently Deleted or Hidden) opens Photos'
    editor on black: **Adjust** (brightness, contrast, saturation, warmth, on a
    ruler you slide), **Filters** (Vivid, Vivid Warm, Vivid Cool, Dramatic, Mono,
    Silvertone, Noir) and **Crop** (rotate, straighten, and Square, 4:3 or 16:9,
    centred; no free-form crop handles). **Done** saves a **new copy** named
    `…_edited.jpg` next to the original (or in Pictures/Lumen) with the original's
    date and camera details, then shows it. The original is never changed. The copy
    isn't added to your albums and doesn't keep the original's heart.
  - **Info** shows the date, the camera, lens, file name, megapixels, resolution,
    size and format, and the exposure (ISO, focal length, ev, aperture, shutter
    speed) read from the photo's EXIF data.
  - **Videos** play in place with a scrubber, play/pause and a mute button.
- **For You**: **Memories**, made from your photos: **On This Day** (this date in
  years past) and each recent month with at least six photos. A memory plays as a
  full-screen **slideshow**, each photo slowly zooming and cross-fading to the next.
  Tap it to pause, close, or **Add Music**: pick a song from your phone, and Lumen
  remembers it for later slideshows.
- **Places**: under Albums, where photos were taken, from the location the camera
  saved in them. **Map** shows each place as a photo with a count on an
  [OpenStreetMap](https://www.openstreetmap.org/copyright) map; **Grid** lists the
  same places. Places are labelled by coordinates: Lumen doesn't look up city names,
  since that would send your photos' locations to an online service. The map tiles
  are the only thing Lumen downloads.
- **Albums**: **My Albums** (Recents, Favorites, your own albums, then each folder like Camera,
  Screenshots or WhatsApp Images) as two rows of covers that scroll sideways, with
  **See All**. **Media Types** has Videos, Screenshots, Panoramas and Animated
  (GIFs). On Android 11+, **Recently Deleted** shows deleted items and the days left
  before they're removed for good, with **Recover** and **Delete**.
- **Search**: albums, months ("October", "2025"), file names, and **words inside
  photos** (a receipt, a sign, a screenshot), with suggestions for albums and recent
  months before you type. Lumen reads the text in your photos itself, entirely on the
  phone with [Tesseract](https://github.com/tesseract-ocr/tesseract), so nothing is
  sent anywhere. It reads each photo once, newest first, while Lumen is open (Search
  shows how far it has got); on a big library the first pass takes a while.
- **Live Text**: on a photo with text, the text button in the viewer outlines each
  line. Tap the lines you want (tap again to unpick); they're shown at the bottom, and
  **Copy** copies just those. With none picked, **Copy All** copies everything. Works best on screenshots and
  documents; text in ordinary photos (signs, handwriting) is hit and miss.
- **Limited access** (Android 14+): if you let Lumen see only some photos, Albums
  says so and **Manage…** lets you change the selection.
- Light and dark mode follow the phone's setting.

Deleting a photo moves it to Android's trash (Android 11+), where it stays for 30
days. Android asks you to confirm, as it does for every app. On older versions
Lumen asks, then deletes it. Favourites are kept in the app, so marking one is
instant. Requires Android 8.0 or newer.

## How it's built

- Kotlin and Jetpack Compose. The iOS look comes from the app's own components
  rather than Material: navigation bars with large titles, tab bar, segmented
  control, search field, inset lists, action sheets and capsule buttons
  (`ui/Components.kt`), and line icons in the style of SF Symbols (`ui/AppIcons.kt`).
- Colours are Apple's iOS system palette for light and dark mode, and text uses the
  iOS text styles (sizes, leading and weights from Apple's Human Interface
  Guidelines) in [Inter](https://rsms.me/inter/) (`ui/Theme.kt`). Inter is licensed
  under the SIL Open Font License 1.1; see `third_party/Inter-LICENSE.txt`. Apple's
  own San Francisco fonts may only be used in apps for Apple's platforms, so they
  aren't bundled.
- Photos and videos come from Android's MediaStore (`Media.kt`). Grid thumbnails are
  the media library's own, with a memory cache (`Thumbnails.kt`). Full-size photos,
  GIFs and video frames load with [Coil](https://coil-kt.github.io/coil/), and videos
  play with [Media3](https://developer.android.com/media/media3) ExoPlayer.
- Camera details are read with AndroidX ExifInterface.
- Text in photos is read with [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android)
  (Apache 2.0) and Tesseract's English model (`tessdata_fast`, Apache 2.0), which CI
  downloads at a pinned version and checks before building.

| File | What's in it |
| --- | --- |
| `Media.kt` | Photo/video model, the MediaStore scan (and trash), albums, months, formatting |
| `GalleryViewModel.kt` | Library state, permission level, refresh on changes, delete/recover requests |
| `Favorites.kt` | Remembers which photos have a heart |
| `Collections.kt` | Your own albums and the hidden photos, kept in the app |
| `HiddenLock.kt` | The fingerprint / face / screen-lock check for Hidden |
| `Thumbnails.kt` | Grid thumbnails from the media library, with a memory cache |
| `ui/App.kt` | Welcome screen, tab bar and navigation, share/delete actions, selection toolbar |
| `ui/LibraryScreen.kt` | Library: All Photos, Months and Years |
| `ui/AlbumsScreen.kt` | Albums, See All, an album's photos, Recently Deleted |
| `ui/SearchScreen.kt` | Search and its suggestions |
| `ui/Viewer.kt` | Full-screen viewer: zoom, swipe to close, video, filmstrip, Info |
| `ui/PhotoGrid.kt` | The photo grid, thumbnails, pinch to resize, selection, drag to select |
| `ui/Preview.kt` | The press-and-hold preview and its menu |
| `Editing.kt` | The editor's filters and adjustments, and saving the edited copy |
| `ui/Editor.kt` | The editor screen |
| `Memories.kt` | Builds the For You memories |
| `ui/ForYouScreen.kt`, `ui/Slideshow.kt` | For You, and the memory slideshow |
| `Places.kt` | Reads photo locations (cached) and groups them into places |
| `ui/PlacesScreen.kt` | Places: the map and the list |
| `Duplicates.kt`, `ui/DuplicatesScreen.kt` | Finding and merging duplicates |
| `TextReader.kt` | Reads text in photos (Tesseract) and keeps the words found for search |
| `ui/AlbumSheets.kt` | Add to Album, and the New / Rename Album dialog |
| `ui/Components.kt` | iOS-style controls |
| `ui/Theme.kt` | iOS colours and text styles in Inter |
| `ui/AppIcons.kt` | SF Symbols-style icons |

## Building

GitHub Actions builds it (`.github/workflows/lumen-apk.yml`) on every push:

1. **build**: `./gradlew assembleRelease` (needs JDK 17 and the Android SDK).
2. **smoke-test**: makes test photos (with EXIF camera details), screenshots, a
   panorama and a video, puts them on an Android 14 emulator, and checks the welcome
   screen, the Library, Months and Years, selecting (by tapping and by dragging
   across a row), the press-and-hold preview, the viewer (favourite, Info, a video
   playing, deleting), Albums, Favorites, making an album and adding to it, Library filters,
   hiding (and Undo) and unhiding, Places (map and list), For You and a slideshow (that it moves, pauses and closes), editing (it checks the saved copy's pixels: turned, black and
   white, dated like the original), Search and dark mode. Then it sets a PIN and checks Hidden won't
   open without it, and finally copies a photo and checks Duplicates finds and merges it. Screenshots are saved
   with the build.
3. **publish**: replaces the `lumen-latest` release with the new APK and the
   screenshots.

To build locally instead, open this repository in Android Studio, or run
`./gradlew assembleRelease` with `ANDROID_HOME` pointing at an Android SDK.

### About the signing key

`signing/sideload.keystore` (password `lumengallery`) is committed on purpose. It
lets every build install as an update over the last one, which matters for a
sideloaded app. Since it's public, it doesn't prove who built the APK. Only install
builds from this repository's releases. If you ever publish the app more widely,
make a private key and supply it through the `SIGNING_KEYSTORE`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` environment
variables (for example from GitHub secrets). `app/build.gradle.kts` uses them when
they're set.

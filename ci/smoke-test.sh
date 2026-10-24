#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the APK, puts test photos and a video on the
# "phone", and checks the welcome screen, the Library (All Photos, Months, Years), the viewer
# (favourite, info, video playback, deleting), press-and-hold previews, drag-selecting, Albums,
# your own albums, hiding (and the Hidden album's lock), editing a copy, Search (including text
# inside photos, and Live Text) and dark mode.
# Usage: smoke-test.sh <apk> <media dir> <output dir>
set -euo pipefail

APK="$1"
MEDIA="$2"
OUT="$3"
PKG=io.github.akrishna87.lumen
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o '\(text\|content-desc\)="[^"]\+"' "$OUT/failure.xml" | head -60 || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  echo "App log:"
  grep -E "AndroidRuntime|FATAL|lumen|ExoPlayer.*(rror|ail)" "$OUT/logcat.txt" | tail -60 || true
  exit 1
}
dump() {
  local i
  for i in 1 2 3 4 5 6; do
    rm -f "$OUT/$1.xml"
    # Remove the last reading first: when uiautomator gives up it leaves the old file behind,
    # which would describe the wrong screen.
    adb shell rm -f /sdcard/ui.xml
    local said
    said=$(adb shell uiautomator dump /sdcard/ui.xml 2>&1 || true)
    if ! echo "$said" | grep -q "dumped to" || ! adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null 2>&1 \
      || ! grep -q '<hierarchy' "$OUT/$1.xml"; then
      echo "(couldn't read the screen: ${said//$'\n'/ }; trying again)"
      rm -f "$OUT/$1.xml"
      sleep 1
      continue
    fi
    # The emulator's own apps sometimes freeze while it warms up; wave the popup away.
    if grep -q "isn&apos;t responding\|isn't responding" "$OUT/$1.xml"; then
      echo "(dismissing a system 'isn't responding' popup)"
      python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait" > /dev/null 2>&1 && adb shell input tap $(python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait")
      sleep 3
      continue
    fi
    return 0
  done
  [ -f "$OUT/$1.xml" ] || fail "couldn't read what's on screen ($1)"
}
# Android blanks screenshots of secure screens (like its PIN prompt); drop those empty files.
shot() { adb exec-out screencap -p > "$OUT/$1.png"; [ -s "$OUT/$1.png" ] || rm -f "$OUT/$1.png"; }
tap() { # tap <dump name> <text> [first|last]
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" "${3:-first}") || fail "couldn't find '$2' on screen"
  adb shell input tap $xy
}
has() { grep -qi "$2" "$OUT/$1.xml"; } # has <dump name> <grep pattern>
crashed() { adb logcat -d | grep -A3 "FATAL EXCEPTION" | grep -q "$PKG"; }
# True when a screenshot's status bar has bright pixels: on a black or photo background its icons
# must be white, or they can't be seen.
status_icons_visible() {
  python3 - "$1" <<'PY'
import sys
from PIL import Image
image = Image.open(sys.argv[1]).convert("L")
w, h = image.size
brightest = max(image.crop((0, 0, w, int(h * 0.03))).getdata())
print(f"brightest status bar pixel in {sys.argv[1].rsplit('/', 1)[-1]}: {brightest}")
sys.exit(0 if brightest > 200 else 1)
PY
}

# Goes to the Albums tab's main screen. The tab remembers the album you were in, as on iOS, so
# back out of it if needed.
open_albums() {
  dump nav
  tap nav "Albums" last
  sleep 2
  dump nav
  if has nav 'content-desc="Back"'; then tap nav "Back"; sleep 2; fi
}

adb wait-for-device
adb install -r "$APK"

echo "--- Copying test photos and a video onto the emulator"
adb shell mkdir -p /sdcard/DCIM/Camera /sdcard/Pictures/Screenshots /sdcard/Pictures/Panoramas
for f in "$MEDIA"/camera/*; do adb push "$f" /sdcard/DCIM/Camera/ > /dev/null; done
for f in "$MEDIA"/screenshots/*; do adb push "$f" /sdcard/Pictures/Screenshots/ > /dev/null; done
for f in "$MEDIA"/panoramas/*; do adb push "$f" /sdcard/Pictures/Panoramas/ > /dev/null; done
adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null 2>&1 || true
for _ in $(seq 1 30); do
  adb shell content query --uri content://media/external/file --projection _display_name | grep -q "Smoke Clip" && break
  sleep 2
done
adb shell content query --uri content://media/external/file --projection _id:_display_name:media_type:relative_path \
  --where "media_type IN (1,3)" || true

echo "--- The welcome screen, before access is given"
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity"
sleep 5
dump welcome
shot 01-welcome
has welcome 'text="Welcome to Lumen"' || fail "the welcome screen didn't show"
has welcome 'text="Continue"' || fail "the welcome screen has no Continue button"
echo "PASS: asks for access with a welcome screen"

echo "--- The Library"
adb shell am force-stop "$PKG"
adb shell pm grant "$PKG" android.permission.READ_MEDIA_IMAGES
adb shell pm grant "$PKG" android.permission.READ_MEDIA_VIDEO
adb shell pm grant "$PKG" android.permission.ACCESS_MEDIA_LOCATION # for Places
adb shell am start -W -n "$PKG/.MainActivity"
sleep 6
dump library
shot 02-library
has library 'content-desc="Photo, ' || fail "the Library doesn't show the test photos"
has library 'text="All Photos"' || fail "the Years/Months/All Photos control is missing"
has library '[0-9] Photos, 1 Video' || fail "the Library doesn't count its photos and video"
has library 'text="Select"' || fail "the Select button is missing"
echo "PASS: the Library lists the photos with a count"

echo "--- Months and Years"
tap library "Months"
sleep 2
dump months
shot 03-months
has months "text=\"$(date +%B) $(date +%Y)\"" || fail "Months doesn't show $(date +%B) $(date +%Y)"
tap months "Years"
sleep 2
dump years
shot 04-years
has years "text=\"$(date +%Y)\"" || fail "Years doesn't show $(date +%Y)"
tap years "$(date +%Y)"
sleep 2
dump years-tapped
has years-tapped 'text="Months"' || fail "tapping a year didn't open Months"
tap years-tapped "All Photos"
sleep 2
echo "PASS: Months and Years show the photos' dates"

echo "--- Selecting"
dump library
tap library "Select"
sleep 1
dump select
tap select "Photo, " last
sleep 1
dump select
has select 'text="1 Photo Selected"' || fail "selecting a photo didn't say '1 Photo Selected'"
# Drag sideways across a row to pick it all at once.
ROW=$(python3 "$HERE/row_swipe.py" "$OUT/select.xml") || fail "couldn't find a row of photos to drag across"
adb shell input swipe $ROW 800
sleep 1
dump select
shot 05-select
has select 'text="[2-9] \(Photos\|Items\) Selected"' || fail "dragging across a row didn't select its photos"
tap select "Cancel"
sleep 1
echo "PASS: selecting photos, by tapping and by dragging across them"

echo "--- Press and hold"
dump library
XY=$(python3 "$HERE/find_text.py" "$OUT/library.xml" "Photo, " last) || fail "no photo to press"
adb shell input swipe $XY $XY 900
sleep 2
dump preview
shot 05b-preview
has preview 'text="Copy"' || fail "pressing and holding a photo didn't show the menu"
has preview 'text="Share"' || fail "the preview menu has no Share"
has preview 'content-desc="Preview, Photo, ' || fail "pressing and holding didn't show a preview of the photo"
adb shell input keyevent KEYCODE_BACK
sleep 1
dump library
has library 'text="Copy"' && fail "Back didn't close the preview"
echo "PASS: press and hold shows a preview with a menu"

echo "--- The viewer"
dump library
tap library "Photo, " last
sleep 3
dump viewer
shot 06-viewer
has viewer 'content-desc="Favorite"' || fail "the viewer has no Favorite button"
has viewer 'content-desc="Info"' || fail "the viewer has no Info button"
has viewer 'text="Today"' || fail "the viewer's title isn't the photo's date (Today)"
# Tapping the photo hides the bars and turns the background black; the status bar icons turn white.
adb shell input tap 540 1200
sleep 2
shot 06b-viewer-bars-hidden
status_icons_visible "$OUT/06b-viewer-bars-hidden.png" || fail "with the viewer's bars hidden, the status bar icons can't be seen"
adb shell input tap 540 1200
sleep 2
tap viewer "Favorite"
sleep 1
dump viewer
has viewer 'content-desc="Unfavorite"' || fail "tapping the heart didn't favourite the photo"
tap viewer "Info"
sleep 2
dump info
shot 07-info
has info 'text="Album"' || fail "Info doesn't show the album"
has info 'text="Camera"' || fail "Info doesn't say the photo is in Camera"
has info 'Pixel Smoke' || fail "Info doesn't show the camera from the photo's EXIF data"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2
dump library
has library 'text="All Photos"' || fail "Back didn't close the viewer"
echo "PASS: the viewer opens a photo, favourites it and shows its info"

echo "--- Albums"
tap library "Albums"
sleep 2
dump albums
shot 08-albums
has albums 'text="My Albums"' || fail "Albums doesn't show My Albums"
for name in Recents Favorites Camera Screenshots Panoramas Videos; do
  has albums "\"$name" || fail "Albums doesn't list $name"
done
tap albums "Favorites"
sleep 2
dump favorites
shot 09-favorites
has favorites 'text="1 Photo"' || fail "Favorites doesn't hold the photo that was favourited"
tap favorites "Back"
sleep 2
dump albums
tap albums "Videos" last
sleep 2
dump videos
has videos 'content-desc="Video, ' || fail "the Videos album doesn't show the test video"
echo "PASS: albums for folders, Favorites and media types"

echo "--- Playing a video"
adb logcat -c
tap videos "Video, "
sleep 5
shot 10-video
adb logcat -d > "$OUT/video-log.txt"
grep -q "ExoPlayerImpl: Init" "$OUT/video-log.txt" || fail "the viewer didn't start the video player"
if grep -q "ExoPlaybackException" "$OUT/video-log.txt"; then fail "the video failed to play"; fi
adb shell input keyevent KEYCODE_BACK
sleep 2
echo "PASS: videos play in the viewer"

echo "--- Search"
dump videos
tap videos "Search"
sleep 2
dump search
tap search "Albums, Months, Files and More"
sleep 1
adb shell input text "Screen"
sleep 2
adb shell input keyevent KEYCODE_BACK # hide the keyboard
sleep 1
dump search
shot 11-search
has search 'text="Screenshots"' || fail "searching 'Screen' didn't suggest the Screenshots album"
has search '[0-9] Results' || fail "searching 'Screen' didn't find the screenshots"
echo "PASS: search finds albums and photos"

echo "--- For You"
dump search
tap search "For You"
sleep 3
dump foryou
shot 26-for-you
MONTH="$(date +%B) $(date +%Y)"
has foryou 'content-desc="Memory, On This Day"' || fail "For You has no On This Day memory (a test photo is from this day last year)"
has foryou "content-desc=\"Memory, $MONTH\"" || fail "For You has no memory for $MONTH"
tap foryou "Memory, $MONTH"
# The slideshow never stops moving, so read it from screenshots, not the screen tree.
sleep 2
shot 27-slideshow-a
sleep 4
shot 27-slideshow-b
status_icons_visible "$OUT/27-slideshow-b.png" || fail "the slideshow's status bar icons can't be seen"
python3 - "$OUT/27-slideshow-a.png" "$OUT/27-slideshow-b.png" <<'PY' || fail "the slideshow isn't moving"
import sys
from PIL import Image, ImageChops, ImageStat
a, b = (Image.open(p).convert("L") for p in sys.argv[1:3])
diff = ImageStat.Stat(ImageChops.difference(a, b)).mean[0]
print(f"slideshow frames differ by {diff:.1f}")
sys.exit(0 if diff > 2 else 1)
PY
adb shell input tap 540 1200 # pause and show the controls
sleep 2
dump slideshow
shot 28-slideshow-controls
has slideshow 'content-desc="Play"' || fail "tapping the slideshow didn't pause it"
has slideshow 'text="Add Music"' || fail "the slideshow has no Add Music button"
tap slideshow "Close"
sleep 2
dump foryou
has foryou 'text="Memories"' || fail "closing the slideshow didn't go back to For You"
echo "PASS: For You shows memories (including On This Day), and they play as slideshows"

echo "--- Places"
open_albums
dump albums
tap albums "Places" last # the row, not the section heading above it
sleep 8
dump places
shot 29-places-map
has places 'content-desc="Map of 2 places"' || fail "the Places map doesn't show the test photos' two places"
# The map must stay below the title bar: the bar's spot (just under the status bar) is light grey/white.
python3 - "$OUT/29-places-map.png" <<'PY' || fail "the map covers the Places title bar"
import sys
from PIL import Image
image = Image.open(sys.argv[1]).convert("RGB")
w, h = image.size
pixel = image.getpixel((w // 2, int(h * 0.055)))
print("title bar pixel", pixel)
sys.exit(0 if min(pixel) > 225 else 1)
PY
tap places "Grid"
sleep 2
dump places
shot 30-places-grid
has places 'content-desc="Place, 3 Photos"' || fail "Places doesn't group the 3 Chennai photos"
has places 'content-desc="Place, 2 Photos"' || fail "Places doesn't group the 2 Paris photos"
tap places "Place, 3 Photos"
sleep 2
dump place
has place 'text="3 Photos"' || fail "a place doesn't open its photos"
tap place "Back"
sleep 1
dump places
tap places "Back"
sleep 1
echo "PASS: Places groups photos by where they were taken, on a map and in a list"
dump albums
tap albums "Search" # back where the dark-mode step expects to be
sleep 2

echo "--- Dark mode"
adb shell cmd uimode night yes || true
sleep 3
shot 12-dark-search
tap search "Library"
sleep 2
shot 13-dark-library
adb shell cmd uimode night no || true

echo "--- Your own albums"
sleep 2
open_albums
dump albums
tap albums "New Album"
sleep 2
adb shell input text Trip
sleep 1
dump naming
shot 16-new-album
tap naming "Save"
sleep 2
dump albums
has albums '"Trip' || fail "the new album isn't in My Albums"
tap albums "Library"
sleep 2
dump library
tap library "Select"
sleep 1
dump select
tap select "Photo, " last
sleep 1
dump select
tap select "More"
sleep 2
dump more
tap more "Add to Album"
sleep 2
dump picker
shot 17-add-to-album
tap picker "Trip"
sleep 2
open_albums
dump albums
tap albums "Trip"
sleep 2
dump trip
shot 18-trip
has trip 'text="1 Photo"' || fail "the photo added to Trip isn't in the album"
echo "PASS: making an album and adding a photo to it"

echo "--- Renaming, removing from and deleting an album"
tap trip "Album Options"
sleep 2
dump options
tap options "Rename Album"
sleep 2
adb shell input text Holiday # the old name is selected, so this replaces it
sleep 1
dump naming
tap naming "Save"
sleep 2
dump trip
has trip 'text="Holiday"' || fail "renaming the album didn't change its title"
tap trip "Select"
sleep 1
dump trip
tap trip "Photo, "
sleep 1
dump trip
tap trip "More"
sleep 2
dump more
tap more "Remove from Album"
sleep 2
dump trip
has trip 'text="No Photos or Videos"' || fail "Remove from Album left the photo in the album"
tap trip "Album Options"
sleep 2
dump options
tap options "Delete Album"
sleep 2
dump confirm
tap confirm "Delete Album"
sleep 2
dump albums
has albums 'text="My Albums"' || fail "deleting the album didn't go back to Albums"
has albums '"Holiday' && fail "the deleted album is still in My Albums"
echo "PASS: renaming an album, removing a photo from it, and deleting it (not its photos)"

echo "--- Hiding"
dump albums
tap albums "Library"
sleep 2
dump library
tap library "Select"
sleep 1
dump select
tap select "Photo, " last
sleep 1
dump select
tap select "More"
sleep 2
dump more
tap more "Hide"
sleep 2
dump library
has library '10 Photos, 1 Video' || fail "hiding a photo didn't take it out of the Library"
open_albums
adb shell input swipe 540 1700 540 500 300 # down to Utilities
sleep 1
dump albums
tap albums "Hidden"
sleep 2
dump hidden
shot 19-hidden
has hidden 'content-desc="Photo, ' || fail "the Hidden album doesn't show the hidden photo"
tap hidden "Select"
sleep 1
dump hidden
tap hidden "Photo, "
sleep 1
dump hidden
tap hidden "More"
sleep 2
dump more
tap more "Unhide"
sleep 2
dump hidden
has hidden 'text="No Hidden Items"' || fail "unhiding didn't take the photo out of Hidden"
tap hidden "Back"
sleep 1
dump albums
tap albums "Library"
sleep 2
dump library
has library '11 Photos, 1 Video' || fail "an unhidden photo didn't come back to the Library"
echo "PASS: hiding takes a photo out of the Library into Hidden, and unhiding brings it back"

echo "--- Deleting from the viewer"
sleep 2
dump library
tap library "Photo, " last
sleep 3
dump viewer
tap viewer "Info"
sleep 2
dump info
GONE=$(grep -o 'text="PXL_smoke_[0-9]*\.jpg"' "$OUT/info.xml" | head -1 | cut -d'"' -f2) || true
[ -n "$GONE" ] || fail "couldn't read which photo is open"
adb shell input keyevent KEYCODE_BACK
sleep 1
dump viewer
tap viewer "Delete"
sleep 2
dump allow
shot 14-delete-confirm
tap allow "Allow"
sleep 3
dump after-delete
shot 15-after-delete
has after-delete 'content-desc="Favorite"\|content-desc="Unfavorite"' || fail "the viewer closed after deleting instead of showing the next photo"
tap after-delete "Info"
sleep 2
dump info-next
grep -q "text=\"$GONE\"" "$OUT/info-next.xml" && fail "the deleted photo ($GONE) is still showing in the viewer"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2
dump library
has library '10 Photos, 1 Video' || fail "the Library still counts the deleted photo"
echo "PASS: deleting moves on to the next photo and leaves the library"

echo "--- Editing saves a copy"
dump library
tap library "Photo, " last
sleep 3
dump viewer
tap viewer "Info"
sleep 2
dump info
ORIG=$(grep -o 'text="PXL_smoke_[0-9]*\.jpg"' "$OUT/info.xml" | head -1 | cut -d'"' -f2) || true
[ -n "$ORIG" ] || fail "couldn't read which photo is open"
COPY="${ORIG%.jpg}_edited.jpg"
adb shell input keyevent KEYCODE_BACK
sleep 1
dump viewer
tap viewer "Edit"
sleep 3
dump editor
shot 23-editor
# The editor is black even in light mode, so its status bar icons must be white, not black on black.
status_icons_visible "$OUT/23-editor.png" || fail "the editor's status bar icons can't be seen"
tap editor "Filters"
sleep 3
dump editor
tap editor "Mono"
sleep 1
tap editor "Crop"
sleep 1
dump editor
tap editor "Rotate"
sleep 2
shot 24-edited
dump editor
tap editor "Done"
sleep 8
dump copy
shot 25-copy
tap copy "Info"
sleep 2
dump copyinfo
has copyinfo "text=\"$COPY\"" || fail "the viewer didn't switch to the edited copy ($COPY)"
has copyinfo 'Pixel Smoke' || fail "the edited copy lost the camera details"
adb shell content query --uri content://media/external/images/media \
  --projection _display_name:width:height:datetaken:date_modified:relative_path > "$OUT/media-after-edit.txt"
REL=$(grep "_display_name=$COPY," "$OUT/media-after-edit.txt" | grep -o 'relative_path=[^,]*' | cut -d= -f2) || true
adb pull "/sdcard/$REL$COPY" "$OUT/edited.jpg" > /dev/null || fail "couldn't fetch the edited copy from /sdcard/$REL$COPY"
python3 "$HERE/check_edit.py" "$OUT/media-after-edit.txt" "$ORIG" "$COPY" "$OUT/edited.jpg" || fail "the edited copy isn't right"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2
dump library
has library '11 Photos, 1 Video' || fail "the edited copy isn't in the Library"
echo "PASS: editing saves a turned, black-and-white copy with the original's date and camera, and opens it"

echo "--- With a screen lock, Hidden asks for it"
dump library
tap library "Select"
sleep 1
dump select
tap select "Photo, " last
sleep 1
dump select
tap select "More"
sleep 2
dump more
tap more "Hide"
sleep 2
adb shell locksettings set-pin 1111
sleep 1
open_albums
adb shell input swipe 540 1700 540 500 300
sleep 1
dump albums
tap albums "Hidden"
sleep 3
dump locked
shot 20-hidden-locked
has locked 'content-desc="Photo, ' && fail "Hidden showed its photos without asking for the screen lock"
adb shell input text 1111
adb shell input keyevent KEYCODE_ENTER
sleep 3
dump unlocked
shot 21-hidden-unlocked
has unlocked 'content-desc="Photo, ' || fail "entering the PIN didn't open Hidden"
# Going to the home screen and back must lock it again.
adb shell input keyevent KEYCODE_HOME
# Android tells an app it's in the background (onStop) only once the home screen has settled,
# which can take a few seconds on the emulator; wait for that before coming back.
for _ in $(seq 1 15); do
  adb shell dumpsys activity activities | grep -A12 "$PKG/.MainActivity" | grep -q "state=STOPPED" && break
  sleep 1
done
adb shell dumpsys activity activities | grep -A12 "$PKG/.MainActivity" | grep -o "state=[A-Z_]*" | head -1 || true
adb shell am start -W -n "$PKG/.MainActivity"
sleep 3
dump relocked
shot 22-hidden-relocked
has relocked 'content-desc="Photo, ' && fail "Hidden stayed open after Lumen went to the background"
adb shell locksettings clear --old 1111 || true
echo "PASS: with a screen lock, Hidden opens only after it, and locks again in the background"

echo "--- Duplicates"
adb push "$MEDIA/camera/PXL_smoke_03.jpg" "/sdcard/DCIM/Camera/PXL_smoke_03 copy.jpg" > /dev/null
adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null 2>&1 || true
sleep 4
open_albums
adb shell input swipe 540 1700 540 500 300 # down to Utilities
sleep 1
dump albums
tap albums "Duplicates"
sleep 5
dump duplicates
shot 31-duplicates
has duplicates 'text="2 Copies"' || fail "Duplicates didn't find the copied photo"
tap duplicates "Merge"
sleep 2
dump allow
tap allow "Allow"
sleep 4
dump duplicates
has duplicates 'text="No Duplicates"' || fail "merging didn't remove the copy"
echo "PASS: Duplicates finds an identical copy, and merging keeps one"

echo "--- Text in photos"
adb shell mkdir -p /sdcard/Pictures/Notes
adb push "$MEDIA/later/Note.png" /sdcard/Pictures/Notes/ > /dev/null
adb shell content call --uri content://media --method scan_volume --arg external_primary > /dev/null 2>&1 || true
sleep 3
dump nav
tap nav "Search" last
sleep 2
dump search
if has search 'content-desc="Clear text"'; then tap search "Clear text"; sleep 1; dump search; fi
tap search "Albums, Months, Files and More"
sleep 1
adb shell input text receipt
sleep 1
adb shell input keyevent KEYCODE_BACK # hide the keyboard
# Lumen reads text in the background; wait for it to reach the new photo.
for _ in $(seq 1 30); do
  sleep 3
  dump search
  has search '[0-9] Result' && break
done
shot 32-text-search
has search 'text="1 Result"' || fail "searching 'receipt' didn't find the photo with that word in it"
tap search "Photo, "
sleep 3
dump viewer
tap viewer "Live Text"
sleep 8
dump live
shot 33-live-text
has live 'content-desc="[^"]*receipt' || fail "Live Text didn't outline the photo's text"
tap live "Copy All"
dump copied # straight away: the note shows for 2.5 s
has copied 'text="Copied"' || fail "Copy All didn't copy the text"
echo "PASS: search finds words inside photos, and Live Text outlines and copies them"

if crashed; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"

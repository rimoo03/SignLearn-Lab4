# SignLearn 2.0 🤟

A Duolingo-style app for learning **American Sign Language (ASL)**, with a camera that checks your hand signs.

**Members:** Ang, Jitendra · Cuilan, Bryle · Galimba, Uriel · La, Thanh · Wandaga, Rolando

**SDGs:** SDG 4 (Quality Education) and SDG 10 (Reduced Inequalities). The app makes sign language easier to learn so more people can communicate with the Deaf community.

---

## Features

| # | Feature | Where |
|---|---------|-------|
| 1 | **Lesson path**: 5 units, 14 lessons (A–Z plus everyday words), unlocked one by one | Learn tab |
| 2 | **Lessons**: new-sign cards (tap to flip), "What does this mean?", "Which one is X?", match the pairs | `LessonActivity` |
| 3 | **Camera sign checking**: MediaPipe tracks 21 points on your hand and the app checks your handshape | `CameraActivity`, `SignClassifier` |
| 4 | **Rewards**: XP, levels, daily goal, 🔥 streak, ❤️ hearts, 12 badges, confetti, sounds, vibration | everywhere |
| 5 | **Practice games**: free practice, ⚡ 60-second Speed Round, ✍️ Spell a Word | Practice tab |
| 6 | **Sign Dictionary** with search | `DictionaryActivity` |
| 7 | **Profile**: stats, badges, daily reminder, sound toggle, share progress | Profile tab |

### Lab 4 requirements (intents, toast, notification)
- **Explicit intents with extras:** Welcome → Main, Main → Lesson (`lesson_id`), Lesson → LessonComplete (XP, accuracy, badges), Main → Camera (mode, letter, word).
- **Implicit intents:** *Share my progress* (`ACTION_SEND`) and *Learn more about ASL* (`ACTION_VIEW` to a website), both on the Profile tab.
- **PendingIntent:** tapping a notification opens the app; `AlarmManager` fires `ReminderReceiver` every day.
- **Toasts:** welcome message, streak/hearts/XP info, badges, +1 heart, and more.
- **Notifications:** a daily streak reminder (scheduled), *Send a test reminder* (instant), and *Badge unlocked*.
- **Manifest:** all 6 activities, the `ReminderReceiver`, plus the `CAMERA` and `POST_NOTIFICATIONS` permissions and `<queries>` for the implicit intents.

---

## Setup (do this once)

1. **Put the new code in your repo.** Make a new branch first so the Lab 4 version stays safe:
   `git checkout -b signlearn-v2`
   Then delete everything in the project folder **except the `.git` folder**, and copy in everything from this zip.
2. **Open the folder in Android Studio** and click **Sync Now** if it asks. The first sync downloads CameraX and MediaPipe, so it can take a few minutes.
3. **Hand-tracking model.** The first build downloads `hand_landmarker.task` (about 7.5 MB) into `app/src/main/assets/` by itself.
   If you see `SignLearn: could not download hand_landmarker.task` in the Build window, download it yourself from
   https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
   and put it at `app/src/main/assets/hand_landmarker.task` (create the `assets` folder if it doesn't exist).
4. **Run on a real phone.** Turn on USB debugging, plug the phone in, and press ▶ Run. The camera feature needs a real camera. The emulator works for everything else.
5. **Allow notifications and camera** when the app asks.

### If the build fails
- *"Could not find com.google.mediapipe:tasks-vision:0.10.32"* → in `gradle/libs.versions.toml`, change `mediapipe = "0.10.32"` to `"0.10.29"` (or `"0.10.21"`), then sync again.
- *"Could not find androidx.camera…:1.5.1"* → change `camerax = "1.5.1"` to `"1.4.2"`.
- Anything else: copy the **first** red error line from the Build window and send it over.

---

## Demo video checklist
1. Fresh start: type a name, pick a daily goal, **Let's go!** (confetti + toast).
2. Dashboard: top bar (🔥 streak, ⭐ XP, ❤️ hearts). Tap each one to show its toast.
3. Do lesson "Letters A–D": flip a card, answer one question wrong on purpose (heart lost, shake, red panel), match pairs, finish (celebration screen).
4. Show that the next lesson is now unlocked, and that tapping a 🔒 node shakes it.
5. Practice tab → Sign It! → sign **L**, **V** and **Y** to the camera. Long-press the title to show the debug numbers.
6. Speed Round for a few seconds, then Spell a Word.
7. Dictionary: search "hello" and open it.
8. Profile: badges, **Send a test reminder** (notification), **Share my progress** (implicit intent), **Learn more** (browser).
9. Explain the **known limitations** below (these are the semantic errors).

## Known limitations (semantic errors to explain)
- **Only 11 signs can be checked by the camera:** A, B, D, F, I, L, U, V, W, Y and 🤟 (I love you).
  - Moving signs (J, Z and most words) can't be judged from one still frame.
  - Letters that differ only by a small thumb position (A/S/E/M/N/T) look almost the same to the hand tracker.
- **The recognizer is rule-based**, not a trained AI model. It measures finger straightness, thumb position and the angle between fingers. Odd camera angles, dim light or a hand at the edge of the screen can cause wrong results, like a slightly bent V being read as U.
- **Hearts refill over time** using the phone clock, so changing the phone's time affects them.
- **Progress is saved only on the phone** (SharedPreferences), so there are no accounts or online sync.

If detection feels too strict or too loose, adjust the numbers at the top of `SignClassifier.kt`. Turning on the debug line (long-press the camera title) shows the values the recognizer sees.

## Project map
```
app/src/main/java/com/example/signlearn/
  WelcomeActivity        – profile setup (launcher)
  MainActivity           – dashboard: Learn / Practice / Profile tabs
  LessonActivity         – lesson exercises, hearts, feedback panel
  LessonCompleteActivity – celebration screen
  CameraActivity         – camera practice, speed round, spelling, camera checkpoints
  DictionaryActivity     – browse/search all signs
  Course                 – ALL lesson content (edit here to add signs/lessons)
  ProgressStore          – XP, levels, streak, hearts, badges (SharedPreferences)
  SignClassifier         – rules that turn hand points into letters
  HandTracker            – MediaPipe Hand Landmarker wrapper
  HandOverlayView        – draws the hand skeleton over the camera
  HandSignView/HandShapes– cartoon hand drawings (made by tools/hands.py)
  ConfettiView, Fx, Sfx  – confetti, animations, sound effects
  Notifier, ReminderReceiver – notifications + daily reminder
tools/hands.py           – script that designs the hand drawings
```

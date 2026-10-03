# Workout Tracker

A personal Android workout tracker: plan and schedule workouts, log weight, sets and reps, and see progress charts.

Kotlin, Jetpack Compose and Material 3, with a Room database stored on the phone. Android Auto Backup copies the database to your Google account.

## Install on your phone

Every push to `main` builds the app and attaches it to the **Latest build** release. On your phone, open
https://github.com/borgesw14/workout-tracker/releases/latest, tap `workout-tracker.apk`, and allow installs from your browser when Android asks. New builds install over the old one and keep your data.

The APK is signed with the debug key in `app/signing/`, which is committed on purpose so every build shares one key. Only install APKs from this repo's releases.

## Build locally

Open the folder in Android Studio, or run `./gradlew assembleDebug` with the Android SDK installed.

## Roadmap

1. App setup, data model, exercise library (done)
2. Workout templates and a schedule calendar with reminders (done)
3. Logging screen with rest timer, prefilled from last time
4. Analytics: weight over time, estimated 1RM, weekly volume, planned vs completed

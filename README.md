# Workout Tracker

A personal Android workout tracker: plan and schedule workouts, log weight, sets and reps, and see progress charts.

Kotlin, Jetpack Compose and Material 3, with a Room database stored on the phone. Android Auto Backup copies the database to your Google account.

## Install on your phone

Every push builds a debug APK in GitHub Actions. Open the latest **Build APK** run, download the `workout-tracker-debug-apk` artifact, unzip it, and open `app-debug.apk` on your phone (allow installs from that source when Android asks).

## Build locally

Open the folder in Android Studio, or run `./gradlew assembleDebug` with the Android SDK installed.

## Roadmap

1. App setup, data model, exercise library (done)
2. Workout templates and a schedule calendar with reminders
3. Logging screen with rest timer, prefilled from last time
4. Analytics: weight over time, estimated 1RM, weekly volume, planned vs completed

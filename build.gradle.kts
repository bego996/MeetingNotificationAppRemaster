// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.1" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
    id("com.google.gms.google-services") version "4.4.3" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("com.google.firebase.firebase-perf") version "2.0.1" apply false
}

buildscript {
    extra.apply {
        set("room_version", "2.6.1")
    }
}





tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
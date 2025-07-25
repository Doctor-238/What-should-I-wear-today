// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.23" apply true
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.7" apply false
}
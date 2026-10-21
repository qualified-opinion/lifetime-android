// Top-level build file. Plugin versions live in gradle/libs.versions.toml.
buildscript {
    dependencies {
        // AGP supplies Kotlin support; select the same KGP version as the Compose compiler.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

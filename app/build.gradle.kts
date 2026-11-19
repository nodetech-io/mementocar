// SPDX-FileCopyrightText: 2026 Prodlabs22, S.L. (Nodetech)
// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.variant.HasUnitTestBuilder
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val releaseSigningFile = rootProject.file("release-signing.properties")
val hasReleaseSigning = releaseSigningFile.isFile
val releaseSigning = Properties().apply {
    if (hasReleaseSigning) {
        releaseSigningFile.inputStream().use(::load)
    }
}

fun releaseSigningProperty(name: String): String {
    return releaseSigning.getProperty(name)
        ?: error("Missing '$name' in ${releaseSigningFile.name}")
}

android {
    namespace = "io.nodetech.mementocar"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.nodetech.mementocar"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseSigningProperty("storeFile"))
                storePassword = releaseSigningProperty("storePassword")
                keyAlias = releaseSigningProperty("keyAlias")
                keyPassword = releaseSigningProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a development build sit next to the Play version on the same device.
            applicationIdSuffix = ".debug"
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    dependenciesInfo {
        // The dependency metadata block is encrypted for Google Play only; keep it out of APKs
        // (F-Droid flags it) while Play still receives it in the bundle.
        includeInApk = false
        includeInBundle = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

// Keep the production variant covered as well as AGP's default debug host tests.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as HasUnitTestBuilder).enableUnitTest = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.concurrent.futures)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

val localProps = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) { localFile.inputStream().use(localProps::load) }

fun prop(key: String): String? = System.getenv(key) ?: localProps.getProperty(key)

android {
    namespace = "com.carsondavis.notetaker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.carsondavis.notetaker"
        minSdk = 29
        targetSdk = 36
        versionCode = prop("VERSION_CODE")?.toIntOrNull()
            ?: error("VERSION_CODE must be set in local.properties or environment")
        versionName = prop("VERSION_NAME")
            ?: error("VERSION_NAME must be set in local.properties or environment")

        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${prop("GITHUB_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "GITHUB_CLIENT_SECRET", "\"${prop("GITHUB_CLIENT_SECRET") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = prop("KEYSTORE_FILE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = prop("KEYSTORE_PASSWORD")
                keyAlias = prop("KEY_ALIAS")
                keyPassword = prop("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystorePath = prop("KEYSTORE_FILE")
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // kotlinx.serialization
    implementation(libs.kotlinx.serialization.json)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Markwon
    implementation(libs.markwon.core)

    // DataStore
    implementation(libs.datastore.preferences)

    // Security (EncryptedSharedPreferences)
    implementation(libs.security.crypto)
}

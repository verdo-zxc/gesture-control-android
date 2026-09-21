plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.verdo.gesturecontrol"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.verdo.gesturecontrol"
        minSdk = 26
        // AccessibilityService sideload restriction on Android 13+ is triggered
        // for apps targeting API 33+. Keep this sideload/debug build at API 32
        // so the user can enable the service normally without the restricted-
        // settings gate. We still compile against the current SDK.
        targetSdk = 32
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("com.google.mediapipe:tasks-vision:0.10.26")
    testImplementation("junit:junit:4.13.2")
}

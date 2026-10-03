import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// signing info is optional, put it in keystore.properties (see README)
val ksFile = rootProject.file("keystore.properties")
val ks = Properties().apply { if (ksFile.exists()) ksFile.inputStream().use { load(it) } }

android {
    namespace = "com.example.edgevolume"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.edgevolume"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (ksFile.exists()) {
            create("release") {
                storeFile = file(ks["storeFile"] as String)
                storePassword = ks["storePassword"] as String
                keyAlias = ks["keyAlias"] as String
                keyPassword = ks["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (ksFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

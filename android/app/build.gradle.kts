plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.twinsanityevolved.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.twinsanityevolved.app"
        minSdk = 26
        targetSdk = 34
        // Android installs an APK over the installed one only when its versionCode is higher. CI sets VERSION_CODE from
        // its run number, so every CI build updates the last; a local build keeps the default unless it sets it.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 4
        versionName = System.getenv("VERSION_NAME") ?: "0.1.3"
    }

    // A fixed key, so each build installs over the last one (Android refuses an update signed by another key).
    // CI sets KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD from repository secrets; without them the
    // build keeps the default debug signing.
    signingConfigs {
        create("fixed") {
            System.getenv("KEYSTORE_PATH")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (System.getenv("KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

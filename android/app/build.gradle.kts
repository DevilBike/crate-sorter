plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "io.github.devilbike.cratesorter"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.devilbike.cratesorter"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    // CI passes in a signing key that it keeps between builds, so each new APK installs
    // over the previous one and keeps your data. Local builds fall back to the debug key.
    val store = System.getenv("SIGNING_STORE")
    signingConfigs {
        create("release") {
            if (store != null) {
                storeFile = file(store)
                storePassword = System.getenv("SIGNING_PASS")
                keyAlias = "crate"
                keyPassword = System.getenv("SIGNING_PASS")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cameronamer.telegramdrive"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cameronamer.telegramdrive"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing.
    //
    // To produce a real Play Store-signed APK:
    //   1. Generate a keystore:  keytool -genkey -v -keystore release.keystore \
    //          -alias telegram_drive -keyalg RSA -keysize 2048 |validity 10000
    //   2. Add the following entries to local.properties (gitignored):
    //          TELEGRAM_DRIVE_KEYSTORE=/abs/path/to/release.keystore
    //          TELEGRAM_DRIVE_KEY_ALIAS=telegram_drive
    //          TELEGRAM_DRIVE_KEYSTORE_PWD=...
    //          TELEGRAM_DRIVE_KEY_PWD=...
    //   3. The release build will then use that keystore. If any of these is
    //      missing we fall back to the debug keystore so local builds still work.
    signingConfigs {
        create("release") {
            val keystorePath = providers.environmentVariable("TELEGRAM_DRIVE_KEYSTORE")
                .orElse(localProperties("TELEGRAM_DRIVE_KEYSTORE"))
            val keystorePwd = providers.environmentVariable("TELEGRAM_DRIVE_KEYSTORE_PWD")
                .orElse(localProperties("TELEGRAM_DRIVE_KEYSTORE_PWD"))
            val keyAlias = providers.environmentVariable("TELEGRAM_DRIVE_KEY_ALIAS")
                .orElse(localProperties("TELEGRAM_DRIVE_KEY_ALIAS"))
                .getOrElse("telegram_drive")
            val keyPwd = providers.environmentVariable("TELEGRAM_DRIVE_KEY_PWD")
                .orElse(localProperties("TELEGRAM_DRIVE_KEY_PWD"))
                .getOrElse(keystorePwd.getOrElse(""))
            val path = keystorePath.orNull
            if (path != null) {
                storeFile = file(path)
                storePassword = keystorePwd.getOrElse("")
                this.keyAlias = keyAlias
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the release keystore if it was configured above; otherwise
            // fall back to debug so local/CI builds without secrets still
            // produce a runnable APK.
            signingConfig = signingConfigs.findByName("release")
                ?.takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

/** Reads a value from the local.properties file (gitignored). */
fun localProperties(key: String) =
    providers.gradleProperty(key)

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.1")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // WorkManager for background sync
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // UniFFI requirement
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    // ExoPlayer for media streaming
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
}

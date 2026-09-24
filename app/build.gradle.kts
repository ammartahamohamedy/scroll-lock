import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "dev.scrolllock.dpc"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.scrolllock.dpc"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        val props = Properties().apply {
            keystorePropertiesFile.inputStream().use { load(it) }
        }

        signingConfigs {
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }

        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
                signingConfig = signingConfigs.getByName("release")
            }
        }
    } else {
        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
            }
        }
    }
}

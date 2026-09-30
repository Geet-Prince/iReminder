import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing credentials are read from local.properties (git-ignored) or
// environment variables, so no keystore or password is ever committed.
val signingProps = Properties().apply {
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        localProps.inputStream().use { load(it) }
    }
}
fun signingValue(key: String, env: String): String? =
    signingProps.getProperty(key) ?: System.getenv(env)

val releaseStoreFile = signingValue("IREMEMINDER_STORE_FILE", "IREMEMINDER_STORE_FILE")
val releaseStorePassword = signingValue("IREMEMINDER_STORE_PASSWORD", "IREMEMINDER_STORE_PASSWORD")
val releaseKeyAlias = signingValue("IREMEMINDER_KEY_ALIAS", "IREMEMINDER_KEY_ALIAS")
val releaseKeyPassword = signingValue("IREMEMINDER_KEY_PASSWORD", "IREMEMINDER_KEY_PASSWORD")

android {
    namespace = "me.geetprince.ireminders"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "me.geetprince.ireminders"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        if (releaseStoreFile != null && releaseStorePassword != null
            && releaseKeyAlias != null && releaseKeyPassword != null
        ) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
}

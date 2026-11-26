import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val signingPropertiesFile = rootProject.file(".signing/keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.beeze.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.beeze.music"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.4"
        manifestPlaceholders["admobAppId"] = "ca-app-pub-8834917625965765~1911816538"
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-8834917625965765/7387504490\"")
        buildConfigField("String", "ADMOB_APP_OPEN_UNIT_ID", "\"ca-app-pub-8834917625965765/7779541681\"")
        buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"ca-app-pub-8834917625965765/2510671621\"")
    }

    signingConfigs {
        create("upload") {
            storeFile = rootProject.file(signingProperties.getProperty("storeFile", ".signing/beeze-upload.jks"))
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "ADMOB_APP_OPEN_UNIT_ID", "\"ca-app-pub-3940256099942544/9257395921\"")
            buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"ca-app-pub-3940256099942544/2247696110\"")
        }
        release {
            // R8 代码压缩 + 资源压缩，分发包体积从 ~22MB 降到个位数 MB
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.coil.compose)
    implementation(libs.mobile.ads)
    implementation(libs.user.messaging.platform)
    implementation(project(":syncedlyrics"))
    debugImplementation(libs.compose.ui.tooling)
}

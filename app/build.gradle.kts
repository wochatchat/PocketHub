import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pockethub"
    compileSdk = 34

    // Auto-increment version: read from version.properties (maintained by build script)
    val versionPropsFile = rootProject.file("version.properties")
    val (vCode, vName) = if (versionPropsFile.exists()) {
        val p = Properties().apply { load(versionPropsFile.inputStream()) }
        (p.getProperty("versionCode") ?: "1").toInt() to (p.getProperty("versionName") ?: "0.1.0")
    } else { 1 to "0.1.0" }

    defaultConfig {
        applicationId = "com.pockethub"
        minSdk = 26
        targetSdk = 34
        versionCode = vCode
        versionName = vName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "GITHUB_DEFAULT_CLIENT_ID", "\"\"")
        buildConfigField("String", "GITHUB_DEFAULT_CLIENT_SECRET", "\"\"")
        buildConfigField("String", "GITHUB_OAUTH_REDIRECT_URI", "\"pockethub://oauth/callback\"")
        buildConfigField("String", "GITHUB_API_BASE_URL", "\"https://api.github.com/\"")
        buildConfigField("String", "GITHUB_WEB_BASE_URL", "\"https://github.com/\"")

        // Skip lint vital check during release — auto-signed CI, fails build on warnings
        lint {
            abortOnError = false
            checkReleaseBuilds = false
        }
    }

    // Read signing config from signing.properties
    val signingPropsFile = rootProject.file("signing.properties")
    signingConfigs {
        if (signingPropsFile.exists()) {
            val sp = Properties().apply { load(signingPropsFile.inputStream()) }
            create("release") {
                storeFile = rootProject.file(sp.getProperty("STORE_FILE"))
                storePassword = sp.getProperty("STORE_PASSWORD")
                keyAlias = sp.getProperty("KEY_ALIAS")
                keyPassword = sp.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Browser (Custom Tabs for OAuth)
    implementation(libs.androidx.browser)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)  // hilt-compiler also handles @HiltWorker

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // Image
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}

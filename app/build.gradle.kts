import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * The Reddit client ID is read from local.properties (git-ignored) so it never
 * lands in the repository. A blank value still builds; the login screen then
 * reports that the app is not configured.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val redditClientId: String = (localProperties.getProperty("reddit.clientId") ?: "").trim()

android {
    namespace = "com.readvoice.app"
    compileSdk = 34

    defaultConfig {
        /**
         * Assumptions pending confirmation: the package name, and minSdk 24
         * (Android 7) as the oldest supported phone.
         */
        applicationId = "com.readvoice.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        /**
         * Release APKs carry no client ID; every user enters their own on the
         * setup screen. Previously the value from local.properties was
         * compiled into every build:
         *
         *     buildConfigField("String", "REDDIT_CLIENT_ID", "\"$redditClientId\"")
         */
        buildConfigField("String", "REDDIT_CLIENT_ID", "\"\"")
        buildConfigField("String", "REDDIT_REDIRECT_URI", "\"readvoice://oauth\"")
    }

    buildTypes {
        /**
         * Assumption pending confirmation: debug builds still read
         * reddit.clientId from local.properties, so a developer can skip the
         * setup screen. Leave it blank to see the screen like any other user.
         */
        debug {
            buildConfigField("String", "REDDIT_CLIENT_ID", "\"$redditClientId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

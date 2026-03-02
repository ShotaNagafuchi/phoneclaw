import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.gms.google-services")
}

// --- versionCode 自動インクリメント ---
val versionPropsFile = file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(versionPropsFile.inputStream())
}
val autoVersionCode = (versionProps["VERSION_CODE"] as? String)?.toIntOrNull() ?: 1

android {
    namespace = "com.example.universal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.universal"
        minSdk = 24
        targetSdk = 35
        versionCode = autoVersionCode
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val moondreamAuth = (project.findProperty("MOONDREAM_AUTH") as String?)
            ?.replace("\"", "\\\"")
            ?: ""
        buildConfigField("String", "MOONDREAM_AUTH", "\"$moondreamAuth\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
        buildConfig = true
    }
}

// Allow debug builds without google-services.json
afterEvaluate {
    tasks.matching {
        it.name.endsWith("GoogleServices") && it.name.contains("Debug")
    }.configureEach {
        enabled = false
    }
}

dependencies {
    // Firebase - KEPT
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.code.gson:gson:2.10.1")
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Material Design
    implementation("com.google.android.material:material:1.11.0")

    // UI Components
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // JavaScript Engine - FIXED VERSION
    implementation("org.mozilla:rhino:1.7.13")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


    // Room (Edge AI データ層)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    // WorkManager (夜間メモリ統合)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // MediaPipe Vision (表情報酬評価)
    implementation("com.google.mediapipe:tasks-vision:0.10.24")

    // MediaPipe GenAI (ローカルLLM推論 - Gemma 3 1B QAT INT4)
    implementation("com.google.mediapipe:tasks-genai:0.10.27")

    // CameraX (MediaPipe FaceMesh用)
    val cameraXVersion = "1.5.1"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// assembleのたびにversionCodeをインクリメント
tasks.configureEach {
    if (name.startsWith("assemble")) {
        doLast {
            val nextCode = autoVersionCode + 1
            versionProps["VERSION_CODE"] = nextCode.toString()
            versionProps.store(versionPropsFile.outputStream(), null)
        }
    }
}

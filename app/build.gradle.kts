import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

val versionFile = file("${rootProject.projectDir}/version.properties")
val versionProps = Properties().apply {
    if (versionFile.exists()) versionFile.inputStream().use { load(it) }
}
val versionCodeFromFile = (versionProps.getProperty("versionCode") ?: "1").toInt().coerceAtLeast(1)
val versionNameFromFile = versionProps.getProperty("versionName") ?: "1.0.0"

android {
    namespace = "com.example.universal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.universal"
        minSdk = 24
        targetSdk = 33
        versionCode = versionCodeFromFile
        versionName = versionNameFromFile

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
    // ビルド前にバージョンを表示（gradlew 実行時に分かるようにする）
    listOf("assembleDebug", "assembleRelease").forEach { name ->
        tasks.findByName(name)?.dependsOn("showVersion")
    }
}

// 現在のバージョンを表示（gradlew 実行前のバージョンチェック用）
tasks.register("showVersion") {
    group = "version"
    description = "Prints current version from version.properties"
    doLast {
        val vf = file("${rootProject.projectDir}/version.properties")
        val p = Properties().apply { if (vf.exists()) vf.inputStream().use { load(it) } }
        val code = (p.getProperty("versionCode") ?: "1").toInt().coerceAtLeast(1)
        val name = p.getProperty("versionName") ?: "1.0.0"
        println(">>> versionName=$name  versionCode=$code")
    }
}

// version.properties の versionCode と versionName（パッチ）を 1 上げる
tasks.register("bumpVersion") {
    group = "version"
    description = "Increments versionCode and versionName (patch) in version.properties"
    doLast {
        val vf = file("${rootProject.projectDir}/version.properties")
        val p = Properties().apply { if (vf.exists()) vf.inputStream().use { load(it) } }
        val code = (p.getProperty("versionCode") ?: "1").toInt().coerceAtLeast(1) + 1
        val name = p.getProperty("versionName") ?: "1.0.0"
        val parts = name.split(".")
        val patch = parts.lastOrNull()?.toIntOrNull() ?: 0
        val newName = parts.dropLast(1).joinToString(".").ifEmpty { "1.0" } + "." + (patch + 1)
        vf.writeText("# Auto-updated by Gradle bumpVersion. Used by app/build.gradle.kts.\nversionCode=$code\nversionName=$newName\n")
        println(">>> Bumped to versionName=$newName  versionCode=$code")
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


    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

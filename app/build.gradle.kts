import java.net.URI

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.signlearn"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.signlearn"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Keep the MediaPipe model file uncompressed so it can be loaded straight from assets.
    androidResources {
        noCompress += "task"
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Camera (CameraX) + on-device hand tracking (MediaPipe)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// Downloads Google's MediaPipe hand-tracking model into src/main/assets the first time you build.
// If this fails (no internet), download the file manually — see README.md, step 3.
val downloadHandModel = tasks.register("downloadHandModel") {
    val url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"
    val dest = layout.projectDirectory.file("src/main/assets/hand_landmarker.task").asFile
    outputs.file(dest)
    onlyIf { !dest.exists() }
    doLast {
        try {
            dest.parentFile.mkdirs()
            URI(url).toURL().openStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            println("SignLearn: downloaded hand_landmarker.task")
        } catch (e: Exception) {
            dest.delete()
            println("SignLearn: could not download hand_landmarker.task (${e.message}). See README step 3.")
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(downloadHandModel) }
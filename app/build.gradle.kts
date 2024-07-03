plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    //id("org.jetbrains.kotlin.plugin.serialization") //version '1.7.20')

    // To use Kotlin Symbol Processing (KSP)
    // https://kotlinlang.org/docs/ksp-quickstart.html#add-a-processor
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}

android {
    namespace = "com.example.first_test"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.first_test"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // quitar "arm64-v8a" si es mu pesada la carpeta lib en el apk
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }
    }

    splits {
        abi {
            //isEnable = true // true para generar por separado
            //reset()
            // include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            //include("armeabi-v7a", "arm64-v8a")
            //isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    androidResources {
        noCompress.addAll(listOf("tflite"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17" //1.8
    }

    buildFeatures {
        compose = true
        mlModelBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.lifecycle)
    //implementation(libs.androidx.camera.core)
    //implementation(libs.androidx.camera.camera2)
    implementation("androidx.camera:camera-core") //:1.0.0
    implementation("androidx.camera:camera-camera2") //:1.0.0
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Numero de BIGcores
    //implementation("com.github.sacv081c:cpufeatures:1.0.4")

    // Tensorflow
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-api:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")

    // The FAT one
    //implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")

    // OpenCV - Otro gordo mas a la lista...
    //implementation("org.opencv:opencv:4.10.0")

    // image y GIF desde una URL
    implementation("io.coil-kt:coil-compose:2.2.2")
    implementation("io.coil-kt:coil-gif:2.2.2")

    // ROOM - Base de datos LOCAL basada en SQL
    // https://developer.android.com/training/data-storage/room#kotlin
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")
    // ksp("androidx.room:room-compiler:$room_version")
    // optional - Kotlin Extensions and Coroutines support for Room
    implementation("androidx.room:room-ktx:$room_version")

    // Ktor - HTTP Client (tambien hay server en esta libreria)
    // https://ktor.io/docs/client-create-new-application.html#add-dependencies
    // val ktor_version = "2.3.12"
    // implementation("io.ktor:ktor-client-core:$ktor_version")
    // implementation("io.ktor:ktor-client-cio:$ktor_version")

    // Torch
    //implementation("org.pytorch:pytorch_android_lite:1.10.0")
    //implementation("org.pytorch:pytorch_android_torchvision_lite:1.10.0")
    //implementation("org.pytorch:pytorch_android_lite:2.1.0")
    //implementation("org.pytorch:pytorch_android_torchvision_lite:2.1.0")
    //implementation("pkg:maven/org.pytorch/torchvision_ops@0.14.0")
    //implementation("org.pytorch:torchvision_ops:0.14.0")

    // JSON
    implementation("com.google.code.gson:gson:2.8.8")

    // HTTP
    //implementation("com.github.mezhevikin:http-request-kotlin:0.0.5")
    //implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")

    // MQTT - https://github.com/eclipse/paho.mqtt.android
    //implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.1.0")
    //implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.1.0")

    implementation("androidx.fragment:fragment-ktx:1.8.0")
}
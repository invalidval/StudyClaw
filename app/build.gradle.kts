plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.dagger.hilt.android)
//    id("com.google.devtools.ksp") version "1.9.22-1.0.18"  // 改这里
    alias(libs.plugins.kotlin.compose)
//    id("com.google.devtools.ksp") version "2.0.20-1.0.24"

}

android {
    namespace = "com.zlearn"
    compileSdk = 36

    signingConfigs {
        create("release") {
            // 请手动将你的 jks 文件放入 app 目录下，并修改下面的文件名
            storeFile = file("sc.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "20050530"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "key1"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "20050530"

            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.zlearn.v5"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 默认开发环境配置
        buildConfigField("String", "BASE_URL", "\"http://10.129.215.233:8080/\"")
        buildConfigField("String", "ALIYUN_API_KEY", "\"sk-cec6e41f863145f895f0d9c563b08ffe\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")

            // 发行环境配置（可以在这里覆盖默认值）
            buildConfigField("String", "BASE_URL", "\"https://api.yourdomain.com/\"")
            buildConfigField("String", "ALIYUN_API_KEY", "\"${System.getenv("ALIYUN_API_KEY") ?: "sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}\"")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

//        debug {
//            // 开发工具使用的配置
//            buildConfigField("String", "BASE_URL", "\"http://10.129.215.233:8080/\"")
//        }
    }

    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }

    buildFeatures {
        compose = true
        buildConfig = true // 显式开启 BuildConfig 功能
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.runtime)
    kapt(libs.hilt.compiler)
//    ksp("androidx.room:room-compiler:2.6.1")
//    ksp("com.google.dagger:hilt-compiler:2.56")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
//    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.coroutines.play.services)

    implementation(libs.markdown.renderer.m3)

    // DashScope SDK & RxJava3
//    implementation("com.alibaba.dashscope:dashscope-sdk:2.10.2")
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-latex:4.6.2")
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}

kapt {
    correctErrorTypes = true
}

kotlin {
    jvmToolchain(17)
}
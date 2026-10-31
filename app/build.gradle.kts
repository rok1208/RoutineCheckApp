import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.routinecheck.app"
    compileSdk = 36

    defaultConfig {
        // 플레이스토어 고유 ID (한 번 올리면 바꿀 수 없음). 코드 패키지(namespace)는 그대로 둠
        applicationId = "io.github.rok1208.routinecheck"
        minSdk = 26          // Android 8.0+ (오버레이 TYPE_APPLICATION_OVERLAY, 알림 채널 기준)
        targetSdk = 36          // 2026-08-31부터 플레이 신규 앱·업데이트 필수
        versionCode = 9
        versionName = "0.7.0"
    }

    // 릴리스 서명: 프로젝트 루트의 keystore.properties (git 제외) 가 있을 때만 사용
    //   storeFile=../release.jks  storePassword=...  keyAlias=...  keyPassword=...
    val keystoreFile = rootProject.file("keystore.properties")
    val hasReleaseKey = keystoreFile.exists()
    signingConfigs {
        if (hasReleaseKey) {
            val props = Properties().apply { keystoreFile.inputStream().use { load(it) } }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: 코드 축소·난독화 + 안 쓰는 리소스 제거 (APK 크기 감소, 플레이 권장)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true   // 설정 화면의 버전 표시(BuildConfig.VERSION_NAME)
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    // 챗헤드: LifecycleService 위에 ComposeView 띄우기
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    // 화면 이동
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

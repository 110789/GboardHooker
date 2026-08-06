plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.a110789.gboardhooker"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.a110789.gboardhooker"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // DexKit 的 libdexkit.so 是 aar 里自带的原生库,按 ABI 打包即可,
    // 不需要额外配置;保留常见的四个 ABI。
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // Xposed / LSPosed API —— 只在编译期存在,不会被打进 APK。
    compileOnly("de.robv.android.xposed:api:82")

    // 混淆类/方法扫描,核心依赖。
    implementation("org.luckypray:dexkit:2.0.7")

    // 设置界面。
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}

plugins { id("com.android.application") }
android {
    namespace = "com.kongye.calculator"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.kongye.calculator"
        minSdk = 26
        targetSdk = 36
        versionCode = 37
        versionName = "1.0.3"
        testInstrumentationRunner = providers.gradleProperty("instrumentationRunner")
            .orElse("com.kongye.calculator.GraphDeviceTest").get()
    }
    signingConfigs {
        create("localRelease") {
            storeFile = rootProject.file("build/test-signing.p12")
            storePassword = "android"
            keyAlias = "kongye-test"
            keyPassword = "android"
        }
    }
    buildTypes { release {
        isMinifyEnabled = false
        // Signing material stays local. A fresh checkout can build an unsigned release.
        if (rootProject.file("build/test-signing.p12").isFile) {
            signingConfig = signingConfigs.getByName("localRelease")
        }
    } }
    testBuildType = "release"
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}







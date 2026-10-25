plugins { id("com.android.application") }

dependencies {
    implementation("androidx.viewpager2:viewpager2:1.1.0")
}

android {
    namespace = "dev.squirrel.vpn"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"
    useLibrary("android.test.runner", false)
    useLibrary("android.test.base", false)
    defaultConfig {
        applicationId = "dev.squirrel.vpn"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("SQUIRREL_VERSION_CODE")?.toInt() ?: 2
        versionName = System.getenv("SQUIRREL_VERSION_NAME") ?: "0.1.1"
        ndk { abiFilters += (System.getenv("ANDROID_ABIS") ?: "arm64-v8a,x86_64").split(",") }
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").jniLibs.srcDir("build/generated/squirrel-jni")
    buildTypes { release { isMinifyEnabled = false } }
}

// Build the core for every packaged ABI. Never silently package a UI-only APK.
val buildNative by tasks.registering(Exec::class) {
    workingDir(rootDir)
    commandLine("bash", "scripts/build-native.sh")
    // Cargo performs its own incremental dependency checks, including the core checkout.
    outputs.upToDateWhen { false }
}
tasks.named("preBuild").configure { dependsOn(buildNative) }

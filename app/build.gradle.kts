import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  // Pinned NDK supplies the C++ shared runtime required by DJL tokenizer JNI.
  ndkVersion = "27.3.13750724"

  defaultConfig {
    applicationId = "com.sovai.nocturnenote"
    minSdk = 24
    targetSdk = 36
    versionCode = 6
    versionName = "1.0.5"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Public, development-only signing key for reproducible test APK updates.
  // Never use this identity to sign a production release.
  signingConfigs {
    create("development") {
      storeFile = rootProject.file("config/nocturnenote-dev-debug.jks")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      // Installable internal release APKs use the existing development certificate
      // so they can update current test installs. NEVER publish to an app store.
      // Normal production release builds remain unsigned until a private signing
      // certificate is configured.
      if (providers.gradleProperty("nocturneInternalRelease").orNull == "true") {
        signingConfig = signingConfigs.getByName("development")
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    debug {
      signingConfig = signingConfigs.getByName("development")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)

  // Sign-In via Credential Manager:
  implementation("com.xdcobra.sherpa:sherpa-onnx:1.13.8-1")
  // Full on-device OmniVoice ONNX inference; weights are downloaded separately.
  // Sherpa already provides ai.onnxruntime classes and native runtime.
  implementation("ai.djl:api:0.33.0")
  implementation("ai.djl.huggingface:tokenizers:0.33.0")
  implementation("ai.djl.android:tokenizer-native:0.33.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  add("ksp", libs.androidx.room.compiler)
}


// DJL's tokenizer-native AAR ships libdjl_tokenizer.so but does not package
// its ELF dependency libc++_shared.so. Android cannot load the tokenizer
// without a same-ABI NDK C++ runtime inside the APK. Copy it from the pinned
// Android NDK at build time, rather than downloading untrusted binaries.
val stageTokenizerCxxRuntime = tasks.register<Copy>("stageTokenizerCxxRuntime") {
  val pinnedNdk = android.ndkVersion
  val androidAbis = mapOf(
    "armeabi-v7a" to "arm-linux-androideabi",
    "arm64-v8a" to "aarch64-linux-android",
    "x86" to "i686-linux-android",
    "x86_64" to "x86_64-linux-android"
  )
  val runtimeRoot = providers.provider {
    val sdkLocation = providers.environmentVariable("ANDROID_HOME")
      .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
      .orElse(providers.provider {
        val props = Properties()
        val local = rootProject.file("local.properties")
        if (local.isFile) local.inputStream().use { props.load(it) }
        props.getProperty("sdk.dir")
          ?: throw GradleException("Cannot find Android SDK. Set ANDROID_HOME or sdk.dir.")
      }).get()
    val root = file(sdkLocation).resolve("ndk/$pinnedNdk/toolchains/llvm/prebuilt")
    val host = root.listFiles()?.firstOrNull { it.isDirectory }
      ?: throw GradleException("Android NDK $pinnedNdk is required to package OmniVoice tokenizer runtime")
    host.resolve("sysroot/usr/lib")
  }
  androidAbis.forEach { (abi, triple) ->
    from(runtimeRoot.map { root ->
      root.resolve("$triple/libc++_shared.so").also { file ->
        if (!file.isFile) throw GradleException("Missing NDK C++ runtime for $abi: $file")
      }
    }) { into(abi) }
  }
  into(layout.buildDirectory.dir("generated/native-tokenizer-runtime"))
}
// AGP 9 disallows TaskProviders in legacy sourceSets. Declare a static path
// and an explicit task dependency before Android merges JNI folders.
android.sourceSets.getByName("main").jniLibs.srcDir(
  layout.buildDirectory.dir("generated/native-tokenizer-runtime").get().asFile
)
tasks.matching {
  it.name == "preBuild" || it.name.endsWith("JniLibFolders")
}.configureEach { dependsOn(stageTokenizerCxxRuntime) }

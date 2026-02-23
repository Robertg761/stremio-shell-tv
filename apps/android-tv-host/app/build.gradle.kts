import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

// Optional CI signing. If these env vars are present, `assemble...Release` will produce signed APKs.
val ssSigningStoreFile = System.getenv("SS_SIGNING_STORE_FILE")
val ssSigningStorePassword = System.getenv("SS_SIGNING_STORE_PASSWORD")
val ssSigningKeyAlias = System.getenv("SS_SIGNING_KEY_ALIAS")
val ssSigningKeyPassword = System.getenv("SS_SIGNING_KEY_PASSWORD")
val ssSigningStoreType = System.getenv("SS_SIGNING_STORE_TYPE") // e.g. "PKCS12" or "JKS"
val ssHasSigning = !ssSigningStoreFile.isNullOrBlank() &&
  !ssSigningStorePassword.isNullOrBlank() &&
  !ssSigningKeyAlias.isNullOrBlank() &&
  !ssSigningKeyPassword.isNullOrBlank()

val githubReleaseOwner = (project.findProperty("githubReleaseOwner") as String?)
  ?.trim()
  .orEmpty()
  .ifBlank { "Robertg761" }
val githubReleaseRepo = (project.findProperty("githubReleaseRepo") as String?)
  ?.trim()
  .orEmpty()
  .ifBlank { "stremio-shell-tv" }

android {
  namespace = "com.stremioshell.host"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.stremioshell.host"
    minSdk = 26
    targetSdk = 34
    versionCode = 2
    versionName = "0.1.1"
    buildConfigField("String", "GITHUB_RELEASE_OWNER", "\"$githubReleaseOwner\"")
    buildConfigField("String", "GITHUB_RELEASE_REPO", "\"$githubReleaseRepo\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  flavorDimensions += "device"
  productFlavors {
    create("tv") {
      dimension = "device"
      applicationIdSuffix = ".tv"
      versionNameSuffix = "-tv"
      resValue("string", "app_name", "Stremio Shell TV")
      buildConfigField("boolean", "IS_TV", "true")
    }
  }

  if (ssHasSigning) {
    signingConfigs {
      create("release") {
        storeFile = file(ssSigningStoreFile!!)
        if (!ssSigningStoreType.isNullOrBlank()) {
          storeType = ssSigningStoreType
        }
        storePassword = ssSigningStorePassword
        keyAlias = ssSigningKeyAlias
        keyPassword = ssSigningKeyPassword
      }
    }
  }

  buildTypes {
    debug {
    }
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      if (ssHasSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
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
    buildConfig = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.14"
  }
}

val coreRuntimeDistDir = rootDir.resolve("core-runtime-dist")
val coreRuntimeAssetsDir = project.layout.projectDirectory.dir("src/main/assets/core-runtime")

val syncCoreRuntimeAssets by tasks.registering(Copy::class) {
  group = "build setup"
  description = "Sync bundled core runtime JS into Android assets."

  from(coreRuntimeDistDir)
  into(coreRuntimeAssetsDir)

  doFirst {
    if (!coreRuntimeDistDir.exists()) {
      throw GradleException(
        "Missing core runtime bundle at ${coreRuntimeDistDir.absolutePath}. Expected runtime.js in this directory."
      )
    }
  }
}

tasks.matching { task ->
  task.name.startsWith("merge") && task.name.endsWith("Assets")
}.configureEach {
  dependsOn(syncCoreRuntimeAssets)
}

tasks.matching { task ->
  task.name.contains("LintVital", ignoreCase = true)
}.configureEach {}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:2024.09.00")

  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
  implementation("androidx.work:work-runtime-ktx:2.9.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

  implementation(composeBom)
  androidTestImplementation(composeBom)
  implementation("androidx.activity:activity-compose:1.9.2")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-util")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.navigation:navigation-compose:2.8.2")
  implementation("androidx.tv:tv-material:1.0.0")
  implementation("androidx.javascriptengine:javascriptengine:1.0.0")

  implementation("androidx.media3:media3-exoplayer:1.4.1")
  implementation("androidx.media3:media3-ui:1.4.1")

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20240303")

  androidTestImplementation("androidx.test:core-ktx:1.6.1")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
  androidTestImplementation("androidx.test:runner:1.6.2")
  androidTestImplementation("androidx.test:rules:1.6.1")
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")

  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}

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

val debugWebAppUrl = (project.findProperty("webAppUrl") as String?)
  ?.trim()
  .orEmpty()
  .ifBlank { "" }
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
    applicationId = "com.stremioshell.host.tv"
    minSdk = 26
    targetSdk = 34
    versionCode = 2
    versionName = "0.1.1-tv"
    resValue("string", "app_name", "Stremio Shell TV")
    buildConfigField("String", "GITHUB_RELEASE_OWNER", "\"$githubReleaseOwner\"")
    buildConfigField("String", "GITHUB_RELEASE_REPO", "\"$githubReleaseRepo\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
      buildConfigField("String", "WEB_APP_URL", "\"$debugWebAppUrl\"")
      buildConfigField("String", "WEB_REMOTE_FALLBACK_URL", "\"https://web.stremio.com/\"")
      manifestPlaceholders["usesCleartextTraffic"] = "true"
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
      buildConfigField("String", "WEB_APP_URL", "\"\"")
      buildConfigField("String", "WEB_REMOTE_FALLBACK_URL", "\"https://web.stremio.com/\"")
      manifestPlaceholders["usesCleartextTraffic"] = "false"
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
    buildConfig = true
  }
}

val webDistDir = rootDir.resolve("../web/dist")
val webAssetsDir = project.layout.projectDirectory.dir("src/main/assets/web")
val coreRuntimeDistDir = rootDir.resolve("core-runtime-dist")
val coreRuntimeAssetsDir = project.layout.projectDirectory.dir("src/main/assets/core-runtime")

val syncWebAssets by tasks.registering(Copy::class) {
  group = "build setup"
  description = "Sync built web shell assets from apps/web/dist into Android assets."

  from(webDistDir)
  into(webAssetsDir)

  doFirst {
    if (!webDistDir.exists()) {
      throw GradleException(
        "Missing web bundle at ${webDistDir.absolutePath}. Run `pnpm --filter @stremio-shell/web build` first."
      )
    }
  }
}

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
  dependsOn(syncWebAssets)
  dependsOn(syncCoreRuntimeAssets)
}

tasks.matching { task ->
  task.name.contains("LintVital", ignoreCase = true)
}.configureEach {
  dependsOn(syncWebAssets)
}

dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.webkit:webkit:1.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
  implementation("androidx.work:work-runtime-ktx:2.9.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

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
}

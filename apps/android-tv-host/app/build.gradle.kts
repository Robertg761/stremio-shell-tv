import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.stremioshell.host"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.stremioshell.host"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "0.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  flavorDimensions += "device"
  productFlavors {
    create("mobile") {
      dimension = "device"
      applicationIdSuffix = ".mobile"
      versionNameSuffix = "-mobile"
      resValue("string", "app_name", "Stremio Shell Mobile")
      buildConfigField("boolean", "IS_TV", "false")
    }
    create("tv") {
      dimension = "device"
      applicationIdSuffix = ".tv"
      versionNameSuffix = "-tv"
      resValue("string", "app_name", "Stremio Shell TV")
      buildConfigField("boolean", "IS_TV", "true")
    }
  }

  buildTypes {
    debug {
      buildConfigField("String", "WEB_APP_URL", "\"\"")
    }
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      buildConfigField("String", "WEB_APP_URL", "\"\"")
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

tasks.matching { task ->
  task.name.startsWith("merge") && task.name.endsWith("Assets")
}.configureEach {
  dependsOn(syncWebAssets)
}

dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.webkit:webkit:1.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")

  implementation("androidx.media3:media3-exoplayer:1.4.1")
  implementation("androidx.media3:media3-ui:1.4.1")
}

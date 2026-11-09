import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Local builds derive the version from git, exactly like CI does from the tag, so a hand-built
// APK never reports a different version than the published release.
fun gitOutput(vararg args: String): String? = try {
    val process = ProcessBuilder("git", *args)
        .directory(rootDir)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    val text = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0 && text.isNotEmpty()) text else null
} catch (_: Exception) {
    null
}

val gitVersionName: String = gitOutput("describe", "--tags", "--abbrev=0")
    ?.removePrefix("v")
    ?.takeIf { it.matches(Regex("""\d+\.\d+\.\d+.*""")) }
    ?: "1.1.0"
val gitVersionCode: Int = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

// A locally generated key signs release builds when keystore.properties is present; without it
// the release APK stays unsigned (as before) instead of failing the build.
val keystoreProperties = rootProject.file("keystore.properties")
val signing = if (keystoreProperties.exists()) {
    Properties().apply { keystoreProperties.inputStream().use { load(it) } }
} else {
    null
}

android {
    namespace = "net.reddec.mylinks"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.reddec.mylinks"
        minSdk = 26
        targetSdk = 36
        // CI passes both explicitly from the tag; local builds derive the same values from git.
        versionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: gitVersionCode
        versionName = providers.gradleProperty("versionName").orNull ?: gitVersionName
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (signing != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    // The mockable android.jar stubs org.json, so unit tests get a real implementation.
    testImplementation("org.json:json:20240303")
}

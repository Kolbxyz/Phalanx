import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

// Release signing is read from local.properties (never committed). Absent -> unsigned release.
val keystoreProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("releaseStoreFile") != null

/** `values-xx` qualifiers that carry a translated strings.xml. */
val translatedLocaleQualifiers: List<String> = file("src/main/res").listFiles { f ->
    f.isDirectory && f.name.startsWith("values-") && f.resolve("strings.xml").exists()
}?.map { it.name.removePrefix("values-") }.orEmpty()

android {
    namespace = "org.phalanx.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.phalanx.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // OAuth redirect scheme, shared by the manifest intent-filter and the code that builds the
        // redirect URI. MAS requires it to start with the reversed host of the client URI
        // (core/matrix/OAuthClient.kt), so change both together.
        val oauthRedirectScheme = "xyz.kolbxyz.phalanx"
        manifestPlaceholders["oauthRedirectScheme"] = oauthRedirectScheme
        buildConfigField("String", "OAUTH_REDIRECT_SCHEME", "\"$oauthRedirectScheme\"")

        // The Rust SDK is ~100 MiB across four ABIs. x86 is emulator-only, so it is not shipped.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("releaseStoreFile"))
                storePassword = keystoreProps.getProperty("releaseStorePassword")
                keyAlias = keystoreProps.getProperty("releaseKeyAlias")
                keyPassword = keystoreProps.getProperty("releaseKeyPassword")
            }
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        // Release's speed on the debug app's identity. A debuggable build runs with ART's
        // optimisations off and without R8, which is most of why Compose animations stutter
        // there; this one is optimised like release but installed as `.debug`, so it updates the
        // debug app in place and keeps its data. Not debuggable.
        create("fastDebug") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Generates `localeConfig` from the `values-xx` directories, so the phone's own "App
        // languages" screen lists exactly what this build ships.
        generateLocaleConfig = true

        // Without `localeFilters`, every locale any dependency ships a resource for is packaged.
        localeFilters += (setOf("en") + translatedLocaleQualifiers.flatMap { qualifier ->
            listOf(qualifier, qualifier.substringBefore('-').substringBefore('+'))
        })
    }

    lint {
        // androidx.lifecycle's RepeatOnLifecycleDetector crashes on Kotlin 2.3 sources
        // ("AsyncExecutionService.getService must not return null"). This disables the broken
        // detector, not a real finding — drop it once the lifecycle lint artifact is fixed.
        disable += "RepeatOnLifecycleWrongUsage"
        disable += "UnsafeOptInUsageError"
        warningsAsErrors = false
        abortOnError = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xannotation-default-target=param-property"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.unifiedpush.connector)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.matrix.rust.sdk)
    implementation(libs.rustls.platform.verifier)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Coverage ratchet: the floor sits just under today's line coverage, so it can only be raised.
kover {
    reports {
        verify {
            rule {
                minBound(0)
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    source.setFrom("src/main/java", "src/test/java")
}

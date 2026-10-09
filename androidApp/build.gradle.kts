plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    // Le chiavi di Navigation 3 sono serializzabili: il back stack sopravvive a rotazioni e process death.
    alias(libs.plugins.kotlin.serialization)
}

val versione = providers.gradleProperty("versionName").getOrElse("0.1.0")
val codiceVersione = versione.substringBefore('-').split('.').map { it.toInt() }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

val keystoreRilascio = providers.environmentVariable("AULA_KEYSTORE_PATH").orNull

android {
    namespace = "it.aula.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "it.aula.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = codiceVersione
        versionName = versione
    }

    signingConfigs {
        if (keystoreRilascio != null) {
            create("rilascio") {
                storeFile = file(keystoreRilascio)
                storePassword = providers.environmentVariable("AULA_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("AULA_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("AULA_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // In locale, senza keystore di rilascio, si firma con quello di debug: installabile per prove.
            signingConfig = signingConfigs.findByName("rilascio") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.allan.imagineer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.allan.imagineer"
        minSdk = 31
        targetSdk = 37
        versionCode = 5
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A assinatura da versão final: o arquivo e a senha ficam FORA do repositório (em ~/.gradle/gradle.properties).
    // Sem essas propriedades, o release sai sem assinar e o build continua funcionando.
    val arquivoDaChave = providers.gradleProperty("IMAGINEER_KEYSTORE_ARQUIVO").orNull
    signingConfigs {
        if (arquivoDaChave != null) {
            create("final") {
                storeFile = file(arquivoDaChave)
                storePassword = providers.gradleProperty("IMAGINEER_KEYSTORE_SENHA").get()
                keyAlias = providers.gradleProperty("IMAGINEER_KEYSTORE_ALIAS").get()
                keyPassword = providers.gradleProperty("IMAGINEER_KEYSTORE_SENHA").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("final")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // O player do vídeo importado (VD19).
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
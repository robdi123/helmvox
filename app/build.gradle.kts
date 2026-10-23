plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.helmvox"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.helmvox"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
            "META-INF/*.kotlin_module", "META-INF/versions/9/previous-compilation-data.bin",
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // SDK oficial de Anthropic (Claude) para la IA opcional con la clave de cada usuario.
    implementation("com.anthropic:anthropic-java:2.34.0")
    // Catálogo de apps de música (búsqueda + reproducir un resultado, como Android Auto).
    implementation("androidx.media:media:1.7.0")
    testImplementation("junit:junit:4.13.2")
    // org.json real para las pruebas en el PC (el de Android es solo un esqueleto ahí).
    testImplementation("org.json:json:20240303")
}

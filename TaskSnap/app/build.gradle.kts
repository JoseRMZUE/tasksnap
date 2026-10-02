plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ue.edu.co.tasksnap"
    compileSdk {
        version = release(37)
        buildFeatures {
            viewBinding = true
            buildConfig = true
        }
    }

    defaultConfig {
        applicationId = "ue.edu.co.tasksnap"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions{
            annotationProcessorOptions{
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }

        // ===== URL BASE DE LA API SPRING BOOT =====
        // Para EMULADOR usa: "http://10.0.2.2:8080"
        // Para CELULAR FÍSICO usa: "http://<TU_IP_LOCAL>:8080"
        // Obtén tu IP con 'ipconfig' en CMD (Windows) buscando "IPv4 Address"
        buildConfigField("String", "API_BASE_URL", "\"https://tasksnap-5kiu.onrender.com\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.ext.junit)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    // --- LIBRERÍAS DE PRUEBAS UNITARIAS (JVM) ---
    // Pruebas Unitarias (JVM) - Para HashUtilTest
    testImplementation("junit:junit:4.13.2")

    // Pruebas Instrumentadas (Dispositivo) - Para OrdenRepositoryTest
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Esta es la CRUCIAL para InstrumentationRegistry.targetContext
    androidTestImplementation("androidx.test:runner:1.5.2")

    // ===== FASE 5: Cliente HTTP Retrofit (Integración app-API, tarea S5, Acta v5) =====
    // Traza: Objetivo específico 3 -> consumir la API Spring Boot desde Android.
    // Retrofit convierte interfaces Java (@GET/@POST) en peticiones HTTP reales.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    // Converter Gson: deserializa el JSON de la API a objetos Java (DTOs).
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    // OkHttp: motor HTTP con timeouts/reintentos configurable (mitiga Riesgo 2: servidor lento/dormido).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Logging interceptor: imprime peticiones/respuestas en Logcat para depurar integración en vivo.
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")


}
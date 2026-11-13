import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localConfig =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun config(name: String, fallback: String = "") =
    providers.gradleProperty(name).orElse(localConfig.getProperty(name, fallback)).get()

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Count all commits reachable from HEAD, including merged backend and Android history.
val shallowRepository =
    providers
        .exec {
            workingDir(rootProject.projectDir)
            commandLine("git", "rev-parse", "--is-shallow-repository")
        }
        .standardOutput
        .asText
        .get()
        .trim()

check(shallowRepository == "false") {
    "App versioning requires complete Git history. Run git fetch --unshallow; use fetch-depth: 0 in CI."
}

val gitRevision =
    providers
        .exec {
            workingDir(rootProject.projectDir)
            commandLine("git", "rev-list", "--count", "HEAD")
        }
        .standardOutput
        .asText
        .get()
        .trim()
        .toInt()

check(gitRevision in 1..2_100_000_000) { "Git commit count exceeds Android versionCode limits." }

val appVersionName = "0.1.$gitRevision"

tasks.register("printAppVersion") {
    group = "help"
    description = "Prints the Git-derived Android version name and code."
    doLast {
        logger.lifecycle("versionName=$appVersionName")
        logger.lifecycle("versionCode=$gitRevision")
    }
}

android {
    namespace = "com.pandre.mileage"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.pandre.mileage"
        minSdk = 26
        targetSdk = 36
        versionCode = gitRevision
        versionName = appVersionName
        testInstrumentationRunner = "com.pandre.mileage.MileageTestRunner"
        buildConfigField(
            "String",
            "API_BASE_URL",
            quoted(config("API_BASE_URL", "http://10.0.2.2:8080/")),
        )
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", quoted(config("GOOGLE_WEB_CLIENT_ID")))
        buildConfigField("String", "FIREBASE_API_KEY", quoted(config("FIREBASE_API_KEY")))
        buildConfigField("String", "FIREBASE_APP_ID", quoted(config("FIREBASE_APP_ID")))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(config("FIREBASE_PROJECT_ID")))
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.google.firebase:firebase-auth:24.0.1")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.12.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Compose brings Espresso 3.5.0; 3.7.0 avoids the removed InputManager.getInstance API.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.work:work-testing:2.11.0")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

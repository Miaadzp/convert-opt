plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ir.miaad.optionheatmap.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        buildConfigField("boolean", "TSETMC_TRUST_ALL", "false")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Temporary diagnostic switch. Set to false to disable in Debug builds.
            buildConfigField("boolean", "TSETMC_TRUST_ALL", "true")
        }
        release {
            buildConfigField("boolean", "TSETMC_TRUST_ALL", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

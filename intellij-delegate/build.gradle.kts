plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val projectVersion: String by project

group = "dev.voqal"
version = project.properties["frameworkVersion"] as String? ?: projectVersion

repositories {
    mavenCentral()
}

dependencies {
    commonMainApi("io.github.oshai:kotlin-logging:7.0.0")
}

kotlin {
    jvm()
    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("org.slf4j:slf4j-api:2.0.16")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
    }
}

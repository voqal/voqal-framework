plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "dev.voqal"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js {
        browser()
        binaries.executable()
    }
}

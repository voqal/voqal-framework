plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val projectVersion: String by project

group = "dev.voqal"
version = project.properties["frameworkVersion"] as String? ?: projectVersion

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

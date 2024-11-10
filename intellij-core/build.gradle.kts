plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val projectVersion: String by project

group = "dev.voqal"
version = project.properties["frameworkVersion"] as String? ?: projectVersion

repositories {
    mavenCentral()
//    maven("https://www.jetbrains.com/intellij-repository/releases/")
}

dependencies {
    commonMainApi("io.github.oshai:kotlin-logging:7.0.0")
}

kotlin {
    jvm {
        withJava()
    }
    js {
        browser()
        binaries.executable()
    }
}

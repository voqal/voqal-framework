plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val projectVersion: String by project

group = "dev.voqal"
version = project.properties["frameworkVersion"] as String? ?: projectVersion

repositories {
    mavenCentral()
    maven("https://www.jetbrains.com/intellij-repository/releases/")
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

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("org.slf4j:slf4j-api:2.0.16")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

                compileOnly("com.jetbrains.intellij.platform:code-style:242.23726.103") {
                    isTransitive = false
                }
                compileOnly("com.jetbrains.intellij.platform:core:242.23726.103") {
                    isTransitive = false
                }
                compileOnly("com.jetbrains.intellij.platform:diff:242.23726.103") {
                    isTransitive = false
                }
                compileOnly("com.jetbrains.intellij.platform:diff-impl:242.23726.103") {
                    isTransitive = false
                }
                compileOnly("com.jetbrains.intellij.platform:editor:242.23726.103") {
                    isTransitive = false
                }
                compileOnly("com.jetbrains.intellij.platform:extensions:242.23726.103") {
                    isTransitive = false
                }
                compileOnly("com.jetbrains.intellij.platform:util:242.23726.103") {
                    isTransitive = false
                }
                compileOnly("com.jetbrains.intellij.platform:util-base:242.23726.103") {
                    isTransitive = false
                }
                compileOnly("com.jetbrains.intellij.platform:util-diff:242.23726.103") {
                    isTransitive = false
                }
            }
        }
    }
}

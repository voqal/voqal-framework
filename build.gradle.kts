plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("maven-publish")
}

val projectVersion: String by project

group = "dev.voqal"
version = project.properties["frameworkVersion"] as String? ?: projectVersion

configure<PublishingExtension> {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/voqal/voqal-framework")
            credentials {
                username = System.getenv("GH_PUBLISH_USERNAME")?.toString()
                password = System.getenv("GH_PUBLISH_TOKEN")?.toString()
            }
        }
    }
}

repositories {
    mavenCentral()
    maven("https://s01.oss.sonatype.org/content/repositories/releases/")
    maven("https://www.jetbrains.com/intellij-repository/releases/")
}

dependencies {
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
                compileOnly(libs.openai.client)
                compileOnly(libs.vertx.core)
                compileOnly(libs.vertx.lang.kotlin.coroutines)
                compileOnly(libs.ktor.client.content.negotiation)
                compileOnly(libs.ktor.serialization.kotlinx.json)
                compileOnly(libs.pebble)
                compileOnly(libs.libfvad.jni)
                compileOnly(libs.vertexai)
                compileOnly(libs.commons.lang3)
                compileOnly(libs.commons.io)
                compileOnly(libs.jna)
                compileOnly(libs.snakeyaml)
                compileOnly("io.github.funnysaltyfish:partial-json-parser:1.0.2")
                compileOnly("io.github.oshai:kotlin-logging:7.0.0")

                compileOnly("com.jetbrains.intellij.platform:code-style:242.23726.103") {
                    isTransitive = false
                }
                compileOnly("com.jetbrains.intellij.platform:core:243.21565.199") {
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
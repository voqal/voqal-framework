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
            url = uri("https://maven.pkg.github.com/voqal/assistant-framework")
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
                compileOnly(project(":intellij-delegate"))

                compileOnly(libs.openai.client)
                compileOnly(libs.vertx.core)
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
            }
        }
    }
}
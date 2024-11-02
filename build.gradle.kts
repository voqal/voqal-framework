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
}

dependencies {
    commonMainCompileOnly(project(":intellij-delegate"))
    commonMainApi(project(":intellij-delegate"))
}

kotlin {
    jvm()
    js {
        browser()
        binaries.executable()
    }
}
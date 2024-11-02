plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("maven-publish")
}

val projectVersion: String by project

group = "dev.voqal"
version = project.properties["frameworkVersion"] as String? ?: projectVersion

//configure<PublishingExtension> {
//    repositories {
//        maven {
//            name = "GitHubPackages"
//            url = uri("https://maven.pkg.github.com/voqal/assistant-framework")
//            credentials {
//                username = System.getenv("GH_PUBLISH_USERNAME")?.toString()
//                password = System.getenv("GH_PUBLISH_TOKEN")?.toString()
//            }
//        }
//    }
//
//    publishing {
//        publications {
//            create<MavenPublication>("maven") {
//                groupId = project.group.toString()
//                artifactId = "assistant-framework"
//                version = project.version.toString()
//
//                from(components["kotlin"])
//            }
//        }
//    }
//}

repositories {
    mavenCentral()
}

dependencies {
}

kotlin {
    jvm()
    js {
        browser()
        binaries.executable()
    }
}
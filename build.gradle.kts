plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("maven-publish")
}

group = "dev.voqal"
version = "1.0-SNAPSHOT"

//val sourcesJar = tasks.register<Jar>("sourcesJar") {
//    archiveClassifier.set("sources")
//    from(project.the<SourceSetContainer>()["main"].allSource)
//}

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

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = "assistant-framework"
                version = project.version.toString()

                from(components["kotlin"])

//                // Ship the sources jar
//                artifact(sourcesJar)
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
}

kotlin {
    jvm()
}
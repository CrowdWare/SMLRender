plugins {
    kotlin("jvm") version "1.9.22"
    `maven-publish`
}

group = "com.innernet.sml"
version = "0.1.0"

dependencyLocking {
    lockAllConfigurations()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "sml-render"
            version = project.version.toString()
        }
    }
    repositories {
        mavenLocal()
    }
}

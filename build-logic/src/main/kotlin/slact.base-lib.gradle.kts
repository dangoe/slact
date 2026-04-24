// SPDX-License-Identifier: MIT OR Apache-2.0

import org.gradle.accessors.dm.LibrariesForLibs

val libs = the<LibrariesForLibs>()

plugins {
    `java-library`
}

group = "de.dangoe.concurrent.slact"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation(libs.jetbrains.annotations)
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "${project.group}.${project.name}",
            "Implementation-Version" to archiveVersion
        )
    }
}

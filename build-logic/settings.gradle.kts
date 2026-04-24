// SPDX-License-Identifier: MIT OR Apache-2.0

rootProject.name = "build-logic"

pluginManagement {

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {

    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

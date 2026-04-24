// SPDX-License-Identifier: MIT OR Apache-2.0

plugins {
    `java-library`
    id("slact.java-lib")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.junit.jupiter)
    implementation(libs.awaitility)
}

// SPDX-License-Identifier: MIT OR Apache-2.0

plugins {
    `java-library`
    id("slact.java-lib")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence"))
    implementation(project(":testkit"))
    implementation(libs.junit.jupiter)
    implementation(libs.assertj.core)
    implementation(libs.awaitility)
}

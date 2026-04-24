// SPDX-License-Identifier: MIT OR Apache-2.0

plugins {
    java
    id("slact.java-lib")
}

dependencies {

    implementation(libs.slf4j.api)

    testImplementation(project(":testkit"))
    testImplementation(libs.awaitility)
    testImplementation(libs.logback.classic)
}

// SPDX-License-Identifier: MIT OR Apache-2.0

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

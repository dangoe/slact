plugins {
    `java-library`
    id("slact.java-lib")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence-core"))
    implementation(project(":testkit"))
    implementation(libs.junit.jupiter)
    implementation(libs.assertj.core)
    implementation(libs.awaitility)
}

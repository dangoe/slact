plugins {
    `java-library`
    id("slact.java-lib")
}

dependencies {
    api(project(":memory"))
    implementation(libs.junit.jupiter)
    implementation(libs.assertj.core)
}

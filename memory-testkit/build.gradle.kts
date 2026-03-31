plugins {
    `java-library`
    id("slact.java-lib")
}

dependencies {
    api(project(":memory"))
    api(libs.junit.jupiter)
    api(libs.assertj.core)
}

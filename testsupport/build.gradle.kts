plugins {
    `java-library`
    id("slact.java-lib")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.junit.jupiter)
}

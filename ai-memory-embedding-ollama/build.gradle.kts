plugins {
    id("slact.java-lib")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ai-memory-core"))
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

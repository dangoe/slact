plugins {
    java
    id("slact.java-lib")
}

dependencies {
    implementation(libs.slf4j.api)
    implementation(libs.awaitility)
    testImplementation(project(":testkit"))
    testImplementation(libs.awaitility)
    testImplementation(libs.logback.classic)
}

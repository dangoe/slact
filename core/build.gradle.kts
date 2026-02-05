plugins {
    java
    id("slact.java-lib")
}

dependencies {
    implementation(libs.slf4j.api)
    testImplementation(project(":testkit"))
    testImplementation(libs.awaitility)
}

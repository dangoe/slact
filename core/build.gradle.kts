plugins {
    java
    id("slact.java-lib")
}

dependencies {
    implementation(libs.slf4j.api)
    testImplementation(libs.awaitility)
}

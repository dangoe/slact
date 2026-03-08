plugins {
    `java-library`
    id("slact.java-lib")
    id("slact.use-junit5-lib")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

dependencies {
    implementation(project(":core"))

    testImplementation(project(":testkit"))
    testImplementation(libs.awaitility)
    testImplementation(libs.logback.classic)
}

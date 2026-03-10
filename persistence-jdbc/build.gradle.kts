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
    api(project(":persistence"))
    implementation(project(":core"))
    implementation(libs.slf4j.api)
    implementation(libs.hikaricp)

    testImplementation(project(":testkit"))
    testImplementation(libs.awaitility)
    testImplementation(libs.logback.classic)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.liquibase.core)
    testRuntimeOnly(libs.postgresql)
}

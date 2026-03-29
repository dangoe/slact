plugins {
    `java-library`
    id("slact.java-lib")
    id("slact.use-junit5-lib")
    id("slact.integration-test-lib")
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
    testImplementation(project(":persistence-testkit"))
    testImplementation(libs.awaitility)
    testImplementation(libs.logback.classic)

    integrationTestImplementation(libs.testcontainers.junit.jupiter)
    integrationTestImplementation(libs.testcontainers.postgresql)
    integrationTestImplementation(libs.liquibase.core)
    integrationTestRuntimeOnly(libs.postgresql)
}

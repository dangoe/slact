plugins {
    `java-library`
    id("slact.java-lib")
    id("slact.integration-test-lib")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

dependencies {
    api(project(":memory"))
    implementation(libs.neo4j.java.driver)
    implementation(libs.slf4j.api)

    testImplementation(libs.logback.classic)

    integrationTestImplementation(project(":memory-testkit"))
    integrationTestImplementation(libs.testcontainers.junit.jupiter)
    integrationTestImplementation(libs.testcontainers.neo4j)
    integrationTestRuntimeOnly(libs.logback.classic)
}

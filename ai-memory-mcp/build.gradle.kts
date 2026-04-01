plugins {
    `java-library`
    id("slact.java-lib")
}

dependencies {
    api(project(":ai-memory-core"))
    implementation(project(":core"))
    implementation(libs.mcp)
    implementation(libs.slf4j.api)

    testImplementation(project(":core"))
}

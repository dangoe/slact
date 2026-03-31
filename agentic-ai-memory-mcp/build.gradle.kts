plugins {
    `java-library`
    id("slact.java-lib")
}

dependencies {
    api(project(":agentic-ai-memory-core"))
    implementation(project(":core"))
    implementation(libs.mcp)
    implementation(libs.slf4j.api)
}

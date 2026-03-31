plugins {
    `java-library`
    id("slact.java-lib")
}

dependencies {
    api(project(":agentic-ai-memory-core"))
    api(libs.junit.jupiter)
    api(libs.assertj.core)
}

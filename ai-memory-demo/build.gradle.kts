plugins {
    application
    id("slact.java-lib")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ai-memory-core"))
    implementation(project(":ai-memory-neo4j"))
    implementation(libs.neo4j.java.driver)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass.set("de.dangoe.concurrent.slact.ai.memory.demo.MemoryDemoCli")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

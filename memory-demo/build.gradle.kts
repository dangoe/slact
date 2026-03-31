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
    implementation(project(":memory"))
    implementation(project(":memory-neo4j"))
    implementation(libs.neo4j.java.driver)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
}

application {
    mainClass.set("de.dangoe.concurrent.slact.memory.demo.MemoryDemoCli")
}

plugins {
    `java-library`
    id("slact.java-lib")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

dependencies {
    api(project(":persistence"))
    implementation(project(":core"))
    implementation(libs.slf4j.api)
}

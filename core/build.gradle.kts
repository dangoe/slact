plugins {
    id("java")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

group = "de.dangoe.concurrent.slact"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed")
    }
}

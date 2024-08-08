plugins {
    id("java")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

allprojects {

    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("org.slf4j:slf4j-api:2.0.14")
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.assertj:assertj-core:3.26.3")
        testImplementation("org.awaitility:awaitility:4.2.1")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()

        testLogging {
            events("passed")
        }
    }
}

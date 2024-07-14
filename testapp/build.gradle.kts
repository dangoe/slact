plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

group = "de.dangoe.concurrent.slact.testapp"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":core"))
}

application {
    mainClass = "de.dangoe.slacktors.Main"
}

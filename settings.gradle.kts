rootProject.name = "slact"

include("core", "testkit")

project(":core").name = "core"
project(":testkit").name = "testkit"

pluginManagement {
    includeBuild("build-logic")
}

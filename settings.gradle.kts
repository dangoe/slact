rootProject.name = "slact"

include("core", "testkit", "persistence", "persistence-jdbc")

project(":core").name = "core"
project(":testkit").name = "testkit"
project(":persistence").name = "persistence"
project(":persistence-jdbc").name = "persistence-jdbc"

pluginManagement {
    includeBuild("build-logic")
}

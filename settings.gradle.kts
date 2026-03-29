rootProject.name = "slact"

include("core", "testkit", "persistence", "persistence-jdbc", "persistence-testkit")

project(":core").name = "core"
project(":testkit").name = "testkit"
project(":persistence").name = "persistence"
project(":persistence-jdbc").name = "persistence-jdbc"
project(":persistence-testkit").name = "persistence-testkit"

pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "slact"

include("core", "testkit", "persistence", "persistence-jdbc", "persistence-testkit",
        "memory", "memory-neo4j", "memory-mcp")

project(":core").name = "core"
project(":testkit").name = "testkit"
project(":persistence").name = "persistence"
project(":persistence-jdbc").name = "persistence-jdbc"
project(":persistence-testkit").name = "persistence-testkit"
project(":memory").name      = "memory"
project(":memory-neo4j").name = "memory-neo4j"
project(":memory-mcp").name  = "memory-mcp"

pluginManagement {
    includeBuild("build-logic")
}

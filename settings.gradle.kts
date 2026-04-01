rootProject.name = "slact"

include(
    "core",
    "testkit",
    "persistence-core",
    "persistence-jdbc",
    "persistence-testkit",
    "ai-memory-core",
    "ai-memory-neo4j",
    "ai-memory-mcp",
    "ai-memory-embedding-ollama",
    "ai-memory-testkit",
    "ai-memory-demo"
)

project(":core").name = "core"
project(":testkit").name = "testkit"
project(":persistence-core").name = "persistence-core"
project(":persistence-jdbc").name = "persistence-jdbc"
project(":persistence-testkit").name = "persistence-testkit"
project(":ai-memory-core").name = "ai-memory-core"
project(":ai-memory-neo4j").name = "ai-memory-neo4j"
project(":ai-memory-mcp").name = "ai-memory-mcp"
project(":ai-memory-embedding-ollama").name = "ai-memory-embedding-ollama"
project(":ai-memory-testkit").name = "ai-memory-testkit"
project(":ai-memory-demo").name = "ai-memory-demo"

pluginManagement {
    includeBuild("build-logic")
}

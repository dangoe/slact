rootProject.name = "slact"

include(
    "core",
    "testkit",
    "persistence-core",
    "persistence-jdbc",
    "persistence-testkit",
    "agentic-ai-memory-core",
    "agentic-ai-memory-neo4j",
    "agentic-ai-memory-mcp",
    "agentic-ai-memory-testkit",
    "agentic-ai-memory-demo"
)

project(":core").name = "core"
project(":testkit").name = "testkit"
project(":persistence-core").name = "persistence-core"
project(":persistence-jdbc").name = "persistence-jdbc"
project(":persistence-testkit").name = "persistence-testkit"
project(":agentic-ai-memory-core").name = "agentic-ai-memory-core"
project(":agentic-ai-memory-neo4j").name = "agentic-ai-memory-neo4j"
project(":agentic-ai-memory-mcp").name = "agentic-ai-memory-mcp"
project(":agentic-ai-memory-testkit").name = "agentic-ai-memory-testkit"
project(":agentic-ai-memory-demo").name = "agentic-ai-memory-demo"

pluginManagement {
    includeBuild("build-logic")
}

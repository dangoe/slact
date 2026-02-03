rootProject.name = "slact"

include("core", "testsupport")

project(":core").name = "core"
project(":testsupport").name = "testsupport"

pluginManagement {
    includeBuild("build-logic")
}

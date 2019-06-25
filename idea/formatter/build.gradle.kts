
plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compile(project(":compiler:util"))
    compile(project(":compiler:frontend"))
    compileOnly(intellijDep()) { includeJars("jdom", "platform-api", "platform-impl", "extensions", "util") }
    Platform[192].orHigher {
        compileOnly(intellijPluginDep("java")) { includeJars("java-api") }
    }
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}


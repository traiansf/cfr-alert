plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("StationsGenKt")
}

dependencies {
    implementation(project(":infofer-client"))
}

kotlin {
    jvmToolchain(21)
}

// Generator writes to a relative path from the repo root, not from the module dir.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

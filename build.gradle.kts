plugins {
    id("java")
}

group = "experimental.users.edarke.joda"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}


dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.3")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.3")
    implementation("com.google.guava:guava:33.4.8-jre")
    implementation("commons-io:commons-io:2.19.0")
    implementation("org.kohsuke:github-api:2.0-rc.3")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.18")
}

tasks.register("validateJsonWithCue") {
    group = "verification"
    description = "Validates JSON files against a CUE schema"

    val jsonDir = file("data")
    val schemaFile = file("./data/experiment.cue")

    doLast {
        val jsonFiles = jsonDir
            .walk()
            .filter { it.isFile && it.extension == "json" }
            .toList()

        if (jsonFiles.isEmpty()) {
            println("No JSON files found for validation.")
            return@doLast
        }

        jsonFiles.forEach { jsonFile ->
            println("Validating ${jsonFile.name}...")
            val process = ProcessBuilder("cue", "vet", schemaFile.absolutePath, jsonFile.absolutePath,
                "-d", "#Experiment",
                "-t", "filename=${jsonFile.nameWithoutExtension}"
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                println("Validation failed for ${jsonFile.name}:\n$output")
                throw GradleException("CUE validation failed for ${jsonFile.name}")
            } else {
                println("✔ ${jsonFile.name} is valid.")
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn("validateJsonWithCue")
}

plugins {
    java
    id("net.neoforged.moddev") version "2.0.78"
}

group = "rrp"
version = property("mod_version") as String

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

neoForge {
    version = property("neo_version") as String

    runs {
        create("server") {
            server()
            programArguments.add("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", "respublica")
        }
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", "respublica")
        }
    }

    mods {
        create("respublica") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val props = mapOf("mod_version" to project.version.toString(), "mod_id" to project.property("mod_id").toString())
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("RealmsResPublica.jar")
}

tasks.test {
    useJUnitPlatform()
    System.getenv("TMPDIR")?.let { systemProperty("java.io.tmpdir", it) }
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

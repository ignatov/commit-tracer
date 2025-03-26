plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
    kotlin("plugin.serialization") version "1.9.25"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2024.2.5")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add Git4Idea plugin dependency
        bundledPlugin("Git4Idea")
    }

    // Add JSON dependency for YouTrack API
    implementation("org.json:json:20240205")
    
    // Add OkHttp dependency for HiBob API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Add Kotlin serialization for JSON handling
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // Add Kotlin coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
    
    // Add Gson for JSON parsing (email mappings)
    implementation("com.google.code.gson:gson:2.10.1")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "251.*"
        }

        changeNotes = """
      Initial version with repository commit listing and YouTrack integration.
      - Added action to list all commits in the current repository
      - Added Git integration for commit history retrieval
      - Added YouTrack integration to show issue details for issues mentioned in commits
    """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }
    
    // Task to run the HiBob CLI
    register<JavaExec>("runHiBobCli") {
        description = "Runs the HiBob CLI application"
        mainClass.set("com.example.ijcommittracer.HiBobCli")
        classpath = sourceSets["main"].runtimeClasspath
        
        // Pass command line arguments to the application
        args = project.findProperty("cliArgs")?.toString()?.split(" ") ?: listOf()
    }
    
    // Task to run the HiBob CLI with debug flag
    register<JavaExec>("runHiBobCliDebug") {
        description = "Runs the HiBob CLI application with debug flag"
        mainClass.set("com.example.ijcommittracer.HiBobCliKt")
        classpath = sourceSets["main"].runtimeClasspath
        args = listOf("--debug")
    }
    
    // Task to list named lists from HiBob
    register<JavaExec>("hibobLists") {
        description = "Lists all named lists from HiBob API"
        mainClass.set("com.example.ijcommittracer.HiBobCliKt")
        classpath = sourceSets["main"].runtimeClasspath
        args = listOf("--lists", "--debug")
    }
    
    // Task to create a standalone JAR for HiBobCli
    register<Jar>("hibobCliJar") {
        dependsOn(configurations.runtimeClasspath)
        archiveBaseName.set("hibob-cli")
        archiveVersion.set(project.version.toString())
        
        manifest {
            attributes["Main-Class"] = "com.example.ijcommittracer.HiBobCli"
        }
        
        // Include all dependencies in the JAR
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        from(sourceSets["main"].output)
        
        // Exclude unnecessary files from the JAR
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
}

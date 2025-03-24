plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
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
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    
    // Add Gson for JSON serialization/deserialization
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Add Kotlin coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
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
    
    // Task to run the HiBob CLI Test
    register<JavaExec>("runHiBobCliTest") {
        description = "Runs the HiBob CLI Test application"
        mainClass.set("com.example.ijcommittracer.HiBobCliTestKt")
        classpath = sourceSets["main"].runtimeClasspath
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

// =============================================================================
// Dota 2 Draft Assistant - Gradle Build Configuration
// =============================================================================
// Java 21 LTS + JavaFX 21 + Spring Boot 3.2 + SQLite
// Cross-platform packaging via jpackage
// =============================================================================

plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.0.1"
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    jacoco
}

group = "com.dota2assistant"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// =============================================================================
// JavaFX Configuration
// =============================================================================
javafx {
    version = "21.0.1"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

// =============================================================================
// Dependencies
// =============================================================================
dependencies {
    // Spring Boot (without web - desktop app)
    implementation("org.springframework.boot:spring-boot-starter")
    
    // Note: Using Spring Boot's managed logging versions to avoid conflicts
    
    // Database - SQLite
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    
    // JSON Processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.0")
    
    // HTTP Client (built into Java 11+, but we add convenience)
    // Using java.net.http.HttpClient - no external dependency needed
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}

// =============================================================================
// Application Configuration
// =============================================================================
application {
    mainClass.set("com.dota2assistant.Dota2DraftAssistant")
}

// =============================================================================
// Java Compilation
// =============================================================================
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-Xlint:all",
        "-Xlint:-processing"
        // "-Werror" disabled until Phase 1 complete
    ))
}

// =============================================================================
// Testing
// =============================================================================
tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// =============================================================================
// jlink + jpackage Configuration
// =============================================================================
jlink {
    options.addAll(listOf(
        "--strip-debug",
        "--compress", "zip-6",
        "--no-header-files",
        "--no-man-pages"
    ))
    
    launcher {
        name = "Dota2DraftAssistant"
    }
    
    jpackage {
        imageName = "Dota2DraftAssistant"
        installerName = "Dota2DraftAssistant"
        appVersion = project.version.toString().replace("-SNAPSHOT", "")
        vendor = "Dota2Assistant"
        
        // Platform-specific configuration
        val currentOs = org.gradle.internal.os.OperatingSystem.current()
        
        when {
            currentOs.isWindows -> {
                installerType = "msi"
                installerOptions.addAll(listOf(
                    "--win-dir-chooser",
                    "--win-menu",
                    "--win-shortcut",
                    "--win-per-user-install"
                ))
            }
            currentOs.isMacOsX -> {
                installerType = "dmg"
                installerOptions.addAll(listOf(
                    "--mac-package-name", "Dota2DraftAssistant"
                ))
            }
            currentOs.isLinux -> {
                installerType = "deb"
                installerOptions.addAll(listOf(
                    "--linux-shortcut",
                    "--linux-menu-group", "Game"
                ))
            }
        }
    }
}

// =============================================================================
// Custom Tasks
// =============================================================================

// Task to check file sizes (no file > 500 lines)
tasks.register("checkFileSizes") {
    group = "verification"
    description = "Check that no Java file exceeds 500 lines"
    
    doLast {
        val maxLines = 500
        var violations = 0
        
        fileTree("src/main/java") {
            include("**/*.java")
        }.forEach { file ->
            val lineCount = file.readLines().size
            if (lineCount > maxLines) {
                println("WARNING: ${file.relativeTo(projectDir)} has $lineCount lines (max: $maxLines)")
                violations++
            }
        }
        
        if (violations > 0) {
            throw GradleException("$violations file(s) exceed the $maxLines line limit")
        }
        println("✓ All files are within the $maxLines line limit")
    }
}

// Task to check domain layer imports
tasks.register("checkDomainImports") {
    group = "verification"
    description = "Check that domain layer has no framework imports"
    
    doLast {
        val forbiddenImports = listOf(
            "org.springframework",
            "javafx",
            "java.sql",
            "com.fasterxml.jackson"
        )
        var violations = 0
        
        fileTree("src/main/java/com/dota2assistant/domain") {
            include("**/*.java")
        }.forEach { file ->
            val content = file.readText()
            forbiddenImports.forEach { forbidden ->
                if (content.contains("import $forbidden")) {
                    println("ERROR: ${file.relativeTo(projectDir)} imports $forbidden")
                    violations++
                }
            }
        }
        
        if (violations > 0) {
            throw GradleException("Domain layer has $violations forbidden import(s)")
        }
        println("✓ Domain layer is clean of framework imports")
    }
}

// Run checks before build
tasks.named("check") {
    dependsOn("checkFileSizes", "checkDomainImports")
}

// Task to import counter data from OpenDota API
tasks.register<JavaExec>("importCounterData") {
    group = "data"
    description = "Import hero counter data from OpenDota API (~3 minutes)"
    
    mainClass.set("com.dota2assistant.cli.ImportCounterDataCommand")
    classpath = sourceSets["main"].runtimeClasspath
}

// Task to import synergy data from DotaBASED
tasks.register<JavaExec>("importSynergyData") {
    group = "data"
    description = "Import hero synergy data from DotaBASED (~5 seconds)"
    
    mainClass.set("com.dota2assistant.cli.ImportSynergyDataCommand")
    classpath = sourceSets["main"].runtimeClasspath
}

// Task to import all matchup data (counter + synergy)
tasks.register("importAllData") {
    group = "data"
    description = "Import both counter and synergy data"
    dependsOn("importSynergyData")
    // Note: importCounterData takes ~3 mins, run separately if needed
}


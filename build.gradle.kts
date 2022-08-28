import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    java
    kotlin("jvm") version "1.7.10"
    id("net.minecrell.plugin-yml.bukkit") version "0.5.2"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

val simpleName = "Intave"

group = "de.jpx3"

version = "14.4.6"

description = "$simpleName is a cheat detection software, providing fair play"

/*
 * Dependencies
 */

repositories {
    mavenCentral()

    // Spigot
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
}

dependencies {
    // Kotlin stuff
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.7.10")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")

    // Spigot
    compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1-SNAPSHOT")
    compileOnly(fileTree(mapOf("dir" to "libs/", "include" to listOf("*.jar"))))

    // random shit
    compileOnly("org.jetbrains:annotations:23.0.0")
    compileOnly("it.unimi.dsi:fastutil:8.5.8")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))

/*
 * Intave Gradle Tasks
 */
tasks.register("iacClean") {
    group = simpleName
    dependsOn(tasks.clean)
}

tasks.register("iacBuild") {
    group = simpleName
    dependsOn(tasks.build)
}

tasks.register("iacDeploy") {
    group = simpleName
    dependsOn("iacBuild")
    doLast {
        copy {
            from("build/libs/$simpleName.jar")
            into("C:\\Users\\tratt\\Development\\e\\plugins")
        }
    }
}

/*
 * Gradle Task Configuration
 */
tasks {
    build { dependsOn(shadowJar) }

    jar {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        archiveFileName.set("$simpleName.jar")
        manifest {
            attributes("Implementation-Title" to simpleName)
            attributes("Implementation-Version" to project.version)
            attributes("Implementation-Vendor" to "Jpx3")
            attributes("Main-Class" to "de.jpx3.intave.IntaveApplication")
        }
        // TODO: remove kotlin module files in META-INF...
    }

    shadowJar {
        val classifier = "file"
        archiveFileName.set("$simpleName.jar")
        archiveClassifier.set(classifier)
        // TODO: since kotlin stdlib is shaded into the jar, jetbrains annotations is also present
        // despite not being needed...
    }

    compileKotlin { kotlinOptions.jvmTarget = "1.8" }
    compileTestKotlin { kotlinOptions.jvmTarget = "1.8" }
}

/*
 * plugin.yml
 */
bukkit {
    name = simpleName
    authors = listOf("DarkAndBlue", "Jpx3", "vento")
    version = "${rootProject.version}"
    description = "${rootProject.description}"

    main = "de.jpx3.intave.IntavePlugin"
    apiVersion = "1.13"
    softDepend = listOf("ProtocolLib", "ViaVersion")

    commands { register("intave") { aliases = listOf("iac") } }

    defaultPermission = BukkitPluginDescription.Permission.Default.FALSE

    permissions {
        register("intave.bypass") { default = BukkitPluginDescription.Permission.Default.FALSE }
        register("intave.trust.green") { default = BukkitPluginDescription.Permission.Default.OP }
        register("intave.trust.yellow") {
            default = BukkitPluginDescription.Permission.Default.FALSE
        }
        register("intave.trust.orange") {
            default = BukkitPluginDescription.Permission.Default.FALSE
        }
        register("intave.trust.red") { default = BukkitPluginDescription.Permission.Default.FALSE }
        register("intave.trust.darkred") {
            default = BukkitPluginDescription.Permission.Default.FALSE
        }
        register("intave.command") { default = BukkitPluginDescription.Permission.Default.OP }
        register("intave.command.notify") {
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("intave.command.verbose") {
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("intave.command.history") {
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("intave.command.proxy") {
            default = BukkitPluginDescription.Permission.Default.FALSE
        }
        register("intave.command.diagnostics") {
            default = BukkitPluginDescription.Permission.Default.OP
            children =
                listOf(
                    "intave.command.diagnostics.performance",
                    "intave.command.diagnostics.statistics"
                )
        }
        register("intave.command.diagnostics.performance") {
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("intave.command.diagnostics.statistics") {
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("intave.command.internals") {
            default = BukkitPluginDescription.Permission.Default.FALSE
            children =
                listOf(
                    "intave.command.internals.delay",
                    "intave.command.internals.rejoinblock",
                    "intave.command.internals.sendnotify",
                    "intave.command.internals.collectivekick",
                    "intave.command.internals.bot"
                )
        }
        register("intave.command.internals.delay") {
            default = BukkitPluginDescription.Permission.Default.FALSE
        }
        register("intave.command.internals.rejoinblock") {
            default = BukkitPluginDescription.Permission.Default.FALSE
        }
        register("intave.command.internals.sendnotify") {
            default = BukkitPluginDescription.Permission.Default.FALSE
        }
        register("intave.command.internals.collectivekick") {
            default = BukkitPluginDescription.Permission.Default.FALSE
        }
        register("intave.command.internals.bot") {
            default = BukkitPluginDescription.Permission.Default.FALSE
        }
    }
}

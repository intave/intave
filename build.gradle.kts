import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.FALSE
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
import xyz.jpenilla.runpaper.task.RunServerTask

plugins {
  java
  // TODO: uncomment if we actually use Kotlin (requires some thinking before)
  // kotlin("jvm") version "1.7.10"
  id("net.minecrell.plugin-yml.bukkit") version "0.5.2"
  id("com.github.johnrengelman.shadow") version "7.1.2"
  id("xyz.jpenilla.run-paper") version "1.0.6"
}

val simpleName = "Intave"
group = "de.jpx3"
version = "14.5.4"
description = "Cheat detection software, providing fair play"

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
  // Spigot
  compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1-SNAPSHOT")
  // It is important to explicitly define the .jar dependency order, since the order of fileTree
  // is  file system dependent and may lead to compilation errors. If issues occur in the future,
  // it may be needed to create the list explicitly instead of just sorting.
  compileOnly(
    files(fileTree(mapOf("dir" to "libs/", "include" to listOf("*.jar"))).files.sorted())
  )

  // Testing
  testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")

  // random shit
  compileOnly("org.jetbrains:annotations:23.0.0")
  compileOnly("it.unimi.dsi:fastutil:8.5.8")
}

/*
 * plugin.yml
 */
bukkit {
  name = simpleName
  authors = listOf("DarkAndBlue", "Jpx3", "vento", "lennoxlotl", "NotLucky", "Trattue")
  version = "${rootProject.version}"
  description = "${rootProject.description}"

  main = "de.jpx3.intave.IntavePlugin"
  apiVersion = "1.13"
  softDepend = listOf("ProtocolLib", "ViaVersion")

  commands { register("intave") { aliases = listOf("iac") } }

  defaultPermission = FALSE

  permissions {
    register("intave.bypass") { default = FALSE }
    register("intave.trust.green") { default = OP }
    register("intave.trust.yellow") { default = FALSE }
    register("intave.trust.orange") { default = FALSE }
    register("intave.trust.red") { default = FALSE }
    register("intave.trust.darkred") { default = FALSE }
    register("intave.command") { default = OP }
    register("intave.command.notify") { default = OP }
    register("intave.command.verbose") { default = OP }
    register("intave.command.history") { default = OP }
    register("intave.command.proxy") { default = FALSE }
    register("intave.command.diagnostics") {
      default = OP
      children =
        listOf(
          "intave.command.diagnostics.performance",
          "intave.command.diagnostics.statistics"
        )
    }
    register("intave.command.diagnostics.performance") { default = OP }
    register("intave.command.diagnostics.statistics") { default = OP }
    register("intave.command.internals") {
      default = FALSE
      children =
        listOf(
          "intave.command.internals.delay",
          "intave.command.internals.rejoinblock",
          "intave.command.internals.sendnotify",
          "intave.command.internals.collectivekick",
          "intave.command.internals.bot"
        )
    }
    register("intave.command.internals.delay") { default = FALSE }
    register("intave.command.internals.rejoinblock") { default = FALSE }
    register("intave.command.internals.sendnotify") { default = FALSE }
    register("intave.command.internals.collectivekick") { default = FALSE }
    register("intave.command.internals.bot") { default = FALSE }
  }
}

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
      into(TODO("enter custom deployment path, e.g. plugin directory"))
    }
  }
}

run {
  registerServerTask("1.8.8", 8)
  registerServerTask("1.9.4", 8)
  registerServerTask("1.10.2", 8)
  registerServerTask("1.11.2", 8)
  registerServerTask("1.12.2", 8)
  registerServerTask("1.13.2", 8)
  registerServerTask("1.14.4", 8)
  registerServerTask("1.15.2", 8)
  registerServerTask("1.16.5", 8)
  registerServerTask("1.17.1", 16)
  registerServerTask("1.18", 17)
  registerServerTask("1.18.2", 17)
  registerServerTask("1.19", 17)
  registerServerTask("1.19.2", 17)
}

fun registerServerTask(serverVersion: String, javaVersion: Int) {
  tasks.register<RunServerTask>("paper_${serverVersion}") {
    group = simpleName
    dependsOn("iacBuild")
    pluginJars.from("build/libs/$simpleName.jar")
    minecraftVersion(serverVersion)
    runDirectory(File("runs/paper_${serverVersion}-j$javaVersion"))
    jvmArgs("-Dcom.mojang.eula.agree=true") // speak with our lawyer about this!!
    javaLauncher.set(
      project.javaToolchains.launcherFor {
        // Sets the JDK version for the Minecraft server, Intave is still built using Java
        // 1.8
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
      }
    )
  }
}

/*
 * Gradle Task Configuration
 */
java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))

tasks {
  // TODO: uncomment if we actually use Kotlin
  // compileKotlin { kotlinOptions.jvmTarget = "1.8" }
  // compileTestKotlin { kotlinOptions.jvmTarget = "1.8" }

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
  }

  shadowJar {
    val classifier = "file"
    archiveFileName.set("$simpleName.jar")
    archiveClassifier.set(classifier)
  }
}

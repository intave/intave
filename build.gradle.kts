import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.*
import xyz.jpenilla.runpaper.task.RunServer

plugins {
  java
  id("com.github.gmazzo.buildconfig") version "4.1.2"
  id("net.minecrell.plugin-yml.bukkit") version "0.5.2"
  id("com.github.johnrengelman.shadow") version "7.1.2"
  id("xyz.jpenilla.run-paper") version "2.1.0"
}

val simpleName = "Intave"
group = "de.jpx3"
version = "14.6.4"
description = "Cheat detection software, providing fair play"

/*
 * Dependencies
 */
repositories {
  mavenCentral()
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
  compileOnly("org.jetbrains:annotations:23.1.0")
  compileOnly("it.unimi.dsi:fastutil:8.5.11")

  // smile
  compileOnly("com.github.haifengl:smile-base:3.0.1")
  compileOnly("com.github.haifengl:smile-core:3.0.1")

  // add bytedeco
  compileOnly("org.bytedeco:openblas:0.3.23-1.5.9")
  compileOnly("org.bytedeco:openblas-platform:0.3.23-1.5.9")
  compileOnly("org.bytedeco:javacpp:1.5.9")
  compileOnly("org.bytedeco:javacpp-presets:1.5.9")
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
    register("intave.command.cps") { default = OP }
    register("intave.command.proxy") { default = FALSE }
    register("intave.command.noupdate") { default = FALSE }
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

tasks.register("production") {
  group = "deploy"
  dependsOn(tasks.build)
  buildConfigFieldSafe("boolean", "PRODUCTION", "true")
  dumpBuildConfig()
}

tasks.register("gomme") {
  group = "deploy"
  dependsOn(tasks.build)
  buildConfigFieldSafe("boolean", "GOMME", "true")
  dumpBuildConfig()
}

/*
 * IntaveSettings build config
 */
buildConfig {
  className("IntaveBuildConfig")
  packageName("de.jpx3.intave")
  useJavaOutput()

  buildConfigFieldSafe("boolean", "PRODUCTION", "false");
  buildConfigFieldSafe("boolean", "GOMME", "false")
  buildConfigFieldSafe("String", "VERSION", "\"${rootProject.version}\"")
}

fun buildConfigFieldSafe(type: String, name: String, value: String) {
  val buildConfig = buildConfig
  val buildConfigFields = buildConfig.buildConfigFields
  buildConfigFields.removeIf { it.name == name }
  buildConfig.buildConfigField(type, name, value)
}

fun dumpBuildConfig() {
  val buildConfig = buildConfig
  val buildConfigFields = buildConfig.buildConfigFields
  println(">> BuildConfig:")
  buildConfigFields.forEach { println("  ${it.name} = ${it.value.get()}") }
}

val serverVersions = mapOf(
  Pair("1.8.8", 8),
  Pair("1.9.4", 8),
  Pair("1.10.2", 8),
  Pair("1.11.2", 8),
  Pair("1.12.2", 8),
  Pair("1.13.2", 11),
  Pair("1.14.4", 11),
  Pair("1.15.2", 11),
  Pair("1.16.5", 16),
  Pair("1.17.1", 16),
  Pair("1.18.2", 17),
  Pair("1.19.4", 17),
  Pair("1.20.1", 17),
)

run {
  serverVersions.forEach { server, java ->
    registerTestTask(server, java)
    registerServerTask(server, java)
  }
}

fun registerTestTask(serverVersion: String, javaVersion: Int) {
  tasks.register<RunServer>("test_${serverVersion}") {
    group = simpleName
    dependsOn("build")
    pluginJars.from("build/libs/$simpleName.jar")
    minecraftVersion(serverVersion)
    runDirectory(File("runs/test_${serverVersion}-j$javaVersion"))
    jvmArgs("-Dcom.mojang.eula.agree=true")
    jvmArgs("-Dintave.test.success=shutdown")
    javaLauncher.set(
      project.javaToolchains.launcherFor {
        // Sets the JDK version for the Minecraft server, Intave is still built using Java
        // 1.8
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
      }
    )
  }
}

run {
  registerTestAllTask()
}

fun registerTestAllTask() {
  tasks.register("test_all") {
    group = simpleName
    dependsOn(serverVersions.keys.map { "test_$it" })
  }
}

fun registerServerTask(serverVersion: String, javaVersion: Int) {
  tasks.register<RunServer>("run_${serverVersion}") {
    group = simpleName
    dependsOn("build")
    pluginJars.from("build/libs/$simpleName.jar")
    minecraftVersion(serverVersion)
    runDirectory(File("runs/paper_${serverVersion}-j$javaVersion"))
    jvmArgs("-Dcom.mojang.eula.agree=true")
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

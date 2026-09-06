/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.FALSE
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
import xyz.jpenilla.runpaper.task.RunServer
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
  java
  jacoco
  id("com.github.gmazzo.buildconfig") version "6.0.9"
  id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
  id("com.gradleup.shadow") version "9.4.1"
  id("xyz.jpenilla.run-paper") version "3.1.0"
}

val gitTag by lazy {
  try {
    providers.exec {
      commandLine("git", "describe", "--tags", "--abbrev=0")
    }.standardOutput.asText.get().trim()
  } catch (e: Exception) {
    "dev-snapshot"
  }
}

val gitCommitHash by lazy {
  providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
  }.standardOutput.asText.get().trim()
}

val simpleName = "Intave"
group = "de.jpx3"
version = "$gitTag-$gitCommitHash"
description = "Automated cheat detection and prevention"

object IntaveTaskGroups {
  const val SERVER_RUNS = "Intave - Server Runs"
  const val SERVER_TESTS = "Intave - Server Tests"
  const val CLIENTS = "Intave - Clients"
  const val BENCHMARKS = "Intave - Benchmarks"
}

/*
 * Dependencies
 */
repositories {
  mavenCentral()
  maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
  maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
  maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
  maven { url = uri("https://oss.sonatype.org/content/repositories/central") }
  maven("https://repo.opencollab.dev/maven-snapshots")
  maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
  // Spigot
  compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1-SNAPSHOT")
  compileOnly("dev.folia:folia-api:1.20.1-R0.1-SNAPSHOT")
  // It is important to explicitly define the .jar dependency order, since the order of fileTree
  // is  file system dependent and may lead to compilation errors. If issues occur in the future,
  // it may be needed to create the list explicitly instead of just sorting.
  compileOnly(
    files(fileTree(mapOf("dir" to "libs/", "include" to listOf("*.jar"))).files.sorted())
  )

  // Bundled libraries.
  implementation("ac.intave:samples:0.0.8") { isTransitive = false }
  implementation("ac.intave:cloud-protocol:0.0.6") { isTransitive = false }
  implementation("it.unimi.dsi:fastutil:8.5.12")
  implementation("net.bytebuddy:byte-buddy:1.18.2")
  implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
  implementation("com.github.luben:zstd-jni:1.5.7-12")

  // Server-provided APIs and compile-time annotations.
  compileOnly("org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT")
  compileOnly("org.jetbrains:annotations:23.1.0")
  compileOnly("org.geysermc.floodgate:api:2.0-SNAPSHOT")
  compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")

  // Test environment.
  testImplementation("org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT")
  testImplementation("net.dmulloy2:ProtocolLib:5.4.0")
  testImplementation("io.netty:netty-all:4.2.15.Final")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val benchmarkSourceSet = sourceSets.create("bench") {
  java.srcDir("src/bench/java")
  compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
  runtimeClasspath += output + compileClasspath
}

configurations[benchmarkSourceSet.implementationConfigurationName].extendsFrom(
  configurations.testImplementation.get()
)
configurations[benchmarkSourceSet.runtimeOnlyConfigurationName].extendsFrom(
  configurations.testRuntimeOnly.get()
)

fun registerBenchmarkTask(
  taskName: String,
  descriptionText: String,
  mainClassName: String,
) {
  tasks.register<JavaExec>(taskName) {
    group = IntaveTaskGroups.BENCHMARKS
    description = descriptionText
    dependsOn(benchmarkSourceSet.classesTaskName)
    classpath = benchmarkSourceSet.runtimeClasspath
    mainClass.set(mainClassName)
    javaLauncher.set(
      project.javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
      }
    )

    val argumentText = providers.gradleProperty("$taskName.args")
    doFirst {
      argumentText.orNull
        ?.takeIf(String::isNotBlank)
        ?.let { args(it.trim().split(Regex("\\s+"))) }
    }
  }
}

registerBenchmarkTask(
  "benchmarkPhysics",
  "Runs the standard movement physics simulator benchmark.",
  "de.jpx3.intave.benchmark.SimulatorBenchmark",
)

registerBenchmarkTask(
  "benchmarkPhysicsMemory",
  "Runs the movement physics simulator memory benchmark.",
  "de.jpx3.intave.benchmark.SimulatorMemoryBenchmark",
)

/*
 * plugin.yml
 */
bukkit {
  name = simpleName
  foliaSupported = true
  authors = listOf("DarkAndBlue", "Jpx3", "vento", "vxcus", "lennoxlotl", "NotLucky", "Trattue")
  version = "${rootProject.version}"
  description = "${rootProject.description}"

  main = "de.jpx3.intave.IntavePlugin"
  apiVersion = "1.13"
  softDepend = listOf("packetevents", "ProtocolLib", "ViaVersion")

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
    register("intave.command.combatmodifiers") { default = OP }
    register("intave.command.cps") { default = OP }
    register("intave.command.check") { default = OP }
    register("intave.command.cloud") { default = OP }
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
 * Special-purpose server runs
 */

tasks.register<RunServer>("runAuthTest1_8_8") {
  group = IntaveTaskGroups.SERVER_RUNS
  description = "Runs the authentication test server on Minecraft 1.8.8"
  dependsOn(tasks.build)
  buildConfigFieldSafe("boolean", "PRODUCTION", "true")
  buildConfigFieldSafe("boolean", "AUTHTEST", "true")
  dumpBuildConfig()

  pluginJars.from(tasks.named("shadowJar"))
  minecraftVersion("1.8.8")
  runDirectory(File("runs/authtest"))
  jvmArgs("-Dcom.mojang.eula.agree=true")
//  jvmArgs("-Dintave.test.success=shutdown")
  javaLauncher.set(
    project.javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(17))
    }
  )
}

tasks.register<RunServer>("runGommeTest1_8_8") {
  group = IntaveTaskGroups.SERVER_RUNS
  description = "Runs the Gomme test server on Minecraft 1.8.8"
  dependsOn(tasks.build)
  buildConfigFieldSafe("boolean", "GOMME", "true")
  dumpBuildConfig()

  pluginJars.from(tasks.named("shadowJar"))
  minecraftVersion("1.8.8")
  runDirectory(File("runs/gommetest"))
  jvmArgs("-Dcom.mojang.eula.agree=true")
//  jvmArgs("-Dintave.test.success=shutdown")
  javaLauncher.set(
    project.javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(8))
    }
  )
}


tasks.register<RunServer>("runAuthTest1_20_1") {
  group = IntaveTaskGroups.SERVER_RUNS
  description = "Runs the authentication test server on Minecraft 1.20.1"
  dependsOn(tasks.build)
  buildConfigFieldSafe("boolean", "PRODUCTION", "true")
  buildConfigFieldSafe("boolean", "AUTHTEST", "true")
  dumpBuildConfig()

  pluginJars.from(tasks.named("shadowJar"))
  minecraftVersion("1.20.1")
  runDirectory(File("runs/authtest_1.20.1"))
  jvmArgs("-Dcom.mojang.eula.agree=true")
//  jvmArgs("-Dintave.test.success=shutdown")
  javaLauncher.set(
    project.javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(17))
    }
  )
}

/*
 * IntaveSettings build config
 */
buildConfig {
  className("IntaveBuildConfig")
  packageName("de.jpx3.intave")
  useJavaOutput()

  buildConfigFieldSafe("boolean", "PRODUCTION", "false");
  buildConfigFieldSafe("boolean", "AUTHTEST", "false");
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

val paperRunConfigs = mapOf(
  Pair("1.8.8", 17),
  Pair("1.9.4", 8),
  Pair("1.12.2", 17),
  Pair("1.14.4", 11),
  Pair("1.15.2", 11),
  Pair("1.16.5", 16),
  Pair("1.17.1", 16),
  Pair("1.18.2", 17),
  Pair("1.19.4", 17),
  Pair("1.20", 17),
  Pair("1.20.1", 17),
  Pair("1.20.2", 17),
  Pair("1.20.4", 21),
  Pair("1.21.1", 21),
  Pair("1.21.3", 21),
  Pair("1.21.4", 21),
  Pair("1.21.7", 21),
  Pair("1.21.11", 25),
  Pair("26.1.2", 25),
  Pair("26.2", 25),
)

val foliaRunConfigs = mapOf(
  Pair("26.1.2", 25)
)

val protocolLibPreloadVersions = setOf("1.21.11", "26.1.2", "26.2")
val protocolLibDevUrl = "https://github.com/dmulloy2/ProtocolLib/releases/download/dev-build/ProtocolLib.jar"

data class McpRebornJvm(
  val gradleJava: Int,
  val minecraftJava: Int = gradleJava,
  val gradleHeap: String? = null,
  val compilerHeap: String? = null,
)

data class McpRebornClientConfig(
  val branch: String,
  val revision: String,
  val mcpVersion: String,
  val mappingsChannel: String,
  val mappingsVersion: String,
  val jvm: McpRebornJvm,
)

val mcpJvm8 = McpRebornJvm(8, gradleHeap = "2G")
val mcpJvm16 = McpRebornJvm(16, gradleHeap = "3G")
val mcpJvm17Legacy = McpRebornJvm(17, gradleHeap = "4G")
val mcpJvm17 = McpRebornJvm(17)
val mcpJvm21 = McpRebornJvm(21)
val mcpJvm25 = McpRebornJvm(21, 25, compilerHeap = "8G")

fun mcpClient(
  version: String,
  revision: String,
  mcpVersion: String,
  jvm: McpRebornJvm,
  snapshotMappings: String? = null,
) = version to McpRebornClientConfig(
  branch = version.split(".").take(2).joinToString("."),
  revision = revision,
  mcpVersion = mcpVersion,
  mappingsChannel = if (snapshotMappings == null) "official" else "snapshot",
  mappingsVersion = snapshotMappings ?: version,
  jvm = jvm,
)

val mcpRebornClientConfigs = mapOf(
  mcpClient("1.14.4", "b4356e384655b0a1f57a480f7481a1895c866f85", "20190829.143755", mcpJvm8,
    snapshotMappings = "20190719-1.14.3"),
  mcpClient("1.15.2", "61643b5d7e8d7e85e631fa3f23b9a900121ebfd8", "20200515.085601", mcpJvm8,
    snapshotMappings = "20200622-1.15.1"),
  mcpClient("1.16.5", "1e71be5bd4c49bc4d6ab0ee559c31b298b7697a3", "20210115.111550", mcpJvm8,
    snapshotMappings = "20201028-1.16.3"),
  mcpClient("1.17.1", "68da7f9f96fbba6ecd3e8f8a0429640264484e43", "20210706.113038", mcpJvm16),
  mcpClient("1.18.2", "46fa56a19fbe8238382e08c653505fcada09cf7d", "20220228.144236", mcpJvm17Legacy),
  mcpClient("1.19.4", "03720bf536cc102f57ba6ce843991a4d93654ff4", "20230314.122934", mcpJvm17Legacy),
  mcpClient("1.20", "0dc201b21e6285038a6644a987ddca69c44c1682", "20230608.053357", mcpJvm17Legacy),
  mcpClient("1.20.1", "fced317528ef365b3030363f7e7e01085fec5605", "20230612.114412", mcpJvm17),
  mcpClient("1.20.2", "6520faec73a73bc86912951194878f16b2bdc35a", "20230921.100330", mcpJvm17),
  mcpClient("1.20.4", "6520faec73a73bc86912951194878f16b2bdc35a", "20231207.112700", mcpJvm17),
  mcpClient("1.21.1", "f862df254fc3b29b0404ddbd97ee5ee94d92b7b6", "20240808.132146", mcpJvm21),
  mcpClient("1.21.2", "f862df254fc3b29b0404ddbd97ee5ee94d92b7b6", "20241025.104818", mcpJvm21),
  mcpClient("1.21.3", "f862df254fc3b29b0404ddbd97ee5ee94d92b7b6", "20241025.112443", mcpJvm21),
  mcpClient("1.21.4", "f862df254fc3b29b0404ddbd97ee5ee94d92b7b6", "20241203.143248", mcpJvm21),
  mcpClient("1.21.5", "870a6f02c0edc70a94646d6799a2e9f5fc825ac6", "20250325.155543", mcpJvm21),
  mcpClient("1.21.6", "887cf0bcff3b7d8d5384b99d45611de25d3780f8", "20250618.020446", mcpJvm21),
  mcpClient("1.21.7", "7f2d312ff0de57d4404667f42e765fd9460749c2", "20250630.104312", mcpJvm21),
  mcpClient("1.21.8", "763bd65d34646a1b9625f5b9705aa7aad2ba2688", "20250717.105350", mcpJvm21),
  mcpClient("1.21.9", "d38cf27335ab04f8585d0c9a5e09f9fdce0be999", "20250930.103108", mcpJvm21),
  mcpClient("1.21.10", "fe0756535e9b317dcc77a4101cefa181d3f20977", "20251007.101210", mcpJvm21),
  mcpClient("1.21.11", "96335c336964e51c7c4392afb33790b045630078", "20251209.095502", mcpJvm21),
  mcpClient("26.1.2", "84820b4daefaf79ec6238840e4be9fb736196398", "20260409.101008", mcpJvm25),
  mcpClient("26.2", "727d72ffc66bcdf1a8c16ee92b120db2eaa46e26", "20260616.103818", mcpJvm25),
)

run {
  val clientVersions = paperRunConfigs.keys.filter(::supportsMcpRebornClient)
  val missingClientConfigs = clientVersions.filterNot(mcpRebornClientConfigs::containsKey)
  check(missingClientConfigs.isEmpty()) {
    "Missing MCP-Reborn client configuration for: ${missingClientConfigs.joinToString()}"
  }

  paperRunConfigs.forEach { (server, java) ->
    registerPaperTestTask(server, java)
    registerPaperRunTask(server, java)
  }
  foliaRunConfigs.forEach { (server, java) ->
    registerFoliaRunTask(server, java)
  }
  registerLeafRunTask(
    serverVersion = "1.21.8",
    build = 175,
    javaVersion = 21,
    expectedSha256 = "6b12678404efd09b353911df60a09724d4732a25725168e77b72e5d07a35378e",
  )
  mcpRebornClientConfigs.forEach { (client, config) ->
    run {
      registerMcpRebornClientTasks(client, config)
    }
  }

  tasks.register("buildAllClients") {
    group = IntaveTaskGroups.CLIENTS
    description = "Builds every explicitly configured MCP-Reborn client"
    dependsOn(mcpRebornClientConfigs.keys.map { "buildClient${it.taskSuffix()}" })
  }
}

fun String.taskSuffix(): String = replace(".", "_").replace("-", "_")

fun sha256(file: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().buffered().use { input ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      digest.update(buffer, 0, count)
    }
  }
  return digest.digest().joinToString("") {
    (it.toInt() and 0xff).toString(16).padStart(2, '0')
  }
}

fun supportsMcpRebornClient(minecraftVersion: String): Boolean {
  val (major, minor) = minecraftVersion.split(".").map { it.toInt() }
  return major > 1 || major == 1 && minor >= 14
}

fun registerMcpRebornClientTasks(
  minecraftVersion: String,
  clientConfig: McpRebornClientConfig,
) {
  val taskSuffix = minecraftVersion.taskSuffix()
  val clientDirectory = layout.projectDirectory.dir("client/$minecraftVersion").asFile
  val clientBuildFile = clientDirectory.resolve("build.gradle")
  val clientGitDirectory = clientDirectory.resolve(".git")
  val generatedSourcesDirectory = clientDirectory.resolve("src/main/java")

  val cloneTask = tasks.register<Exec>("cloneMcpRebornClient_$taskSuffix") {
    description = "Clones MCP-Reborn $minecraftVersion into client/$minecraftVersion"
    workingDir(layout.projectDirectory.asFile)
    commandLine(
      "git", "clone", "--no-checkout", "--single-branch", "--branch", clientConfig.branch,
      "https://github.com/Hexeption/MCP-Reborn.git", clientDirectory.absolutePath,
    )
    onlyIf { !clientGitDirectory.isDirectory }
    doFirst {
      if (clientDirectory.exists() && !clientDirectory.isDirectory) {
        throw GradleException("$clientDirectory exists but is not a directory")
      }
      if (clientDirectory.isDirectory && !clientGitDirectory.isDirectory &&
        !clientDirectory.list().isNullOrEmpty()
      ) {
        throw GradleException("$clientDirectory is not empty and is not a Git checkout")
      }
      clientDirectory.parentFile.mkdirs()
    }
  }

  val checkoutTask = tasks.register<Exec>("checkoutMcpRebornClient_$taskSuffix") {
    description = "Checks out the pinned MCP-Reborn source template for $minecraftVersion"
    dependsOn(cloneTask)
    workingDir(clientDirectory)
    commandLine("git", "checkout", "--detach", clientConfig.revision)
    onlyIf { !clientBuildFile.isFile }
    doFirst {
      if (!clientGitDirectory.isDirectory) {
        throw GradleException(
          "Missing MCP-Reborn Git checkout at $clientDirectory; remove an incomplete " +
            "directory and retry"
        )
      }
    }
  }

  val configureTask = tasks.register("configureMcpRebornClient_$taskSuffix") {
    description = "Configures MCP-Reborn for Minecraft $minecraftVersion"
    dependsOn(checkoutTask)
    doLast {
      val wrapper = mcpRebornGradleWrapper(clientDirectory)
      if (!clientGitDirectory.isDirectory || !clientBuildFile.isFile || !wrapper.isFile) {
        throw GradleException("$clientDirectory is not a complete MCP-Reborn checkout")
      }
      val origin = providers.exec {
        workingDir(clientDirectory)
        commandLine("git", "remote", "get-url", "origin")
      }.standardOutput.asText.get().trim()
      if (!isMcpRebornOrigin(origin)) {
        throw GradleException(
          "$clientDirectory has unexpected Git origin '$origin'; expected Hexeption/MCP-Reborn"
        )
      }
      val containsPinnedRevision = providers.exec {
        workingDir(clientDirectory)
        commandLine("git", "merge-base", "--is-ancestor", clientConfig.revision, "HEAD")
        isIgnoreExitValue = true
      }.result.get().exitValue == 0
      if (!containsPinnedRevision) {
        throw GradleException(
          "$clientDirectory is not based on the pinned MCP-Reborn revision " +
            clientConfig.revision
        )
      }
      configureMcpRebornBuild(clientBuildFile, minecraftVersion, clientConfig)
    }
  }

  val setupTask = registerMcpRebornGradleTask(
    "setupMcpRebornClient_$taskSuffix", "Generates Minecraft $minecraftVersion sources",
    configureTask, clientDirectory, clientConfig, "setup",
  ) {
    onlyIf("decompiled client sources do not exist yet") {
      !generatedSourcesDirectory
        .walkTopDown()
        .any { sourceFile -> sourceFile.isFile && sourceFile.extension == "java" }
    }
  }

  val buildTask = registerMcpRebornGradleTask(
    "buildClient$taskSuffix", "Builds the Minecraft $minecraftVersion client",
    setupTask, clientDirectory, clientConfig, "build",
  ) {
    group = IntaveTaskGroups.CLIENTS
  }

  registerMcpRebornGradleTask(
    "runClient$taskSuffix", "Runs the Minecraft $minecraftVersion client",
    buildTask, clientDirectory, clientConfig, "runclient",
  ) {
    group = IntaveTaskGroups.CLIENTS
  }
}

fun registerMcpRebornGradleTask(
  name: String,
  description: String,
  dependency: Any,
  clientDirectory: File,
  clientConfig: McpRebornClientConfig,
  nestedTask: String,
  taskConfiguration: Exec.() -> Unit,
) = tasks.register<Exec>(name) {
  this.description = description
  dependsOn(dependency)
  configureMcpRebornGradleExec(clientDirectory, clientConfig, nestedTask)
  taskConfiguration()
}

fun Exec.configureMcpRebornGradleExec(
  clientDirectory: File,
  clientConfig: McpRebornClientConfig,
  vararg nestedTasks: String,
) {
  val gradleJavaLauncher = project.javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(clientConfig.jvm.gradleJava))
  }
  val minecraftJavaLauncher = project.javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(clientConfig.jvm.minecraftJava))
  }

  workingDir(clientDirectory)
  doFirst {
    val wrapper = mcpRebornGradleWrapper(clientDirectory)
    val gradleJavaHome = gradleJavaLauncher.get().metadata.installationPath.asFile.absolutePath
    val minecraftJavaHome = minecraftJavaLauncher.get().metadata.installationPath.asFile.absolutePath
    val gradleArguments = mutableListOf(
      "--no-daemon",
      "-Dorg.gradle.java.installations.paths=$minecraftJavaHome",
    )
    clientConfig.jvm.gradleHeap?.let { gradleHeap ->
      gradleArguments += "-Dorg.gradle.jvmargs=-Xmx$gradleHeap"
    }
    gradleArguments += nestedTasks

    environment("JAVA_HOME", gradleJavaHome)
    val command = if (isWindows()) listOf("cmd.exe", "/d", "/c") else listOf("sh")
    commandLine(command + wrapper.absolutePath + gradleArguments)
  }
}

fun configureMcpRebornBuild(
  buildFile: File,
  minecraftVersion: String,
  clientConfig: McpRebornClientConfig,
) {
  val originalSource = buildFile.readText()
  var configuredSource = originalSource
  mapOf(
    "minecraft_version" to minecraftVersion,
    "mcp_version" to clientConfig.mcpVersion,
    "mappings_channel" to clientConfig.mappingsChannel,
    "mappings_version" to clientConfig.mappingsVersion,
  ).forEach { (name, value) ->
    val assignment = Regex("""(?m)^(\s*${Regex.escape(name)}\s*=\s*)(['"])[^'"]*\2""")
    val matches = assignment.findAll(configuredSource).toList()
    if (matches.size != 1) {
      throw GradleException(
        "Expected exactly one $name assignment in $buildFile, found ${matches.size}"
      )
    }
    val match = matches.single()
    val quote = match.groupValues[2]
    configuredSource = configuredSource.replaceRange(
      match.range,
      "${match.groupValues[1]}$quote$value$quote",
    )
  }

  clientConfig.jvm.compilerHeap?.let { maxHeap ->
    val startMarker = "// openintave-client-compiler-memory-start"
    val endMarker = "// openintave-client-compiler-memory-end"
    val managedBlock = """
      $startMarker
      tasks.withType(org.gradle.api.tasks.compile.JavaCompile).configureEach {
          options.fork = true
          options.forkOptions.memoryMaximumSize = '$maxHeap'
      }
      $endMarker
    """.trimIndent()
    val existingBlock = Regex(
      """(?s)\r?\n?${Regex.escape(startMarker)}.*?${Regex.escape(endMarker)}"""
    ).find(configuredSource)
    configuredSource = if (existingBlock == null) {
      "${configuredSource.trimEnd()}\n\n$managedBlock\n"
    } else {
      configuredSource.replaceRange(existingBlock.range, "\n\n$managedBlock")
    }
  }

  if (configuredSource != originalSource) {
    buildFile.writeText(configuredSource)
  }
}

fun isWindows(): Boolean =
  System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

fun mcpRebornGradleWrapper(clientDirectory: File): File =
  clientDirectory.resolve(if (isWindows()) "gradlew.bat" else "gradlew")

fun isMcpRebornOrigin(origin: String): Boolean {
  val normalizedOrigin = origin
    .trim()
    .trimEnd('/')
    .removeSuffix(".git")
    .lowercase()
  return normalizedOrigin.endsWith("github.com/hexeption/mcp-reborn") ||
    normalizedOrigin.endsWith("github.com:hexeption/mcp-reborn")
}

fun registerPaperTestTask(serverVersion: String, javaVersion: Int) {
  tasks.register<RunServer>("testPaper${serverVersion.taskSuffix()}") {
    group = IntaveTaskGroups.SERVER_TESTS
    description = "Runs the Intave server test on Paper $serverVersion"
    dependsOn("shadowJar")
    pluginJars.from(tasks.named("shadowJar"))
    minecraftVersion(serverVersion)
    // Minecraft 1.8.8 requires special patches to work with Java 17
    if (serverVersion == "1.8.8") {
      serverJar(File("libs/servers/panda-1.8.8.jar"))
    }
    if (serverVersion == "1.9.4") {
      serverJar(File("libs/servers/spigot-1.9.4.jar"))
    }
    if (serverVersion == "1.21.7") {
      serverJar(File("libs/servers/paper-1.21.7-15.jar"))
    }
    if (serverVersion in protocolLibPreloadVersions) {
      downloadPlugins {
        url(protocolLibDevUrl)
      }
    }
    runDirectory(File("runs/test_${serverVersion}-j$javaVersion"))
    jvmArgs("-Dcom.mojang.eula.agree=true")
    jvmArgs("-Dintave.test.success=shutdown")
    javaLauncher.set(
      project.javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
      }
    )
  }
}

run {
  registerTestAllTask()
}

fun registerTestAllTask() {
  tasks.register("testAllPaperVersions") {
    group = IntaveTaskGroups.SERVER_TESTS
    description = "Runs the Intave server test on every configured Paper version"
    dependsOn(paperRunConfigs.keys.map { "testPaper${it.taskSuffix()}" })
  }
}

fun registerPaperRunTask(serverVersion: String, javaVersion: Int) {
  tasks.register<RunServer>("runPaper${serverVersion.taskSuffix()}") {
    group = IntaveTaskGroups.SERVER_RUNS
    description = "Runs Intave on Paper $serverVersion"
    dependsOn("shadowJar")
    pluginJars.from(tasks.named("shadowJar"))
    minecraftVersion(serverVersion)
    // Minecraft 1.8.8 requires special patches to work with Java 17
    if (serverVersion == "1.8.8") {
      serverJar(File("libs/servers/panda-1.8.8.jar"))
    }
    if (serverVersion == "1.9.4") {
      serverJar(File("libs/servers/spigot-1.9.4.jar"))
    }
    if (serverVersion == "1.21.7") {
      serverJar(File("libs/servers/paper-1.21.7-15.jar"))
    }
    downloadPlugins {
      if (serverVersion in protocolLibPreloadVersions) {
        url(protocolLibDevUrl)
      }
      modrinth("viaversion", "5.11.0")
      modrinth("viabackwards", "5.11.0")
    }
    runDirectory(File("runs/paper_${serverVersion}-j$javaVersion"))
    jvmArgs("-Dcom.mojang.eula.agree=true")
    // set online mode to false
    args("-o", "false")
    javaLauncher.set(
      project.javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
      }
    )
  }
}

fun registerFoliaRunTask(serverVersion: String, javaVersion: Int) {
  runPaper.folia.registerTask({
    group = IntaveTaskGroups.SERVER_RUNS
    description = "Runs Intave on Folia $serverVersion"
    dependsOn("shadowJar")
    pluginJars.from(tasks.named("shadowJar"))
    minecraftVersion(serverVersion)
    runDirectory(File("runs/folia_${serverVersion}-j$javaVersion"))
    jvmArgs("-Dcom.mojang.eula.agree=true")
    args("-o", "false")
    javaLauncher.set(
      project.javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
      }
    )
  });
}

fun registerLeafRunTask(
  serverVersion: String,
  build: Int,
  javaVersion: Int,
  expectedSha256: String,
) {
  val taskSuffix = serverVersion.taskSuffix()
  val jarName = "leaf-$serverVersion-$build.jar"
  val downloadUrl =
    "https://github.com/Winds-Studio/Leaf/releases/download/ver-$serverVersion/$jarName"
  val leafServerJar = layout.projectDirectory.file(".gradle/run-leaf/$jarName")

  val downloadTask = tasks.register("downloadLeaf$taskSuffix") {
    description = "Downloads and verifies Leaf $serverVersion build $build"
    inputs.property("downloadUrl", downloadUrl)
    inputs.property("sha256", expectedSha256)
    outputs.file(leafServerJar)
    outputs.upToDateWhen {
      val serverJar = leafServerJar.asFile
      serverJar.isFile && sha256(serverJar) == expectedSha256
    }

    doLast {
      val serverJar = leafServerJar.asFile
      val partialJar = serverJar.resolveSibling("${serverJar.name}.part")
      serverJar.parentFile.mkdirs()
      partialJar.delete()

      try {
        val connection = URI(downloadUrl).toURL().openConnection().apply {
          connectTimeout = 30_000
          readTimeout = 60_000
          setRequestProperty("User-Agent", "Gradle")
        }
        connection.getInputStream().buffered().use { input ->
          partialJar.outputStream().buffered().use { output -> input.copyTo(output) }
        }

        val actualSha256 = sha256(partialJar)
        if (actualSha256 != expectedSha256) {
          throw GradleException(
            "Checksum mismatch for $jarName: expected $expectedSha256, got $actualSha256"
          )
        }
        Files.move(
          partialJar.toPath(),
          serverJar.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
        )
      } finally {
        partialJar.delete()
      }
    }
  }

  tasks.register<RunServer>("runLeaf$taskSuffix") {
    group = IntaveTaskGroups.SERVER_RUNS
    description = "Runs Intave on Leaf $serverVersion build $build"
    dependsOn("shadowJar", downloadTask)
    pluginJars.from(tasks.named("shadowJar"))
    minecraftVersion(serverVersion)
    serverJar(leafServerJar.asFile)
    downloadPlugins {
      modrinth("viaversion", "5.11.0")
      modrinth("viabackwards", "5.11.0")
    }
    runDirectory(File("runs/leaf_${serverVersion}-j$javaVersion"))
    jvmArgs("-Dcom.mojang.eula.agree=true")
    args("-o", "false")
    javaLauncher.set(
      project.javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
      }
    )
  }
}

/*
 * Gradle Task Configuration
 */
java {
  toolchain.languageVersion = JavaLanguageVersion.of(25)
  disableAutoTargetJvm()
}

apply(from = "gradle/packaging.gradle.kts")

tasks {
  build { dependsOn(shadowJar) }

  jar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveFileName.set("$simpleName.jar")
    manifest {
      attributes("Implementation-Title" to simpleName)
      attributes("Implementation-Version" to project.version)
      attributes("Implementation-Vendor" to "Jpx3")
      attributes("paperweight-mappings-namespace" to "mojang")
      attributes("Main-Class" to "de.jpx3.intave.IntaveApplication")
    }
  }

  compileJava {
    options.encoding = Charsets.UTF_8.name()
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
  }

  shadowJar {
    archiveFileName.set("$simpleName-bundled.jar")
    archiveClassifier.set("bundled")
    from("src/bundled/resources")
    minimize {
      // Preserve public Intave APIs and libraries with reflection or JNI entry points.
      exclude(dependency("ac.intave:.*:.*"))
      exclude(dependency("net.bytebuddy:byte-buddy:.*"))
      exclude(dependency("com.github.luben:zstd-jni:.*"))
    }
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/*.EC")
    exclude("module-info.class")
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    mergeServiceFiles()
  }

  test {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
  }

  jacocoTestReport {
    dependsOn(test)
    reports {
      html.required.set(true)
      xml.required.set(true)
      csv.required.set(true)
    }
  }

  register("testCoverage") {
    group = "verification"
    description = "Runs the test suite and generates a JaCoCo coverage report."
    dependsOn(jacocoTestReport)
  }
}

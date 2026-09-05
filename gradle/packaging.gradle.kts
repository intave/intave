val sourceSets = extensions.getByType<SourceSetContainer>()
val mainSourceSet = sourceSets.getByName("main")
val testSourceSet = sourceSets.getByName("test")
val runtimeClasspath = configurations.named("runtimeClasspath")

// Intave APIs go into both builds; third-party libraries are downloaded by the minimal build.
val intaveApis = runtimeClasspath.get().incoming.artifactView {
  componentFilter { it is ModuleComponentIdentifier && it.group == "ac.intave" }
}.files
val downloadableLibraries = runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
  .filter { it.moduleVersion.id.group != "ac.intave" }
  .sortedBy { it.moduleVersion.id.toString() }

// Minimal JAR: plugin classes, Intave APIs and the generated download list.

val generateLibraryList = tasks.register("generateLibraryList") {
  val coordinates = downloadableLibraries.map { it.moduleVersion.id.toString() }
  val directory = layout.buildDirectory.dir("generated/library-resources")

  inputs.property("coordinates", coordinates)
  outputs.dir(directory)

  doLast {
    val manifest = directory.get().file("META-INF/intave/libraries.txt").asFile
    manifest.parentFile.mkdirs()
    manifest.writeText(coordinates.joinToString("\n", postfix = "\n"))
  }
}
mainSourceSet.resources.srcDir(generateLibraryList)

val minimalJar = tasks.named<Jar>("jar") {
  from(intaveApis.map { zipTree(it) }) {
    include("ac/intave/**")
  }
  exclude("de/jpx3/classloader/native/**")
}

// Artifact tests must load dependencies from the built JAR or cache, not Gradle's runtime classpath.
val testLibraries = configurations.named("testRuntimeClasspath").get() - runtimeClasspath.get()
val bundledJar = tasks.named<Jar>("shadowJar")
val minimalJarFile = minimalJar.flatMap { it.archiveFile }
val bundledJarFile = bundledJar.flatMap { it.archiveFile }
val testLibraryCache = layout.buildDirectory.dir("test-library-cache")

testSourceSet.resources.srcDir("src/bundled/resources")

val testShadedJar = tasks.register<Test>("testShadedJar") {
  group = "verification"
  description = "Tests the distribution JAR without unshrunk dependency JARs on the classpath."
  dependsOn(bundledJar, tasks.named("testClasses"))

  testClassesDirs = testSourceSet.output.classesDirs
  classpath = files(bundledJarFile) + testSourceSet.output + testLibraries
  useJUnitPlatform()

  filter {
    includeTestsMatching("de.jpx3.intave.library.BundledDependenciesTest")
    includeTestsMatching("de.jpx3.classloader.NativeLibraryTest")
    includeTestsMatching("de.jpx3.intave.share.CertificateTest")
    includeTestsMatching("de.jpx3.intave.cloud.*")
    includeTestsMatching("de.jpx3.intave.report.*")
  }
  systemProperty("intave.test.shadedJar", bundledJarFile.get().asFile.absolutePath)
}

val prepareLibraryCache = tasks.register<Sync>("prepareLibraryCache") {
  into(testLibraryCache)

  // Match Library's cache layout beneath APPDATA (Windows) or user.home (Unix).
  val cachePath = if (System.getProperty("os.name").lowercase().contains("win")) {
    "Intave/Libraries"
  } else {
    ".intave/libraries"
  }

  downloadableLibraries.forEach { artifact ->
    val id = artifact.moduleVersion.id
    from(artifact.file) {
      into("$cachePath/${id.group}/${id.name}/${id.version}")
      rename { "${id.name}.jar" }
    }
  }
}

val testMinimalJar = tasks.register<Test>("testMinimalJar") {
  group = "verification"
  description = "Tests the minimal JAR and its local dependency-cache loading path."
  dependsOn(minimalJar, tasks.named("testClasses"), prepareLibraryCache)

  // This JAR is opened by the tests rather than included on their classpath.
  inputs.file(minimalJarFile).withPropertyName("minimalJar")
  testClassesDirs = testSourceSet.output.classesDirs
  classpath = testSourceSet.output + testLibraries
  useJUnitPlatform()

  filter { includeTestsMatching("de.jpx3.intave.library.MinimalJarTest") }

  systemProperty("intave.test.minimalJar", minimalJarFile.get().asFile.absolutePath)
  val cache = testLibraryCache.get().asFile.absolutePath
  environment("APPDATA", cache)
  systemProperty("user.home", cache)
}

tasks.named("check") {
  dependsOn(testShadedJar, testMinimalJar)
}

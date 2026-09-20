import java.net.URI

plugins {
    application
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = rootProject.group
version = rootProject.version

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    mavenCentral()
    maven("https://repo.pgm.fyi/snapshots")
}

dependencies {
    implementation("tc.oc.pgm:core:${providers.gradleProperty("pgmVersion").get()}") {
        isTransitive = false
    }
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    implementation("org.javassist:javassist:3.28.0-GA")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// This is an offline application, so the mapped server and its libraries are runtime dependencies.
paperweight.addServerDependencyTo.add(configurations.implementation.get())
paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

sourceSets.main {
    java.srcDir("../src/main/java")
    resources.srcDir("../src/main/resources")
}
sourceSets.test { java.srcDir("../src/test/java") }

application {
    mainClass = "tc.oc.pgm.server.parser.MapValidator"
    applicationName = "pgm-validate-modern"
    applicationDefaultJvmArgs = listOf("-Dlog4j.configurationFile=pgm-parser-log4j2.xml")
}

// Paper's dependency list exceeds cmd.exe's command length limit at ordinary install paths.
// A manifest classpath preserves Gradle's classpath order while keeping both launchers short.
val runtimeJarNames = configurations.runtimeClasspath.map { configuration ->
    configuration.files.map { it.name }
}
val applicationJarName = tasks.jar.flatMap { it.archiveFileName }
val launcherClasspathJar by tasks.registering(Jar::class) {
    archiveFileName = "launcher-classpath.jar"
    inputs.property("applicationJarName", applicationJarName)
    inputs.property("runtimeJarNames", runtimeJarNames)
    manifest.attributes["Class-Path"] =
        (listOf(applicationJarName.get()) + runtimeJarNames.get()).joinToString(" ") { name ->
            URI(null, null, name, null).toASCIIString()
        }
}
tasks.startScripts {
    classpath = files(launcherClasspathJar)
}

distributions.main {
    contents {
        from("../LICENSE", "../LICENSE_LINKING", "../README.md")
        into("lib") { from(launcherClasspathJar) }
    }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    forkEvery = 1
    maxHeapSize = "2g"
}

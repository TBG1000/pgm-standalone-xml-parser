plugins {
    application
}

group = "tc.oc.pgm"
version = "1.0.0-SNAPSHOT"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    maven("https://repo.pgm.fyi/snapshots")
}

dependencies {
    // Published core is shaded: it includes util, platform implementations and relocated JDOM.
    implementation("tc.oc.pgm:core:${providers.gradleProperty("pgmVersion").get()}") {
        isTransitive = false
    }
    implementation("app.ashcon:sportpaper:1.8.8-R0.1-SNAPSHOT")
    implementation("org.javassist:javassist:3.28.0-GA")
    runtimeOnly("org.slf4j:slf4j-nop:1.7.32")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "tc.oc.pgm.server.parser.MapValidator"
    applicationName = "pgm-validate"
    applicationDefaultJvmArgs = listOf("-Dlog4j.configurationFile=pgm-parser-log4j2.xml")
}

distributions {
    main {
        contents {
            from("LICENSE", "LICENSE_LINKING", "README.md")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    forkEvery = 1
}

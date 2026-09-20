# Standalone XML parser

`pgm-validate` checks PGM map XML without starting a Minecraft server. It runs the same
`MapFactoryImpl` used by PGM, including preprocessing, module parsing, reference resolution,
and post-parse validation. The parser is built independently from published PGM and SportPaper artifacts.

This first version targets **SportPaper / Minecraft 1.8.8**. Maps whose version constraints
exclude 1.8.8 fail with an explicit diagnostic. It does not validate modern-only branches of
server-version conditionals. Java 21 is required.

## Build and run

From this repository root (Java 21 required):

```sh
./gradlew installDist
build/install/pgm-validate/bin/pgm-validate /path/to/map.xml
```

On Windows:

```powershell
.\gradlew.bat installDist
.\build\install\pgm-validate\bin\pgm-validate.bat C:\maps\example\map.xml
```

The entire `build/install/pgm-validate` directory can be copied to another computer
with Java 21; Gradle is only needed to build it. `distZip` also creates a distributable
archive under `build/distributions`.

```text
Usage: pgm-validate [--includes DIR] [--variant ID] [--] FILE_OR_DIR...
```

- Provide one or more XML files or directories. Each directory is searched recursively for
  `map.xml` files, including one directly inside it. Individual XML files may have any filename;
  other filenames are ignored during directory discovery.
- Maps discovered within each directory are validated in sorted path order. Overlapping input
  paths are normalized so the same path is validated only once. Discovery does not follow symbolic
  links. An empty search or directory traversal failure produces an error; other discovered maps
  and remaining inputs are still validated.
- `--includes DIR` loads `.xml` files directly inside that directory. `common.xml` supplies
  `<include id="common"/>`; `global.xml` is injected using PGM's normal global-include rules.
  Invalid includes fail validation instead of being silently skipped. Includes are reread for
  each map, so a reused parser validates edits rather than retaining stale content.
- `--variant ID` selects a variant, including its conditionals. The default is `default`.
  Run the command separately for each variant you want to check.
- `--` ends option parsing, for filenames starting with a dash. `--help` prints usage.

For example:

```sh
pgm-validate --includes ./includes --variant tournament ./maps/arena ./maps/bridge/map.xml
pgm-validate ./maps-repository
```

Successful maps produce `VALID:` on stdout. Failures and warnings go to stderr, with source
and line information where available. Validation continues through the remaining input files
after a failure. Like PGM, the parser reports the first fatal error in each map; fix it and rerun.

Exit codes are `0` when every selected map is valid, `1` for invalid or unreadable map XML
(including invalid includes and unsupported map versions), and `2` for invalid command
arguments, empty directory searches, directory traversal failures, or parser runtime failures.
Warnings for unused XML do not change the exit code.

## What is checked

The validator checks PGM metadata, teams, regions, filters, kits, items, enchantments, potions,
objectives, other registered map modules, and their references using the existing PGM parser.
Includes, constants, and variant conditionals use PGM's existing preprocessing semantics.
Unknown or unused XML is reported as a warning, as it is during normal map loading.

No worlds, plugins, match loop, database, or network listener are started. World files are
optional; an existing `level.dat` may be read to infer the map's minimum Minecraft version.
Contributor UUIDs are parsed locally without fetching usernames. Defaults used for FFA are
2 minimum players and 100 maximum players; experimental options use the parser's default values.
Validation does not check terrain contents, gameplay behavior, or integrations requiring live
server services. Unsupported runtime service calls fail explicitly.

Map and include documents reject DTDs and external entities. Include expansion is limited to
1024 per map parse, so recursive includes produce a diagnostic instead of hanging.

## Java API

Use the distribution's jars on the classpath:

```java
var parser = new StandaloneMapParser(Logger.getLogger("map-validation"), Path.of("includes"));
MapContext map = parser.parse(Path.of("map.xml"), "default");
for (MapModule<?> module : map.getModules()) {
  // Inspect parsed module definitions.
}
```

The parser class is `tc.oc.pgm.server.parser.StandaloneMapParser`. Pass `null` for the includes
directory when none is needed. Parsing throws `MapException`. Diagnostics for unused XML
are sent to the supplied logger.

The offline adapter owns process-wide Bukkit and PGM services. Use a dedicated JVM, separate
from a running Bukkit server. Reuse the parser for multiple maps; each call creates a fresh
map factory. Returned modules are intended for inspection, not for starting matches. Enumerate
them with `getModules()` (`MapContextImpl` does not support `getModule(Class)`).
Recursive discovery is a CLI feature; `StandaloneMapParser.parse` still parses one map at a time.

Run the integration tests with `./gradlew test`.

## Dependencies and upgrades

This is an independent Gradle build. It does not use a PGM checkout, `buildSrc`,
`mavenLocal()`, or sibling projects. Only Maven Central and the public
[PGM snapshot repository](https://repo.pgm.fyi/snapshots) are required.

The default PGM dependency is `tc.oc.pgm:core:0.16-20260509.080057-30`, a pinned
snapshot listed by the repository on extraction. Override it with
`-PpgmVersion=0.16-SNAPSHOT` to test the current snapshot; run the full tests before
updating the pin. Snapshots may eventually be removed by the repository operator.

PGM's `core` publication is its shaded plugin JAR. It already contains `util`,
the platform implementations, and PGM's bundled libraries. Its JDOM classes are
relocated to `tc.oc.pgm.lib.org.jdom2`, so the adapter imports that namespace.
Do not add a separate unshaded JDOM or `util` JAR: their types differ from the
published parser's API. The modern platform is bundled but is not selected by
the SportPaper runtime.

SportPaper `1.8.8-R0.1-SNAPSHOT` supplies Bukkit, Minecraft 1.8.8 internals, and
its runtime libraries. Javassist `3.28.0-GA` creates the offline service proxies;
SLF4J NOP `1.7.32` supplies the logging binding. These versions and JUnit `6.0.2`
come from the original Gradle configurations. SportPaper remains a changing
snapshot, so the complete dependency graph is not immutable.

The published PGM snapshot predates the standalone-specific fixes in the source
checkout. This adapter therefore validates XML securely before PGM reads it,
loads includes as in-memory PGM documents, limits include expansion to 1024,
and initializes Bukkit's potion helpers in a compatible order. The integration
tests cover these compatibility requirements; keep them when upgrading PGM.

GitHub Actions runs `check distZip` and uploads the ZIP as a workflow artifact.
No PGM build or GitHub Packages credentials are needed. The generated ZIP contains
the executable scripts and all runtime JARs; retain `LICENSE` and `LICENSE_LINKING`
when redistributing source. This project carries PGM's existing license and linking
exception.

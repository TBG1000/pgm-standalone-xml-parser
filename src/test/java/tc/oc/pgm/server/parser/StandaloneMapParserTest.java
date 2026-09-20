package tc.oc.pgm.server.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tc.oc.pgm.api.map.exception.MapException;
import tc.oc.pgm.spawns.SpawnModule;
import tc.oc.pgm.util.xml.InvalidXMLException;

class StandaloneMapParserTest {
  @TempDir
  Path directory;

  private static final String MAP = """
      <map proto="1.4.2">
        <name>Standalone test</name>
        <version>1.0.0</version>
        <objective>Test map parsing</objective>
        <authors><author uuid="12345678-1234-1234-1234-123456789abc"/></authors>
        <spawns><default><regions><point>0,64,0</point></regions></default></spawns>
        %s
      </map>
      """;

  private Path xml(String text) throws Exception {
    return Files.writeString(directory.resolve("map.xml"), text);
  }

  private StandaloneMapParser parser() throws Exception {
    return new StandaloneMapParser(Logger.getAnonymousLogger(), null);
  }

  @Test
  void parsesModulesAndItemsWithoutWorldFiles() throws Exception {
    var context = parser().parse(xml(MAP.formatted("""
        <kits><kit id="test"><item slot="0" enchantment="sharpness:2">diamond sword</item>
        <potion duration="10">speed</potion></kit></kits>
        """)), "default");
    assertEquals("Standalone test", context.getInfo().getName());
    assertTrue(context.getModules().stream().anyMatch(SpawnModule.class::isInstance));
    assertFalse(Files.exists(directory.resolve("level.dat")));
  }

  @Test
  void rejectsInvalidMaterialWithLineNumber() throws Exception {
    MapException error = assertThrows(MapException.class, () -> parser()
        .parse(
            xml(MAP.formatted(
                "<kits><kit id=\"test\"><item slot=\"0\">not_a_material</item></kit></kits>")),
            "default"));
    var invalidXml = assertInstanceOf(InvalidXMLException.class, error.getCause());
    assertTrue(invalidXml.getStartLine() > 0);
  }

  @Test
  void resolvesAndRejectsFeatureReferences() throws Exception {
    var parser = parser();
    parser.parse(xml(MAP.formatted("""
        <regions><union id="all"><region id="later"/></union><point id="later">0,0,0</point></regions>
        """)), "default");
    assertNotNull(
        assertThrows(MapException.class, () -> parser.parse(xml(MAP.formatted("""
        <regions><union id="all"><region id="missing"/></union></regions>
      """)), "default")));
  }

  @Test
  void rejectsMalformedXmlAndWrongRoot() throws Exception {
    var parser = parser();
    assertNotNull(assertThrows(MapException.class, () -> parser.parse(xml("<map>"), "default")));
    assertNotNull(assertThrows(
        MapException.class,
        () -> parser.parse(
            xml(MAP.formatted("").replace("<map ", "<other ").replace("</map>", "</other>")),
            "default")));
  }

  @Test
  void rejectsExternalEntities() throws Exception {
    Path secret = Files.writeString(directory.resolve("secret.txt"), "external content");
    assertNotNull(assertThrows(MapException.class, () -> parser()
        .parse(
            xml("<!DOCTYPE map [<!ENTITY external SYSTEM \"" + secret.toUri() + "\">]>"
                + MAP.formatted("").replace("Standalone test", "&external;")),
            "default")));
  }

  @Test
  void rejectsExternalEntitiesInIncludes() throws Exception {
    Path includes = Files.createDirectory(directory.resolve("includes"));
    Path secret = Files.writeString(directory.resolve("secret.txt"), "external content");
    Files.writeString(includes.resolve("global.xml"),
        "<!DOCTYPE include [<!ENTITY external SYSTEM \"" + secret.toUri()
            + "\">]><include><rules><rule>&external;</rule></rules></include>");
    var parser = new StandaloneMapParser(Logger.getAnonymousLogger(), includes);
    assertThrows(MapException.class, () -> parser.parse(xml(MAP.formatted("")), "default"));
  }

  @Test
  void expandsGlobalInclude() throws Exception {
    Path includes = Files.createDirectory(directory.resolve("includes"));
    Files.writeString(includes.resolve("global.xml"),
        "<include><rules><rule>Global rule</rule></rules></include>");
    var parser = new StandaloneMapParser(Logger.getAnonymousLogger(), includes);
    assertEquals(java.util.List.of("Global rule"),
        parser.parse(xml(MAP.formatted("")), "default").getInfo().getRules());
  }

  @Test
  void parsesIncludesConstantsAndVariants() throws Exception {
    Path includes = Files.createDirectory(directory.resolve("includes"));
    Files.writeString(
        includes.resolve("common.xml"),
        "<include><constant id=\"label\">included</constant></include>");
    var parser = new StandaloneMapParser(Logger.getAnonymousLogger(), includes);
    Path file = xml(MAP.formatted("""
        <variant id="alternate">Alternate</variant>
        <include id="common"/>
        <if variant="alternate"><rules><rule>${label}</rule></rules></if>
        """));
    assertEquals(
        java.util.List.of("included"), parser.parse(file, "alternate").getInfo().getRules());
    assertTrue(parser.parse(file, "default").getInfo().getRules().isEmpty());
    Files.writeString(
        includes.resolve("common.xml"),
        "<include><constant id=\"label\">edited</constant></include>");
    assertEquals(
        java.util.List.of("edited"), parser.parse(file, "alternate").getInfo().getRules());
    assertNotNull(assertThrows(MapException.class, () -> parser.parse(file, "missing")));
  }

  @Test
  void rejectsMissingIncludesAndUnsupportedVersions() throws Exception {
    var parser = parser();
    assertNotNull(assertThrows(
        MapException.class,
        () -> parser.parse(xml(MAP.formatted("<include id=\"missing\"/>")), "default")));
    MapException error = assertThrows(
        MapException.class,
        () -> parser.parse(
            xml(MAP.formatted("").replace("<map ", "<map min-server-version=\"1.21.0\" ")),
            "default"));
    assertTrue(error.getMessage().contains("targets 1.8.8"));
  }

  @Test
  void rejectsCyclicAndMalformedIncludes() throws Exception {
    Path includes = Files.createDirectory(directory.resolve("includes"));
    Files.writeString(includes.resolve("cycle.xml"), "<include><include id=\"cycle\"/></include>");
    var parser = new StandaloneMapParser(Logger.getAnonymousLogger(), includes);
    MapException error = assertThrows(
        MapException.class,
        () -> parser.parse(xml(MAP.formatted("<include id=\"cycle\"/>")), "default"));
    assertTrue(error.getMessage().contains("include cycle"));
    Files.writeString(includes.resolve("cycle.xml"), "<include>");
    assertNotNull(assertThrows(
        MapException.class,
        () -> parser.parse(xml(MAP.formatted("<include id=\"cycle\"/>")), "default")));
  }

  @Test
  void parsesTeamsFiltersAndCrafting() throws Exception {
    var context = parser().parse(xml(MAP.formatted("""
        <teams><team id="red" color="red" max="10">Red</team>
        <team id="blue" color="blue" max="10">Blue</team></teams>
        <filters><team id="only-red">red</team></filters>
        <crafting><shapeless><result>diamond sword</result><ingredient>diamond</ingredient></shapeless></crafting>
        """)), "default");
    assertEquals(java.util.List.of(10, 10), context.getInfo().getMaxPlayers());
    assertTrue(context.getModules().stream()
        .anyMatch(tc.oc.pgm.crafting.CraftingModule.class::isInstance));
  }

  @Test
  void reportsUnusedXmlAndLineDiagnostics() throws Exception {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    var out = new PrintStream(stdout);
    var err = new PrintStream(stderr);
    Path file = xml(MAP.formatted("<typo/>"));
    assertEquals(0, MapValidator.run(new String[] {file.toString()}, out, err));
    assertTrue(stderr.toString().contains("Unused node"));
    stderr.reset();
    xml(MAP.formatted(
        "<kits><kit id=\"bad\"><item slot=\"0\">missing_material</item></kit></kits>"));
    assertEquals(1, MapValidator.run(new String[] {file.toString()}, out, err));
    assertTrue(stderr.toString().contains("line 7"), stderr.toString());
  }

  @Test
  void recursivelyValidatesRepositoriesAndDeduplicatesOverlappingInputs() throws Exception {
    Path nested = Files.createDirectories(directory.resolve("category/map with spaces"));
    Path rootMap = xml(MAP.formatted(""));
    Path nestedMap = Files.writeString(nested.resolve("map.xml"), MAP.formatted(""));
    Files.writeString(nested.resolve("include.xml"), "<not-a-map/>");
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();

    assertEquals(
        0,
        MapValidator.run(
            new String[] {
              directory.toString(),
              nested.toString(),
              nested.resolve(".").resolve("map.xml").toString()
            },
            new PrintStream(stdout),
            new PrintStream(stderr)));
    var results =
        stdout.toString().lines().filter(line -> line.startsWith("VALID:")).toList();
    var expected = java.util.stream.Stream.of(rootMap, nestedMap)
        .sorted()
        .map(file -> "VALID: " + file + " [default] - Standalone test")
        .toList();
    assertEquals(expected, results);
    assertEquals("", stderr.toString());
  }

  @Test
  void continuesAfterInvalidDiscoveredMapAndAcceptsExplicitOtherFilenames() throws Exception {
    Path bad = Files.createDirectories(directory.resolve("a-bad")).resolve("map.xml");
    Files.writeString(bad, "<map>");
    Path good = Files.createDirectories(directory.resolve("z-good")).resolve("map.xml");
    Files.writeString(good, MAP.formatted(""));
    Path explicit = Files.writeString(directory.resolve("custom.xml"), MAP.formatted(""));
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();

    assertEquals(
        1,
        MapValidator.run(
            new String[] {directory.toString(), explicit.toString()},
            new PrintStream(stdout),
            new PrintStream(stderr)));
    assertTrue(stderr.toString().contains("INVALID: " + bad));
    assertTrue(stdout.toString().contains("VALID: " + good));
    assertTrue(stdout.toString().contains("VALID: " + explicit));
    assertEquals(
        2, stdout.toString().lines().filter(line -> line.startsWith("VALID:")).count());
  }

  @Test
  void emptySearchIsAnErrorAndDoesNotPreventValidatingOtherInputs() throws Exception {
    Path empty = Files.createDirectory(directory.resolve("empty"));
    Files.writeString(empty.resolve("include.xml"), "<include/>");
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    var out = new PrintStream(stdout);
    var err = new PrintStream(stderr);
    assertEquals(2, MapValidator.run(new String[] {empty.toString()}, out, err));
    assertEquals("", stdout.toString());
    assertTrue(stderr.toString().contains("No map.xml files found"));
    Path valid = xml(MAP.formatted(""));
    Path invalid = Files.writeString(directory.resolve("bad.xml"), "<map>");
    assertEquals(
        2,
        MapValidator.run(
            new String[] {empty.toString(), invalid.toString(), valid.toString()}, out, err));
    assertTrue(stdout.toString().contains("VALID: " + valid));
    assertTrue(stderr.toString().contains("INVALID: " + invalid));
  }

  @Test
  void cliExitCodesAndBatchProcessing() throws Exception {
    var stdout = new ByteArrayOutputStream();
    var stderr = new ByteArrayOutputStream();
    var out = new PrintStream(stdout);
    var err = new PrintStream(stderr);
    assertEquals(0, MapValidator.run(new String[] {"--help"}, out, err));
    assertEquals(2, MapValidator.run(new String[0], out, err));
    assertEquals(2, MapValidator.run(new String[] {"--variant"}, out, err));
    Path valid = xml(MAP.formatted(""));
    assertEquals(0, MapValidator.run(new String[] {directory.toString()}, out, err));
    assertEquals(
        1,
        MapValidator.run(
            new String[] {directory.resolve("missing.xml").toString(), valid.toString()},
            out,
            err));
    assertTrue(stdout.toString().contains("VALID: " + valid), stdout + "\n" + stderr);
    assertTrue(stderr.toString().contains("INVALID:"));
  }
}

package tc.oc.pgm.server.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tc.oc.pgm.api.map.exception.MapException;
import tc.oc.pgm.util.platform.Platform;

class ModernMapParserTest {
  @TempDir Path directory;

  private static final String MAP = """
      <map proto="1.4.2" min-server-version="1.21.11">
        <name>Modern parser</name>
        <version>1.0.0</version>
        <objective>Validate modern XML</objective>
        <spawns><default><regions><point>0,64,0</point></regions></default></spawns>
        %s
      </map>
      """;

  @Test
  void parsesModernItemsAndVersionConditionals() throws Exception {
    var parser = new StandaloneMapParser(Logger.getAnonymousLogger(), null);
    Path xml = Files.writeString(directory.resolve("map.xml"), MAP.formatted("""
        <kits><kit id="modern"><item slot="0" enchantment="sharpness:2">netherite sword</item>
        <item slot="1">mace</item><effect duration="10">slow falling</effect></kit></kits>
        <if min-server-version="1.21.11"><rules><rule>Modern branch</rule></rules></if>
        <if max-server-version="1.8.8"><kits><kit id="old"><item>invalid_material</item></kit></kits></if>
        """));
    assertEquals(java.util.List.of("Modern branch"), parser.parse(xml, "default").getInfo().getRules());
    assertTrue(Platform.isModern());
    assertFalse(Files.exists(directory.resolve("level.dat")));
  }

  @Test
  void rejectsLegacyOnlyMapsAndReportsTheActualTarget() throws Exception {
    var parser = new StandaloneMapParser(Logger.getAnonymousLogger(), null);
    Path xml = Files.writeString(directory.resolve("map.xml"),
        MAP.formatted("").replace("min-server-version=\"1.21.11\"", "max-server-version=\"1.8.8\""));
    var error = assertThrows(MapException.class, () -> parser.parse(xml, "default"));
    assertTrue(error.getMessage().contains("targets 1.21.11"));
  }

  @Test
  void validatesModernBlocksAndReportsBadModernItems() throws Exception {
    var parser = new StandaloneMapParser(Logger.getAnonymousLogger(), null);
    Path xml = Files.writeString(directory.resolve("map.xml"), MAP.formatted("""
        <kits><kit id="blocks"><item slot="0">deepslate</item></kit></kits>
        <filters><material id="modern-block">deepslate</material></filters>
        """));
    assertNotNull(parser.parse(xml, "default"));
    assertEquals(org.bukkit.Material.DEEPSLATE,
        tc.oc.pgm.util.material.MaterialUtils.MATERIAL_UTILS
            .parseBlockMaterialData("deepslate", null).getItemType());
    Files.writeString(xml, MAP.formatted("""
        <kits><kit id="bad"><item slot="0">not_a_modern_item</item></kit></kits>
        """));
    var out = new ByteArrayOutputStream();
    var err = new ByteArrayOutputStream();
    assertEquals(1, MapValidator.run(new String[] {xml.toString()}, new PrintStream(out), new PrintStream(err)));
    assertTrue(err.toString().contains("line"));
  }

  @Test
  void validatesModernTrimRegistry() throws Exception {
    var parser = new StandaloneMapParser(Logger.getAnonymousLogger(), null);
    Path xml = Files.writeString(directory.resolve("map.xml"),
        MAP.formatted("<trims helmet=\"sentry\"/>"));
    assertTrue(parser.parse(xml, "default").getModules().stream()
        .anyMatch(module -> module.getClass().getSimpleName().equals("TrimModule")));
    Files.writeString(xml, MAP.formatted("<trims helmet=\"missing_trim\"/>"));
    assertThrows(MapException.class, () -> parser.parse(xml, "default"));
  }

  @Test
  void helpIdentifiesModernTarget() {
    var out = new ByteArrayOutputStream();
    assertEquals(0, MapValidator.run(new String[] {"--help"}, new PrintStream(out), System.err));
    assertTrue(out.toString().contains("1.21.11"));
  }
}

package tc.oc.pgm.server.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tc.oc.pgm.api.map.exception.MapException;
import tc.oc.pgm.util.bukkit.BukkitUtils;
import tc.oc.pgm.util.bukkit.PotionEffects;

/** This class runs in a fresh JVM, so no earlier item parsing can initialize BukkitUtils. */
class PotionEffectStartupTest {
  @TempDir
  Path directory;

  @Test
  void parsesEffectsBeforeAnyEnchantedItems() throws Exception {
    var parser = new StandaloneMapParser(Logger.getAnonymousLogger(), null);
    Path xml = directory.resolve("map.xml");
    String document = """
        <map proto="1.4.2">
          <name>Effect startup</name>
          <version>1.0.0</version>
          <objective>Validate effects before items</objective>
          <kits><kit id="protection">
            <effect amplifier="10" duration="oo">damage resistance</effect>
          </kit></kits>
          <spawns><default><regions><point>0,64,0</point></regions></default></spawns>
        </map>
        """;
    Files.writeString(xml, document);
    assertEquals("Effect startup", parser.parse(xml, "default").getInfo().getName());
    assertNotNull(PotionEffects.NAUSEA);
    assertEquals("Nausea", BukkitUtils.potionEffectTypeName(PotionEffects.NAUSEA));
    assertEquals(PotionEffects.RESISTANCE, PotionEffects.getByName("resistance"));
    assertEquals(PotionEffects.RESISTANCE, PotionEffects.getByName("damage resistance"));

    Files.writeString(xml, document.replace("damage resistance", "unknown effect"));
    assertThrows(MapException.class, () -> parser.parse(xml, "default"));
  }
}

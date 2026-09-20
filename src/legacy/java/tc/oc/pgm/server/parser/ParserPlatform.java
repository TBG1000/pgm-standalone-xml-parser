package tc.oc.pgm.server.parser;

import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.Enchantment;
import net.minecraft.server.v1_8_R3.MobEffectList;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.v1_8_R3.potion.CraftPotionBrewer;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffectType;

final class ParserPlatform {
  static final String VERSION = "1.8.8";

  static Server initialize(Logger logger, PluginManager plugins) {
    Server server = ParserRuntime.proxy(Server.class, (self, method, args) -> switch (method.getName()) {
      case "getName" -> "PGM XML parser";
      case "getVersion" -> "SportPaper (standalone XML parser)";
      case "getBukkitVersion" -> "1.8.8-R0.1-SNAPSHOT";
      case "getLogger" -> logger;
      case "getItemFactory" -> CraftItemFactory.instance();
      case "getPluginManager" -> plugins;
      case "getMaxPlayers" -> 100;
      default -> throw ParserRuntime.unsupported(method.getName());
    });
    Bukkit.setServer(server);

    // Register Minecraft's item, enchantment and potion data without constructing a server,
    // loading worlds, opening sockets, scheduling ticks, or enabling plugins.
    DispenserRegistry.c();
    Enchantment.DAMAGE_ALL.getClass();
    org.bukkit.enchantments.Enchantment.stopAcceptingRegistrations();
    Potion.setPotionBrewer(new CraftPotionBrewer());
    MobEffectList.BLINDNESS.getClass();
    PotionEffectType.stopAcceptingRegistrations();

    return server;
  }
}

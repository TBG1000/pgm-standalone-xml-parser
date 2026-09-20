package tc.oc.pgm.server.parser;

import java.lang.reflect.InvocationHandler;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.util.proxy.ProxyFactory;
import net.kyori.adventure.text.Component;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.Enchantment;
import net.minecraft.server.v1_8_R3.MobEffectList;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.v1_8_R3.potion.CraftPotionBrewer;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffectType;
import tc.oc.pgm.api.Config;
import tc.oc.pgm.api.Datastore;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.player.Username;
import tc.oc.pgm.util.named.NameStyle;

/** Supplies only the Bukkit and PGM services used during XML parsing. */
final class ParserRuntime {
  private static boolean initialized;

  private ParserRuntime() {}

  static synchronized void initialize() {
    if (initialized) return;
    if (Bukkit.getServer() != null || PGM.GLOBAL.get() != null) {
      throw new IllegalStateException("The standalone parser must run outside a Bukkit server");
    }

    Logger logger = Logger.getLogger("PGM XML parser");
    logger.setLevel(Level.WARNING);
    PluginManager plugins =
        proxy(PluginManager.class, (self, method, args) -> switch (method.getName()) {
          case "getPermissionSubscriptions", "getDefaultPermSubscriptions" -> Set.of();
          case "getPermission", "getPlugin", "recalculatePermissionDefaults" -> null;
          case "isPluginEnabled" -> false;
          default -> throw unsupported(method.getName());
        });
    Server server = proxy(Server.class, (self, method, args) -> switch (method.getName()) {
      case "getName" -> "PGM XML parser";
      case "getVersion" -> "SportPaper (standalone XML parser)";
      case "getBukkitVersion" -> "1.8.8-R0.1-SNAPSHOT";
      case "getLogger" -> logger;
      case "getItemFactory" -> CraftItemFactory.instance();
      case "getPluginManager" -> plugins;
      case "getMaxPlayers" -> 100;
      default -> throw unsupported(method.getName());
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

    // Published PGM initializes PotionEffects through BukkitUtils. Initialize in that
    // direction to avoid exposing partially initialized potion constants.
    tc.oc.pgm.util.bukkit.BukkitUtils.potionEffectTypeName(PotionEffectType.SPEED);

    Config config = proxy(Config.class, (self, method, args) -> switch (method.getName()) {
      case "showUnusedXml" -> true;
      case "getMinimumPlayers" -> 2L;
      case "getExperimentAsBool" -> args[1];
      default -> throw unsupported(method.getName());
    });
    Datastore datastore = proxy(Datastore.class, (self, method, args) -> {
      if (!method.getName().equals("getUsername")) throw unsupported(method.getName());
      UUID id = (UUID) args[0];
      return new Username() {
        @Override
        public Component getName(NameStyle style) {
          return Component.text(id.toString());
        }

        @Override
        public UUID getId() {
          return id;
        }

        @Override
        public String getNameLegacy() {
          return id.toString();
        }
      };
    });
    PGM.set(proxy(PGM.class, (self, method, args) -> switch (method.getName()) {
      case "getConfiguration" -> config;
      case "getDatastore" -> datastore;
      case "getLogger", "getGameLogger" -> logger;
      case "getServer" -> server;
      case "getName" -> "PGM XML parser";
      case "isEnabled" -> true;
      default -> throw unsupported(method.getName());
    }));
    initialized = true;
  }

  private static UnsupportedOperationException unsupported(String method) {
    return new UnsupportedOperationException(
        "Not available during standalone XML parsing: " + method);
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    // SportPaper has binary compatibility methods with the same parameters and different
    // return types (getOnlinePlayers). java.lang.reflect.Proxy cannot implement that API.
    ProxyFactory factory = new ProxyFactory();
    factory.setInterfaces(new Class<?>[] {type});
    factory.setFilter(method -> !method.getName().equals("finalize"));
    try {
      return type.cast(
          factory.create(new Class<?>[0], new Object[0], (self, method, proceed, args) -> {
            if (method.getDeclaringClass() == Object.class) {
              return switch (method.getName()) {
                case "toString" -> "Standalone " + type.getSimpleName();
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                default -> throw unsupported(method.getName());
              };
            }
            return handler.invoke(self, method, args);
          }));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to initialize standalone " + type.getSimpleName(), e);
    }
  }
}

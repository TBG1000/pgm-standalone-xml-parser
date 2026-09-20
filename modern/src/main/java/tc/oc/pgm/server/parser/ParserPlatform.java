package tc.oc.pgm.server.parser;

import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.plugin.PluginManager;

/** Loads Paper's vanilla registries without constructing a server or world. */
final class ParserPlatform {
  static final String VERSION = "1.21.11";

  static Server initialize(Logger logger, PluginManager plugins) {
    SharedConstants.tryDetectVersion();
    // Bootstrap temporarily redirects stdout/stderr; CLI diagnostics retain their own streams.
    var stdout = System.out;
    var stderr = System.err;
    try {
      Bootstrap.bootStrap();
    } finally {
      System.setOut(stdout);
      System.setErr(stderr);
    }

    // Enchantments and other data-driven registries are in the bundled vanilla data pack.
    // Loading these mirrors Paper's registry loading, without its server lifecycle.
    {
      var packs = ServerPacksSource.createVanillaTrustedRepository();
      packs.reload();
      packs.setSelected(List.of("vanilla"), false);
      try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, packs.openAllSelected())) {
        var layers = RegistryLayer.createRegistryAccess();
        var tags = TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
        var lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLDGEN), tags);
        var worldgen = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES);
        layers = layers.replaceFrom(RegistryLayer.WORLDGEN, worldgen);
        var dimensions = RegistryDataLoader.load(resources,
            Stream.concat(lookups.stream(), worldgen.listRegistries()).toList(),
            RegistryDataLoader.DIMENSION_REGISTRIES);
        layers = layers.replaceFrom(RegistryLayer.DIMENSIONS, dimensions);
        tags.forEach(net.minecraft.core.Registry.PendingTags::apply);
        CraftRegistry.setMinecraftRegistry(layers.compositeAccess());
      }
    }

    Server server = ParserRuntime.proxy(Server.class, (self, method, args) -> switch (method.getName()) {
      case "getName" -> "Paper";
      case "getVersion" -> "Paper (standalone XML parser)";
      case "getBukkitVersion" -> VERSION + "-R0.1-SNAPSHOT";
      case "getMinecraftVersion" -> VERSION;
      case "getLogger" -> logger;
      case "getItemFactory" -> CraftItemFactory.instance();
      case "getUnsafe" -> CraftMagicNumbers.INSTANCE;
      case "getPluginManager" -> plugins;
      case "getMaxPlayers" -> 100;
      case "getRegistry" -> io.papermc.paper.registry.PaperRegistryAccess.instance().getRegistry((Class) args[0]);
      case "createBlockData" -> blockData(args);
      default -> throw ParserRuntime.unsupported(method.getName());
    });
    Bukkit.setServer(server);
    return server;
  }

  @SuppressWarnings("unchecked")
  private static BlockData blockData(Object[] args) {
    Material material = args[0] instanceof Material value ? value : null;
    String data = args[0] instanceof String value ? value
        : args.length > 1 && args[1] instanceof String value ? value : null;
    BlockData block = CraftBlockData.newData(material == null ? null : material.asBlockType(), data);
    if (args.length > 1 && args[1] instanceof Consumer<?> consumer) {
      ((Consumer<BlockData>) consumer).accept(block);
    }
    return block;
  }
}

package tc.oc.pgm.server.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import tc.oc.pgm.lib.org.jdom2.JDOMException;
import tc.oc.pgm.lib.org.jdom2.input.JDOMParseException;
import tc.oc.pgm.api.map.MapContext;
import tc.oc.pgm.api.map.MapSource;
import tc.oc.pgm.api.map.exception.MapException;
import tc.oc.pgm.api.map.includes.MapInclude;
import tc.oc.pgm.map.MapFactoryImpl;
import tc.oc.pgm.map.includes.MapIncludeProcessorImpl;
import tc.oc.pgm.util.xml.InvalidXMLException;

/**
 * Parses PGM XML into map modules without starting a Minecraft server. This runtime targets
 * the distribution's Minecraft version and owns process-wide Bukkit and PGM services. Use it in a separate JVM from
 * a running server. The returned context can be inspected, but cannot create matches.
 */
public final class StandaloneMapParser {
  private static final Logger logger = Logger.getLogger(StandaloneMapParser.class.getName());
  private final Path includesDirectory;

  public StandaloneMapParser(Logger logger, Path includesDirectory) {
    ParserRuntime.initialize();
    StandaloneMapParser.logger.setParent(logger);
    this.includesDirectory = includesDirectory;
  }

  private MapIncludeProcessorImpl loadIncludes() throws IOException, JDOMException, MapException {
    Map<String, MapInclude> loaded = new LinkedHashMap<>();
    if (includesDirectory != null) {
      try (var files = Files.list(includesDirectory)) {
        for (Path file : files
            .filter(p -> p.getFileName().toString().endsWith(".xml"))
            .sorted()
            .toList()) {
          String name = file.getFileName().toString();
          String id = name.substring(0, name.length() - 4);
          try {
            var document = (tc.oc.pgm.util.xml.DocumentWrapper) ParserXml.read(file);
            document.setVisitingAllowed(false);
            document.setBaseURI("#" + id);
            long modified = Files.getLastModifiedTime(file).toMillis();
            loaded.put(id, new MapInclude() {
              @Override
              public java.util.List<tc.oc.pgm.lib.org.jdom2.Content> getContent() {
                return document.getRootElement().cloneContent();
              }

              @Override
              public long getLastModified() {
                return modified;
              }
            });
          } catch (JDOMParseException e) {
            if (e.getPartialDocument() != null) {
              e.getPartialDocument().setBaseURI(file.toAbsolutePath().toString());
            }
            throw e;
          }
        }
      }
    }
    return new MapIncludeProcessorImpl(StandaloneMapParser.logger) {
      private int expansions;

      @Override
      public MapInclude getMapInclude(tc.oc.pgm.lib.org.jdom2.Element element)
          throws InvalidXMLException {
        if (++expansions > 1024) {
          throw new InvalidXMLException(
              "Too many include expansions (possible include cycle)", element);
        }
        return super.getMapInclude(element);
      }

      @Override
      public MapInclude getMapIncludeById(String id) {
        return loaded.get(id);
      }
    };
  }

  /** Parse one variant, resolving includes, constants, modules and feature references. */
  public MapContext parse(Path xml, String variant) throws MapException {
    MapSource source =
        new XmlMapSource(Files.isDirectory(xml) ? xml.resolve("map.xml") : xml, variant);
    try (var factory = new MapFactoryImpl(logger, source, null, loadIncludes())) {
      MapContext context = factory.load();
      if (!context.getInfo().isServerSupported()) {
        throw new MapException(
            source,
            context.getInfo(),
            "Map requires a different Minecraft version; this validator targets " + ParserPlatform.VERSION,
            null);
      }
      return context;
    } catch (JDOMParseException e) {
      throw new MapException(source, null, "Invalid include XML", InvalidXMLException.fromJDOM(e));
    } catch (IOException | JDOMException e) {
      throw new MapException(source, null, "Unable to read map includes", e);
    }
  }
}

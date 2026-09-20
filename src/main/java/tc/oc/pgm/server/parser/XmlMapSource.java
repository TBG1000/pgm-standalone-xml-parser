package tc.oc.pgm.server.parser;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import tc.oc.pgm.api.map.MapSource;
import tc.oc.pgm.api.map.exception.MapMissingException;
import tc.oc.pgm.api.map.includes.MapInclude;
import tc.oc.pgm.map.source.MapRoot;

/** A single XML file; no world download or map repository is needed. */
final class XmlMapSource implements MapSource {
  private final Path file;
  private final String variant;

  XmlMapSource(Path file, String variant) {
    this.file = file.toAbsolutePath().normalize();
    this.variant = variant;
  }

  @Override
  public String getId() {
    return file + "[" + variant + "]";
  }

  @Override
  public String getVariantId() {
    return variant;
  }

  @Override
  public MapSource asVariant(String variant) {
    return new XmlMapSource(file, variant);
  }

  @Override
  public InputStream getDocument() throws MapMissingException {
    try {
      byte[] bytes = Files.readAllBytes(file);
      var document = ParserXml.read(bytes);
      if (!document.getRootElement().getName().equals("map")) {
        throw new IOException("Expected <map> root element");
      }
      return new ByteArrayInputStream(bytes);
    } catch (tc.oc.pgm.lib.org.jdom2.input.JDOMParseException e) {
      throw new MapMissingException(file.toString(), "Invalid map XML",
          tc.oc.pgm.util.xml.InvalidXMLException.fromJDOM(e));
    } catch (tc.oc.pgm.lib.org.jdom2.JDOMException e) {
      throw new MapMissingException(file.toString(), "Invalid map XML", new IOException(e));
    } catch (IOException e) {
      throw new MapMissingException(getId(), "Unable to read map document", e);
    }
  }

  @Override
  public void downloadTo(String folder, File dir) {
    throw new UnsupportedOperationException("Standalone parsing does not load worlds");
  }

  @Override
  public boolean checkForUpdates() {
    return false;
  }

  @Override
  public void setIncludes(Collection<MapInclude> includes) {}

  @Override
  public MapRoot getRoot() {
    return new MapRoot(file.getParent(), null, "XML", null, true);
  }

  @Override
  public Path getRelativeDir() {
    return Path.of("");
  }

  @Override
  public Path getAbsoluteXml() {
    return file;
  }
}

package tc.oc.pgm.server.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tc.oc.pgm.lib.org.jdom2.Document;
import tc.oc.pgm.lib.org.jdom2.JDOMException;
import tc.oc.pgm.lib.org.jdom2.input.SAXBuilder;
import tc.oc.pgm.util.xml.SAXHandler;

/** Local input guards for published PGM versions predating the standalone parser. */
final class ParserXml {
  private ParserXml() {}

  static Document read(byte[] bytes) throws IOException, JDOMException {
    SAXBuilder builder = new SAXBuilder();
    builder.setSAXHandlerFactory(SAXHandler.FACTORY);
    builder.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
    builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    return builder.build(new ByteArrayInputStream(bytes));
  }

  static Document read(Path file) throws IOException, JDOMException {
    return read(Files.readAllBytes(file));
  }
}

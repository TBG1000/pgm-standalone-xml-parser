package tc.oc.pgm.server.parser;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import tc.oc.pgm.api.map.MapSource;
import tc.oc.pgm.api.map.exception.MapException;
import tc.oc.pgm.util.xml.InvalidXMLException;

/** Command-line entry point for offline map validation. */
public final class MapValidator {
  private MapValidator() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  /** Returns 0 for valid XML, 1 for validation failures, or 2 for usage/runtime errors. */
  public static int run(String[] args, PrintStream out, PrintStream err) {
    Path includes = null;
    String variant = MapSource.DEFAULT_VARIANT;
    List<Path> files = new ArrayList<>();
    try {
      boolean options = true;
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if (options && arg.equals("--")) {
          options = false;
        } else if (options && (arg.equals("--help") || arg.equals("-h"))) {
          usage(out);
          return 0;
        } else if (options && (arg.equals("--includes") || arg.equals("--variant"))) {
          if (++i == args.length || args[i].isBlank()) {
            throw new IllegalArgumentException("Missing value for " + arg);
          }
          if (arg.equals("--includes")) includes = Path.of(args[i]);
          else variant = args[i];
        } else if (options && arg.startsWith("-")) {
          throw new IllegalArgumentException("Unknown option: " + arg);
        } else {
          files.add(Path.of(arg));
        }
      }
      if (files.isEmpty())
        throw new IllegalArgumentException("At least one XML file or map folder is required");
    } catch (IllegalArgumentException e) {
      err.println(e.getMessage());
      usage(err);
      return 2;
    }

    Discovery discovery = discover(files, err);
    if (discovery.files().isEmpty()) return discovery.status();

    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(new Handler() {
      @Override
      public void publish(LogRecord record) {
        err.println("WARNING: " + diagnostic(record.getThrown(), record.getMessage()));
      }

      @Override
      public void flush() {}

      @Override
      public void close() {}
    });

    StandaloneMapParser parser;
    try {
      parser = new StandaloneMapParser(logger, includes);
    } catch (Exception | LinkageError e) {
      err.println("Unable to initialize parser: " + diagnostic(e, e.toString()));
      return 2;
    }

    int status = discovery.status();
    for (Path file : discovery.files()) {
      try {
        var context = parser.parse(file, variant);
        out.println(
            "VALID: " + file + " [" + variant + "] - " + context.getInfo().getName());
      } catch (MapException e) {
        if (e.getCause() != null
            && !(e.getCause() instanceof InvalidXMLException)
            && !(e.getCause() instanceof IOException)) {
          err.println("ERROR: " + file + " - " + diagnostic(e, e.getMessage()));
          status = 2;
          continue;
        }
        err.println("INVALID: " + file + " - " + diagnostic(e, e.getMessage()));
        status = Math.max(status, 1);
      } catch (Exception | LinkageError e) {
        err.println("ERROR: " + file + " - " + diagnostic(e, e.toString()));
        status = 2;
      }
    }
    return status;
  }

  private static Discovery discover(List<Path> inputs, PrintStream err) {
    // Keep input order and the first display path, but validate overlapping inputs only once.
    var files = new LinkedHashMap<Path, Path>();
    int status = 0;
    for (Path input : inputs) {
      if (!Files.isDirectory(input)) {
        files.putIfAbsent(input.toAbsolutePath().normalize(), input);
        continue;
      }

      var visitor = new MapFileVisitor(err);
      try {
        // Do not follow symbolic links: repositories may contain cycles or links outside them.
        Files.walkFileTree(input, visitor);
      } catch (IOException | SecurityException e) {
        visitor.failed(input, e);
      }
      if (visitor.failed) {
        status = 2;
      } else if (visitor.files.isEmpty()) {
        err.println("ERROR: " + input + " - No map.xml files found");
        status = 2;
      }
      visitor.files.stream()
          .sorted()
          .forEach(file -> files.putIfAbsent(file.toAbsolutePath().normalize(), file));
    }
    return new Discovery(List.copyOf(files.values()), status);
  }

  private record Discovery(List<Path> files, int status) {}

  private static final class MapFileVisitor extends SimpleFileVisitor<Path> {
    private final PrintStream err;
    private final List<Path> files = new ArrayList<>();
    private boolean failed;

    private MapFileVisitor(PrintStream err) {
      this.err = err;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
      if (attributes.isRegularFile() && file.getFileName().equals(MapSource.FILE)) {
        files.add(file);
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException error) {
      failed(file, error);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path directory, IOException error) {
      if (error != null) failed(directory, error);
      return FileVisitResult.CONTINUE;
    }

    private void failed(Path path, Exception error) {
      err.println("ERROR: " + path + " - Unable to scan directory: " + error.getMessage());
      failed = true;
    }
  }

  private static String diagnostic(Throwable error, String fallback) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof InvalidXMLException xml) {
        String location = xml.getFullLocation();
        return (location == null ? "" : location + ": ") + xml.getMessage();
      }
      if (cause.getCause() == null && cause != error) return fallback + ": " + cause;
    }
    return fallback;
  }

  private static void usage(PrintStream out) {
    out.println("Usage: pgm-validate [--includes DIR] [--variant ID] [--] FILE_OR_DIR...");
    out.println("Validate PGM map XML for Minecraft 1.8.8 without running a server.");
    out.println(
        "Folders are searched recursively for map.xml files; individual XML files are also accepted.");
  }
}

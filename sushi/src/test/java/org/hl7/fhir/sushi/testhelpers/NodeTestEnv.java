package org.hl7.fhir.sushi.testhelpers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.utils.NodePath;
import org.hl7.fhir.sushi.utils.Processing;

/**
 * The node built-ins SUSHI's Jest tests use, so ported tests read like the TS ones: {@code path} (node path.posix),
 * {@code fs} (fs-extra, sync calls), {@code temp} (the temp package: tracked temp dirs, cleanupSync) and
 * {@link #dirname(String)} for {@code __dirname} (the TS test folder in the SUSHI checkout, -Dsushi.ts.dir).
 * Usage: {@code import static org.hl7.fhir.sushi.testhelpers.NodeTestEnv.*;} and in the test class
 * {@code static final String __dirname = dirname("ig");}.
 */
public final class NodeTestEnv {
  private NodeTestEnv() {}

  /** __dirname of a TS test folder, e.g. dirname("ig") = {sushi}/test/ig. */
  public static String dirname(String testSubdir) {
    return NodePath.join(TestFHIRDefinitions.tsDir(), "test", testSubdir);
  }

  public static final PathApi path = new PathApi();
  public static final FsApi fs = new FsApi();
  public static final TempApi temp = new TempApi();

  public static final class PathApi {
    public final String sep = "/";

    public String join(String... p) { return NodePath.join(p); }
    public String resolve(String... p) { return NodePath.resolve(p); }
    public String relative(String from, String to) { return NodePath.relative(from, to); }
    public String basename(String p) { return NodePath.basename(p); }
    public String dirname(String p) { return NodePath.dirname(p); }
    public String extname(String p) { return NodePath.extname(p); }
  }

  public static final class FsApi {
    public boolean existsSync(String p) { return Processing.exists(p); }

    public String readFileSync(String p) { return Processing.readFile(p); }

    public String readFileSync(String p, String encoding) { return Processing.readFile(p); }

    public Object readJSONSync(String p) { return Json.parseFile(Processing.readFile(p)); }

    public Object readJsonSync(String p) { return readJSONSync(p); }

    public List<String> readdirSync(String p) { return Processing.readdir(p); }

    public void mkdirSync(String p) {
      try {
        Files.createDirectory(Path.of(p));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    public void mkdirpSync(String p) { Processing.ensureDir(p); }

    public void ensureDirSync(String p) { Processing.ensureDir(p); }

    public void ensureFileSync(String p) {
      if (!existsSync(p)) Processing.outputFile(p, "");
    }

    public void writeFileSync(String p, String content) { Processing.outputFile(p, content); }

    public void outputFileSync(String p, String content) { Processing.outputFile(p, content); }

    public void renameSync(String from, String to) {
      try {
        Files.move(Path.of(from), Path.of(to), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    public void copyFileSync(String from, String to) {
      try {
        Files.copy(Path.of(from), Path.of(to), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    /** fs-extra copySync: files or whole folders. */
    public void copySync(String from, String to) {
      try {
        Path src = Path.of(from);
        Path dst = Path.of(to);
        if (!Files.isDirectory(src)) {
          Files.createDirectories(dst.toAbsolutePath().getParent());
          Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
          return;
        }
        try (var s = Files.walk(src)) {
          for (Path p : s.toList()) {
            Path t = dst.resolve(src.relativize(p).toString());
            if (Files.isDirectory(p)) Files.createDirectories(t);
            else Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
          }
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    public void removeSync(String p) { remove(p); }

    /** fs-extra remove (sync here). */
    public void remove(String p) {
      deleteTree(Path.of(p));
    }

    public void symlinkSync(String target, String link) {
      try {
        Files.createSymbolicLink(Path.of(link), Path.of(target));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    public void chmodSync(String p, int mode) {
      try {
        Files.setPosixFilePermissions(Path.of(p), java.nio.file.attribute.PosixFilePermissions.fromString(
            perms(mode)));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    private static String perms(int mode) {
      String s = "rwxrwxrwx";
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < 9; i++) b.append((mode & (1 << (8 - i))) != 0 ? s.charAt(i) : '-');
      return b.toString();
    }
  }

  /** The temp package: mkdirSync(prefix) under the OS temp dir, tracked; cleanupSync deletes them. */
  public static final class TempApi {
    private final List<Path> dirs = new ArrayList<>();

    public void track() {}

    public synchronized String mkdirSync(String prefix) {
      try {
        Path d = Files.createTempDirectory(prefix);
        dirs.add(d);
        return d.toString();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    public synchronized String mkdirSync() {
      return mkdirSync("temp");
    }

    public synchronized void cleanupSync() {
      for (Path d : dirs) deleteTree(d);
      dirs.clear();
    }
  }

  static void deleteTree(Path p) {
    if (!Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
    try {
      if (Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
        try (var s = Files.list(p)) {
          for (Path c : s.toList()) deleteTree(c);
        }
      }
      Files.delete(p);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Text of a file as UTF-8 (convenience for assertions). */
  public static String read(String p) {
    try {
      return Files.readString(Path.of(p), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

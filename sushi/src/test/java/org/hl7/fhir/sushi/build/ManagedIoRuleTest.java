package org.hl7.fhir.sushi.build;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.hl7.fhir.sushi.utils.Fs;
import org.junit.jupiter.api.Test;

/**
 * PORTING.md "I/O policy": all file access goes through org.hl7.fhir.core's ManagedFileAccess and all network access
 * through its ManagedWebAccess, so the host's policy, fhir-settings.json, proxies and offline mode apply to SUSHI.
 * This test reads the constant pool of every compiled main class (the class files themselves, so fully qualified
 * names, lambdas and method references are all seen) and fails on a direct use of java.io / java.nio.file / java.net
 * file or network APIs outside the one adapter class {@code utils.Fs}.
 */
class ManagedIoRuleTest {
  /** The adapter: the only main class that may use the APIs below (on files obtained through ManagedFileAccess). */
  static final String ADAPTER = "org/hl7/fhir/sushi/utils/Fs";

  /** Types whose every use (reference to the class or to any member) is file or network I/O. */
  static final Set<String> FORBIDDEN_TYPES = Set.of(
      "java/io/FileReader", "java/io/FileWriter", "java/io/RandomAccessFile",
      "java/nio/file/Files", "java/nio/file/FileSystems", "java/nio/file/FileSystem",
      "java/nio/file/spi/FileSystemProvider", "java/nio/file/DirectoryStream",
      "java/nio/channels/FileChannel", "java/nio/channels/AsynchronousFileChannel",
      "java/nio/channels/SocketChannel", "java/nio/channels/ServerSocketChannel",
      "java/util/zip/ZipFile", "java/util/jar/JarFile",
      "java/net/URLConnection", "java/net/HttpURLConnection", "javax/net/ssl/HttpsURLConnection",
      "java/net/JarURLConnection", "java/net/Socket", "java/net/ServerSocket", "java/net/DatagramSocket",
      "java/net/http/HttpClient", "java/net/http/HttpRequest", "java/net/http/HttpResponse",
      "java/net/http/WebSocket");

  /** java.io.File members that are pure path handling; every other File member (incl. constructors) is I/O. */
  static final Set<String> FILE_PURE = Set.of("separator", "separatorChar", "pathSeparator", "pathSeparatorChar",
      "getName", "getPath", "getParent", "getAbsolutePath", "isAbsolute", "toPath", "toString", "equals", "hashCode",
      "compareTo");

  /**
   * Explicit allow-list (owner.name+descriptor): uses of otherwise forbidden members that are not file access.
   */
  static final Map<String, String> ALLOWED = Map.of(
      // App.out / App.err: UTF-8 PrintStreams on the process's stdout/stderr file descriptors (as node writes them)
      "java/io/FileOutputStream.<init>(Ljava/io/FileDescriptor;)V", "stdout/stderr, not a file");

  /** Returns the forbidden references in one class file ("owner.name+descriptor" or "class owner"). */
  static List<String> violations(byte[] classFile) throws IOException {
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(classFile));
    if (in.readInt() != 0xCAFEBABE) throw new IOException("not a class file");
    in.readUnsignedShort();
    in.readUnsignedShort();
    int n = in.readUnsignedShort();
    String[] utf8 = new String[n];
    int[] classIdx = new int[n];
    int[][] refs = new int[n][]; // Fieldref/Methodref/InterfaceMethodref: {class, nameAndType}
    int[][] nat = new int[n][];
    for (int i = 1; i < n; i++) {
      int tag = in.readUnsignedByte();
      switch (tag) {
        case 1 -> utf8[i] = in.readUTF();
        case 3, 4 -> in.readInt();
        case 5, 6 -> {
          in.readLong();
          i++;
        }
        case 7 -> classIdx[i] = in.readUnsignedShort();
        case 8, 16, 19, 20 -> in.readUnsignedShort();
        case 9, 10, 11 -> refs[i] = new int[] {in.readUnsignedShort(), in.readUnsignedShort()};
        case 12 -> nat[i] = new int[] {in.readUnsignedShort(), in.readUnsignedShort()};
        case 15 -> {
          in.readUnsignedByte();
          in.readUnsignedShort();
        }
        case 17, 18 -> {
          in.readUnsignedShort();
          in.readUnsignedShort();
        }
        default -> throw new IOException("unknown constant pool tag " + tag);
      }
    }
    Set<String> out = new TreeSet<>();
    for (int i = 1; i < n; i++) {
      if (classIdx[i] != 0 && FORBIDDEN_TYPES.contains(utf8[classIdx[i]])) out.add("class " + utf8[classIdx[i]]);
      if (refs[i] == null) continue;
      String owner = utf8[classIdx[refs[i][0]]];
      int[] nt = nat[refs[i][1]];
      String name = utf8[nt[0]];
      String desc = utf8[nt[1]];
      String ref = owner + "." + name + desc;
      if (ALLOWED.containsKey(ref)) continue;
      if (forbidden(owner, name, desc)) out.add(ref);
    }
    return new ArrayList<>(out);
  }

  static boolean forbidden(String owner, String name, String desc) {
    if (FORBIDDEN_TYPES.contains(owner)) return true;
    switch (owner) {
      case "java/io/File":
        return !FILE_PURE.contains(name);
      case "java/io/FileInputStream", "java/io/FileOutputStream":
        // the constructors open the file; reading from / writing to a stream that ManagedFileAccess opened is fine
        return name.equals("<init>");
      case "java/nio/file/Path":
        return name.equals("toFile") || name.equals("toRealPath") || name.equals("register");
      case "java/net/URL":
        return name.equals("openStream") || name.equals("openConnection") || name.equals("getContent");
      case "java/io/PrintStream", "java/io/PrintWriter", "java/util/Formatter":
        // the constructors that open a file by name, File or Path
        return name.equals("<init>") && (desc.startsWith("(Ljava/lang/String;") || desc.startsWith("(Ljava/io/File;")
            || desc.startsWith("(Ljava/nio/file/Path;"));
      case "java/util/Scanner":
        return name.equals("<init>") && (desc.startsWith("(Ljava/io/File;") || desc.startsWith("(Ljava/nio/file/Path;"));
      case "org/hl7/fhir/utilities/filesystem/ManagedFileAccess":
        // inStream(File) / outStream(File) open the file without applying the access policy
        return (name.equals("inStream") || name.equals("outStream")) && desc.startsWith("(Ljava/io/File;");
      default:
        return false;
    }
  }

  static Path classesRoot(Class<?> c) throws URISyntaxException {
    return Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI());
  }

  @Test void mainCodeUsesOnlyManagedFileAndWebAccess() throws Exception {
    Path root = classesRoot(Fs.class);
    org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(root), "main classes are not a directory: " + root);
    List<String> bad = new ArrayList<>();
    int scanned = 0;
    try (Stream<Path> s = Files.walk(root)) {
      for (Path p : s.filter(f -> f.toString().endsWith(".class")).sorted().toList()) {
        String name = root.relativize(p).toString().replace('\\', '/').replaceFirst("\\.class$", "");
        if (!name.startsWith("org/hl7/fhir/sushi/")) continue; // common's classes in a --fast style layout
        scanned++;
        if (name.equals(ADAPTER)) continue;
        for (String v : violations(Files.readAllBytes(p))) bad.add(name + ": " + v);
      }
    }
    assertTrue(scanned > 200, "only " + scanned + " classes found under " + root);
    assertEquals(List.of(), bad, "use org.hl7.fhir.sushi.utils.Fs (ManagedFileAccess) or core's ManagedWebAccess");
  }

  /** The scanner itself: it finds each kind of forbidden use (in {@link Violations}, compiled but never run). */
  @Test void scannerFindsForbiddenUses() throws Exception {
    Path p = classesRoot(Violations.class).resolve(Violations.class.getName().replace('.', '/') + ".class");
    List<String> v = violations(Files.readAllBytes(p));
    assertEquals(List.of(
        "class java/net/http/HttpClient",
        "class java/nio/file/Files",
        "java/io/File.<init>(Ljava/lang/String;)V",
        "java/io/File.exists()Z",
        "java/io/FileInputStream.<init>(Ljava/lang/String;)V",
        "java/io/PrintWriter.<init>(Ljava/lang/String;)V",
        "java/net/URL.openStream()Ljava/io/InputStream;",
        "java/net/http/HttpClient.newHttpClient()Ljava/net/http/HttpClient;",
        "java/nio/file/Files.exists(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
        "java/nio/file/Files.readString(Ljava/nio/file/Path;)Ljava/lang/String;",
        "java/nio/file/Path.toFile()Ljava/io/File;",
        "org/hl7/fhir/utilities/filesystem/ManagedFileAccess.inStream(Ljava/io/File;)Ljava/io/FileInputStream;"), v);
    // and the uses that are fine: pure path handling, stdout, the policy-checked ManagedFileAccess calls
    Path ok = classesRoot(Fine.class).resolve(Fine.class.getName().replace('.', '/') + ".class");
    assertEquals(List.of(), violations(Files.readAllBytes(ok)));
  }

  @SuppressWarnings("unused")
  static final class Violations {
    void io() throws Exception {
      new java.io.File("x").exists();
      new java.io.FileInputStream("x").close();
      new java.io.PrintWriter("x").close();
      java.util.function.Predicate<Path> exists = Files::exists;
      Files.readString(Path.of("x"));
      Path.of("x").toFile();
      java.net.URI.create("https://example.org").toURL().openStream().close();
      java.net.http.HttpClient.newHttpClient();
      org.hl7.fhir.utilities.filesystem.ManagedFileAccess.inStream(new java.io.File("x")).close();
    }
  }

  @SuppressWarnings("unused")
  static final class Fine {
    void io() throws Exception {
      Path.of("a", "b").toAbsolutePath().normalize().getFileName();
      char c = java.io.File.separatorChar;
      new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true,
          java.nio.charset.StandardCharsets.UTF_8).flush();
      org.hl7.fhir.utilities.filesystem.ManagedFileAccess.inStream("x").close();
      org.hl7.fhir.utilities.filesystem.ManagedFileAccess.file("x").getName();
      new java.util.Scanner("text").close();
    }
  }
}

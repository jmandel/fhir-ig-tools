package org.hl7.fhir.sushi.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;

/**
 * The one place where SUSHI touches the file system (PORTING.md "I/O policy"). Every file and folder is obtained
 * through org.hl7.fhir.core's {@link ManagedFileAccess}, so the host's access policy (DIRECT with allowed paths,
 * MANAGED through an accessor, PROHIBITED) applies to SUSHI exactly as to core:
 * <ul>
 *   <li>file contents: {@link ManagedFileAccess#inStream(String)} / {@link ManagedFileAccess#outStream(String)};</li>
 *   <li>folder listings: {@link ManagedFileAccess#listFiles(File)};</li>
 *   <li>metadata ({@code exists}, {@code isDirectory}, {@code isFile}): on the {@link File} from
 *       {@link ManagedFileAccess#file(String)}.</li>
 * </ul>
 * ManagedFileAccess has no operation for the following, so they are done here on the {@link File} (or its
 * {@link Path}) that ManagedFileAccess returned, never on a path SUSHI built itself:
 * <ul>
 *   <li>{@link #isDirectoryNoFollow} / {@link #isRegularFileNoFollow}: lstat (Node's {@code Dirent.isDirectory()} /
 *       {@code isFile()} describe a symlink itself, not its target), {@code Files.isDirectory/isRegularFile} with
 *       {@code NOFOLLOW_LINKS};</li>
 *   <li>{@link #createDirectories}: {@code Files.createDirectories} (fs-extra {@code ensureDirSync}; it keeps NIO's
 *       exceptions, whose messages SUSHI reports, where {@code File.mkdirs()} only returns false);</li>
 *   <li>{@link #delete}: {@code Files.delete} (fs-extra {@code emptyDirSync}, used by {@code sushi init});</li>
 *   <li>{@link #setPosixPermissions}: {@code Files.setPosixFilePermissions} ({@code sushi init} makes
 *       {@code _build.sh} executable).</li>
 * </ul>
 * Errors keep the NIO exception types SUSHI had before it used ManagedFileAccess ({@link NoSuchFileException},
 * {@link AccessDeniedException}, ...), because {@code Processing.nodeMessage} turns them into Node's
 * {@code ENOENT}/{@code EACCES} texts: a {@link FileNotFoundException} from core's streams is translated by looking at
 * the file. Paths are handled like {@code java.nio}: an empty path means the current folder.
 * <p>
 * {@code build.ManagedIoRuleTest} fails the build when code outside this class uses java.io / java.nio.file / java.net
 * I/O directly.
 */
public final class Fs {
  private Fs() {}

  /** The path as NIO's UnixPath passes it to the system calls: {@code ""} is {@code "."}. */
  private static String sys(String p) {
    return p.isEmpty() ? "." : p;
  }

  /** {@link ManagedFileAccess#file(String)}. */
  public static File file(String p) throws IOException {
    return ManagedFileAccess.file(sys(p));
  }

  private static File fileUnchecked(String p) {
    try {
      return file(p);
    } catch (IOException e) {
      throw new UncheckedIOException(e.getMessage(), e);
    }
  }

  /** {@code Files.exists(p)} (follows symlinks); false for a path the file system can't represent. */
  public static boolean exists(String p) {
    return p.indexOf('\0') < 0 && fileUnchecked(p).exists();
  }

  /** {@code Files.isDirectory(p)} (follows symlinks). */
  public static boolean isDirectory(String p) {
    return p.indexOf('\0') < 0 && fileUnchecked(p).isDirectory();
  }

  /** {@code Files.isRegularFile(p)} (follows symlinks). */
  public static boolean isFile(String p) {
    return p.indexOf('\0') < 0 && fileUnchecked(p).isFile();
  }

  /** {@code Files.isDirectory(p, NOFOLLOW_LINKS)}: lstat on the managed file (adapter operation, see class doc). */
  public static boolean isDirectoryNoFollow(String p) {
    return p.indexOf('\0') < 0 && Files.isDirectory(fileUnchecked(p).toPath(), LinkOption.NOFOLLOW_LINKS);
  }

  /** {@code Files.isRegularFile(p, NOFOLLOW_LINKS)}: lstat on the managed file (adapter operation, see class doc). */
  public static boolean isRegularFileNoFollow(String p) {
    return p.indexOf('\0') < 0 && Files.isRegularFile(fileUnchecked(p).toPath(), LinkOption.NOFOLLOW_LINKS);
  }

  /**
   * The paths of the entries of folder {@code dir} ({@code dir} joined with each name, as {@code Files.list}), in the
   * order the file system returns them, via {@link ManagedFileAccess#listFiles(File)}. Throws like
   * {@code Files.list}: {@link NoSuchFileException}, {@link NotDirectoryException}, {@link AccessDeniedException}.
   */
  public static List<Path> list(String dir) throws IOException {
    File d = file(dir);
    File[] entries = ManagedFileAccess.listFiles(d);
    if (entries == null) {
      if (!d.exists()) throw new NoSuchFileException(dir);
      if (!d.isDirectory()) throw new NotDirectoryException(dir);
      throw new AccessDeniedException(dir);
    }
    Path base = Path.of(dir);
    List<Path> out = new ArrayList<>(entries.length);
    for (File e : entries) out.add(base.resolve(e.getName()));
    return out;
  }

  /** The entry names of folder {@code dir} (unsorted); as {@link #list}. */
  public static List<String> listNames(String dir) throws IOException {
    List<String> out = new ArrayList<>();
    for (Path p : list(dir)) out.add(p.getFileName().toString());
    return out;
  }

  /** {@code Files.readAllBytes(p)}, via {@link ManagedFileAccess#inStream(String)}. */
  public static byte[] readAllBytes(String p) throws IOException {
    InputStream in;
    try {
      in = ManagedFileAccess.inStream(sys(p));
    } catch (FileNotFoundException e) {
      throw translateRead(p, e);
    }
    try (in) {
      return in.readAllBytes();
    }
  }

  /** {@code new String(Files.readAllBytes(p), UTF_8)}: lenient UTF-8 (U+FFFD for malformed input), BOM kept. */
  public static String readUtf8(String p) throws IOException {
    return new String(readAllBytes(p), StandardCharsets.UTF_8);
  }

  /** {@code Files.readString(p, UTF_8)}: strict UTF-8, malformed input throws {@link CharacterCodingException}. */
  public static String readUtf8Strict(String p) throws IOException {
    byte[] bytes = readAllBytes(p);
    return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
  }

  /** {@code Files.write(p, bytes)} (create or truncate), via {@link ManagedFileAccess#outStream(String)}. */
  public static void write(String p, byte[] bytes) throws IOException {
    OutputStream out;
    try {
      out = ManagedFileAccess.outStream(sys(p));
    } catch (FileNotFoundException e) {
      throw translateWrite(p, e);
    }
    try (out) {
      out.write(bytes);
    }
  }

  /** {@code Files.createDirectories(p)} on the managed file (adapter operation, see class doc). */
  public static void createDirectories(String p) throws IOException {
    Files.createDirectories(file(p).toPath());
  }

  /** {@code Files.delete(p)} on the managed file (adapter operation, see class doc). */
  public static void delete(String p) throws IOException {
    Files.delete(file(p).toPath());
  }

  /**
   * {@code Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(perms))} on the managed file (adapter
   * operation, see class doc); throws {@link UnsupportedOperationException} on a non-POSIX file system.
   */
  public static void setPosixPermissions(String p, String perms) throws IOException {
    Files.setPosixFilePermissions(file(p).toPath(), PosixFilePermissions.fromString(perms));
  }

  // FileInputStream/FileOutputStream report every failure to open as FileNotFoundException with a platform text;
  // give the exception java.nio would have thrown for the same file.

  private static IOException translateRead(String p, FileNotFoundException e) throws IOException {
    File f = file(p);
    String s = Path.of(sys(p)).toString();
    IOException r = !f.exists() ? new NoSuchFileException(s)
        : f.isDirectory() ? new IOException("Is a directory")
        : new AccessDeniedException(s);
    r.initCause(e);
    return r;
  }

  private static IOException translateWrite(String p, FileNotFoundException e) throws IOException {
    File f = file(p);
    String s = Path.of(sys(p)).toString();
    File parent = f.getAbsoluteFile().getParentFile();
    IOException r = f.isDirectory() ? new FileSystemException(s, null, "Is a directory")
        : parent != null && !parent.exists() ? new NoSuchFileException(s)
        : parent != null && !parent.isDirectory() ? new FileSystemException(s, null, "Not a directory")
        : new AccessDeniedException(s);
    r.initCause(e);
    return r;
  }
}

package org.hl7.fhir.sushi.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.List;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess;
import org.hl7.fhir.utilities.filesystem.ManagedFileAccess.FileAccessPolicy;
import org.hl7.fhir.utilities.http.ManagedWebAccess;
import org.hl7.fhir.utilities.http.ManagedWebAccess.WebAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link Fs}: java.nio semantics and exceptions on top of core's ManagedFileAccess, and the policy applying. */
class FsTest {
  @TempDir Path tmp;

  @Test void readsWritesAndLists() throws IOException {
    String f = tmp.resolve("a.txt").toString();
    Fs.write(f, "hé".getBytes(StandardCharsets.UTF_8));
    assertEquals("hé", Fs.readUtf8(f));
    assertTrue(Fs.exists(f) && Fs.isFile(f) && !Fs.isDirectory(f) && Fs.isRegularFileNoFollow(f));
    Fs.createDirectories(tmp.resolve("d/e").toString());
    assertTrue(Fs.isDirectoryNoFollow(tmp.resolve("d/e").toString()));
    List<String> names = Fs.listNames(tmp.toString());
    names.sort(null);
    assertEquals(List.of("a.txt", "d"), names);
    assertEquals(List.of(tmp.resolve("d/e")), Fs.list(tmp.resolve("d").toString()));
    Fs.delete(f);
    assertFalse(Fs.exists(f));
    assertFalse(Fs.exists("a\0b"));
  }

  @Test void keepsNioExceptions() throws IOException {
    String missing = tmp.resolve("missing.txt").toString();
    NoSuchFileException e = assertThrows(NoSuchFileException.class, () -> Fs.readAllBytes(missing));
    assertEquals(missing, e.getFile());
    assertEquals("ENOENT: no such file or directory, " + missing, Processing.nodeMessage(e));
    assertEquals("Is a directory", assertThrows(IOException.class, () -> Fs.readAllBytes(tmp.toString())).getMessage());
    String inMissingDir = tmp.resolve("no/such/dir/x.txt").toString();
    assertEquals(inMissingDir, assertThrows(NoSuchFileException.class, () -> Fs.write(inMissingDir, new byte[0])).getFile());
    assertThrows(NoSuchFileException.class, () -> Fs.list(tmp.resolve("missing").toString()));
    Files.writeString(tmp.resolve("f"), "x");
    assertThrows(NotDirectoryException.class, () -> Fs.list(tmp.resolve("f").toString()));
    Files.write(tmp.resolve("bad"), new byte[] {(byte) 0xC3, 0x28});
    assertEquals("�(", Fs.readUtf8(tmp.resolve("bad").toString()));
    assertThrows(CharacterCodingException.class, () -> Fs.readUtf8Strict(tmp.resolve("bad").toString()));
  }

  @Test void emptyPathIsTheCurrentFolderLikeNio() {
    assertEquals(Files.isDirectory(Path.of("")), Fs.isDirectory(""));
    assertEquals(Files.exists(Path.of("")), Fs.exists(""));
  }

  @Test void appliesCoreFileAccessPolicy() throws IOException {
    String f = tmp.resolve("p.txt").toString();
    Fs.write(f, new byte[] {1});
    FileAccessPolicy before = ManagedFileAccess.getAccessPolicy();
    ManagedFileAccess.setAccessPolicy(FileAccessPolicy.PROHIBITED);
    try {
      String msg = "Access to files is not allowed by local security policy";
      assertEquals(msg, assertThrows(IOException.class, () -> Fs.readAllBytes(f)).getMessage());
      assertEquals(msg, assertThrows(IOException.class, () -> Fs.write(f, new byte[0])).getMessage());
      assertEquals(msg, assertThrows(IOException.class, () -> Fs.listNames(tmp.toString())).getMessage());
      assertEquals(msg, assertThrows(UncheckedIOException.class, () -> Fs.exists(f)).getMessage());
      assertEquals(List.of(), Processing.readdir(tmp.toString())); // fs.readdirSync failures read as an empty folder
    } finally {
      ManagedFileAccess.setAccessPolicy(before);
    }
    assertArrayEquals(new byte[] {1}, Fs.readAllBytes(f));
  }

  @Test void appliesCoreWebAccessPolicy() {
    WebAccessPolicy before = ManagedWebAccess.getAccessPolicy();
    ManagedWebAccess.setAccessPolicy(WebAccessPolicy.PROHIBITED);
    try {
      IOException e = assertThrows(IOException.class, () -> Processing.webGet("https://registry.npmjs.org/fsh-sushi"));
      assertEquals("Access to the internet is not allowed by local security policy", e.getMessage());
      // #latest lookups fall back to the next registry and then give up, as on a network error
      assertNull(assertDoesNotThrow(
          () -> new org.hl7.fhir.sushi.fhirdefs.CorePackageProvider(tmp.toString()).resolveLatest("hl7.fhir.us.core")));
    } finally {
      ManagedWebAccess.setAccessPolicy(before);
    }
  }
}

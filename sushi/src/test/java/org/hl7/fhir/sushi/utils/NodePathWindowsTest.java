package org.hl7.fhir.sushi.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** NodePath's Windows mode (drive roots, backslashes), checked on any OS by switching it on. */
class NodePathWindowsTest {
  private boolean saved;

  @BeforeEach void on() { saved = NodePath.WINDOWS; NodePath.WINDOWS = true; }

  @AfterEach void off() { NodePath.WINDOWS = saved; }

  @Test void driveRootsAreAbsolute() {
    assertTrue(NodePath.isAbsolute("C:\\Users\\x\\ig"));
    assertTrue(NodePath.isAbsolute("d:/a"));
    assertFalse(NodePath.isAbsolute("ig\\input"));
    assertEquals("C:/Users/x/ig", NodePath.resolve("D:/a/b", "C:\\Users\\x\\ig"));
    assertEquals("C:/Users/x/ig/input/fsh", NodePath.join("C:\\Users\\x\\ig", "input", "fsh"));
    assertEquals("C:/Users/x/ig/input", NodePath.resolve("C:\\Users\\x\\ig", "input"));
    assertEquals("C:/a", NodePath.normalize("C:\\a\\b\\.."));
    assertEquals("C:/", NodePath.normalize("C:\\"));
  }

  @Test void dirnameBasenameRelative() {
    assertEquals("C:/Users/x", NodePath.dirname("C:\\Users\\x\\ig"));
    assertEquals("C:/", NodePath.dirname("C:\\ig"));
    assertEquals("ig", NodePath.basename("C:\\Users\\x\\ig"));
    assertEquals(".fsh", NodePath.extname("C:\\ig\\a.fsh"));
    assertEquals("input/fsh/a.fsh", NodePath.relative("C:\\ig", "C:\\ig\\input\\fsh\\a.fsh"));
    assertEquals("../b", NodePath.relative("C:/x/a", "C:/x/b"));
    assertEquals("D:/b", NodePath.relative("C:/a", "D:/b"));
  }

  @Test void posixUnchangedWhenOff() {
    NodePath.WINDOWS = false;
    assertFalse(NodePath.isAbsolute("C:/a"));
    assertEquals("a\\b", NodePath.normalize("a\\b"));
  }
}

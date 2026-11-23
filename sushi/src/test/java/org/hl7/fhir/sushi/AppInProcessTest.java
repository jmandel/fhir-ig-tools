package org.hl7.fhir.sushi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link App#runInProcess}: the entry point the IG Publisher calls to run SUSHI in its own JVM. */
class AppInProcessTest {
  @TempDir Path dir;

  @AfterEach void after() {
    FshLogger.logger.resetForTest();
  }

  private static String run(ByteArrayOutputStream buf, String... args) {
    buf.reset();
    PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8);
    int code = App.runInProcess(args, ps);
    return code + "\n" + buf.toString(StandardCharsets.UTF_8);
  }

  @Test void outputAndExitStatusAreReturnedAndEachRunStartsClean() throws Exception {
    PrintStream consoleOut = App.out, consoleErr = App.err;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    // FSH but no sushi-config.yaml: an error, exit 1, as on the command line
    Path bad = Files.createDirectories(dir.resolve("bad/input/fsh"));
    Files.writeString(bad.resolve("a.fsh"), "Profile: P\nParent: Patient\n");
    String r1 = run(buf, "build", bad.getParent().getParent().toString());
    assertTrue(r1.startsWith("1\n"), r1);
    assertTrue(r1.contains("\nerror "), r1);
    assertTrue(r1.contains("info  Running SUSHI"), r1);
    assertFalse(r1.contains("\u001b["), "no colour: " + r1);
    // a project with an empty input/fsh: nothing to do, exit 0; the error of the first run isn't counted again
    Path empty = Files.createDirectories(dir.resolve("empty/input/fsh"));
    String r2 = run(buf, empty.getParent().getParent().toString());
    assertTrue(r2.startsWith("0\n"), r2);
    assertTrue(r2.contains("No FSH files or sushi-config.yaml present."), r2);
    assertFalse(r2.contains("error"), r2);
    assertEquals(0, FshLogger.logger.stats.numError, "the logger is clean after a run");
    // the console streams are restored
    assertSame(consoleOut, App.out);
    assertSame(consoleErr, App.err);
    assertSame(consoleErr, App.stderr());
  }

  @Test void onlyBuildRuns() {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    for (String command : new String[] {"init", "update-dependencies", "help"}) {
      String r = run(buf, command, dir.toString());
      assertTrue(r.startsWith("1\n"), r);
      assertTrue(r.contains("sushi in process runs only 'build', not '" + command + "'"), r);
    }
  }

  @Test void syntaxErrorsOfTheConsoleListenerGoToTheOutput() {
    PrintStream saved = App.err;
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    App.err = new PrintStream(buf, true, StandardCharsets.UTF_8);
    try {
      org.hl7.fhir.sushi.importer.parser.ErrorListener.CONSOLE.syntaxError(null, null, 3, 4, "mismatched input");
    } finally {
      App.err = saved;
    }
    assertEquals("line 3:4 mismatched input", buf.toString(StandardCharsets.UTF_8).strip());
  }
}

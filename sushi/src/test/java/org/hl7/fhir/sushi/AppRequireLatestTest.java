package org.hl7.fhir.sushi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.Processing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code sushi build --require-latest} (the IG Publisher passes it on the ci-build): the port checks the latest
 * fhir-ig-tools release, not npm's fsh-sushi (docs/core-divergences.md T3). The release fetch is stubbed.
 */
class AppRequireLatestTest {
  private PrintStream savedOut;
  private Processing.ReleaseSource savedSource;
  private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
  @TempDir Path dir;

  @BeforeEach void before() {
    savedOut = App.out;
    savedSource = Processing.releaseSource;
    App.out = new PrintStream(buf, true, StandardCharsets.UTF_8);
  }

  @AfterEach void after() {
    App.out = savedOut;
    Processing.releaseSource = savedSource;
    FshLogger.logger.resetForTest();
  }

  private String output() {
    return buf.toString(StandardCharsets.UTF_8);
  }

  @Test void failsWhenANewerReleaseHasSushi() {
    Processing.releaseSource = url -> org.hl7.fhir.common.js.Json.parse(
        "{\"tag_name\":\"v99.0.0\",\"assets\":[{\"name\":\"sushi.jar\"}]}");
    int code = App.run(new String[] {"build", dir.toString(), "--require-latest"});
    assertEquals(1, code);
    assertTrue(output().contains("is not the latest version"), output());
    assertTrue(output().contains("the latest fhir-ig-tools release is 99.0.0."), output());
    assertFalse(output().contains("npm"), output());
  }

  @Test void failsWhenTheLatestReleaseIsUndetermined() {
    Processing.releaseSource = url -> {
      throw new java.io.IOException("Request failed with status code 503");
    };
    int code = App.run(new String[] {"build", dir.toString(), "--require-latest"});
    assertEquals(1, code);
    assertTrue(output().contains("could not be checked: the latest fhir-ig-tools release is undetermined"), output());
  }

  @Test void goesOnWhenTheLatestReleaseHasNoSushi() throws Exception {
    // the only release today, v0.1.0, has no sushi.jar: the check passes and the build goes on (here: to the
    // project-structure error for an empty folder, exit 1, after the check)
    Processing.releaseSource = url -> org.hl7.fhir.common.js.Json.parse(
        "{\"tag_name\":\"v0.1.0\",\"assets\":[{\"name\":\"jekyll.jar\"}]}");
    Files.createDirectories(dir.resolve("input/fsh"));
    App.run(new String[] {"build", dir.toString(), "--require-latest"});
    assertFalse(output().contains("is not the latest version"), output());
    assertFalse(output().contains("could not be checked"), output());
    assertTrue(output().contains("--require-latest"), output());
  }
}

package org.hl7.fhir.sushi.testhelpers;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;

/**
 * Test data that cannot live in the repository (the 267-IG corpus, recorded TypeScript SUSHI runs, node-generated
 * oracle sets): opt-in. A test asks for a directory by its own system property ({@code -Dwp1.data=...}) or, for all of
 * them at once, {@code -Dsushi.vmdata=<data root>} (the build VM's layout: {@code <root>/sushi-wp3},
 * {@code <root>/sushi-port/ref}, ...). Without either, the test is skipped and says which property to set. The TS
 * SUSHI test fixtures are not opt-in: they are vendored ({@link TestFHIRDefinitions#tsDir()}).
 */
public final class OptInData {
  private OptInData() {}

  /** The directory, or null when neither {@code property} nor {@code sushi.vmdata} is set. */
  public static Path dir(String property, String vmSubdir) {
    String p = System.getProperty(property, "");
    if (!p.isEmpty()) return Path.of(p);
    String vm = System.getProperty("sushi.vmdata", "");
    return vm.isEmpty() ? null : Path.of(vm, vmSubdir);
  }

  /** The directory; skips the calling test (with the reason) when it is not configured or not there. */
  public static Path require(String property, String vmSubdir, String what) {
    Path p = dir(property, vmSubdir);
    Assumptions.assumeTrue(p != null, "opt-in: " + what + " is not in the repository; run with -D" + property
        + "=<dir> or -Dsushi.vmdata=<data root> (see sushi/README.md)");
    Assumptions.assumeTrue(Files.isDirectory(p), "opt-in: " + what + " not found at " + p);
    return p;
  }
}

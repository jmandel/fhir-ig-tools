package org.hl7.fhir.sushi.build;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * PORTING.md D1: main code may import only the JDK, our own packages (sushi, and common's shared code) and
 * org.hl7.fhir.core's utilities (r4/r5 model
 * modules only for WP-3's ig.xml reading). Libraries that arrive transitively with core (jackson, guava, commons-*,
 * okhttp, icu4j, saxon, slf4j...) must not be used directly. Runs from the checkout (surefire's working dir).
 */
class DependencyRuleTest {
  static final List<String> ALLOWED =
      List.of("java.", "javax.xml.", "org.hl7.fhir.sushi.", "org.hl7.fhir.common.", "org.hl7.fhir.utilities.");
  static final Map<String, List<String>> ALLOWED_IN = Map.of(
      "org.hl7.fhir.r4.", List.of("importer/config/"), "org.hl7.fhir.r5.", List.of("importer/config/"));

  @Test void mainCodeImportsOnlyAllowedPackages() throws IOException {
    Path root = Paths.get("src/main/java/org/hl7/fhir/sushi");
    org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(root));
    Pattern imp = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)", Pattern.MULTILINE);
    List<String> bad = new ArrayList<>();
    try (Stream<Path> s = Files.walk(root)) {
      for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
        String rel = root.relativize(p).toString().replace('\\', '/');
        Matcher m = imp.matcher(Files.readString(p));
        while (m.find()) {
          String i = m.group(1);
          if (ALLOWED.stream().anyMatch(i::startsWith)) continue;
          if (ALLOWED_IN.entrySet().stream().anyMatch(e -> i.startsWith(e.getKey()) && e.getValue().stream().anyMatch(rel::startsWith))) continue;
          bad.add(rel + ": " + i);
        }
      }
    }
    assertEquals(List.of(), bad);
  }

  /** The pom's non-test dependencies are exactly fhir-ig-common, core's utilities and commons-compress (runtime). */
  @Test void pomRuntimeDependenciesAreCoreUtilitiesAndCommonsCompressOnly() throws IOException {
    Path pom = Paths.get("pom.xml");
    org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(pom));
    String xml = Files.readString(pom);
    String deps = xml.substring(xml.indexOf("<dependencies>"), xml.indexOf("</dependencies>"));
    Matcher m = Pattern.compile("<dependency>(.*?)</dependency>", Pattern.DOTALL).matcher(deps);
    List<String> nonTest = new ArrayList<>();
    while (m.find()) {
      String d = m.group(1);
      if (d.contains("<scope>test</scope>")) continue;
      Matcher a = Pattern.compile("<artifactId>([^<]+)</artifactId>").matcher(d);
      if (a.find()) nonTest.add(a.group(1));
    }
    assertEquals(List.of("fhir-ig-common", "org.hl7.fhir.utilities", "commons-compress"), nonTest);
  }
}

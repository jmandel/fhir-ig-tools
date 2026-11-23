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
 * org.hl7.fhir.core's utilities; the r4/r4b/r5 model modules only in fhirdefs/FhirXml (FHIR XML of predefined
 * resources and a legacy ig.xml, docs/core-divergences.md C6), by import or by fully qualified name. Libraries that
 * arrive transitively with core (jackson, guava, commons-*, okhttp, icu4j, saxon, slf4j, xpp3...) must not be used
 * directly. Runs from the checkout (surefire's working dir).
 */
class DependencyRuleTest {
  static final List<String> ALLOWED =
      List.of("java.", "javax.xml.", "org.hl7.fhir.sushi.", "org.hl7.fhir.common.", "org.hl7.fhir.utilities.");
  static final List<String> MODEL_ONLY_IN = List.of("fhirdefs/FhirXml.java");
  static final Map<String, List<String>> ALLOWED_IN = Map.of(
      "org.hl7.fhir.r4.", MODEL_ONLY_IN, "org.hl7.fhir.r4b.", MODEL_ONLY_IN, "org.hl7.fhir.r5.", MODEL_ONLY_IN);
  /** a fully qualified use of a model package in code (not in a comment line) */
  static final Pattern MODEL_FQN = Pattern.compile("^(?!\\s*(?://|\\*|/\\*)).*\\borg\\.hl7\\.fhir\\.(r4|r4b|r5)\\.",
      Pattern.MULTILINE);

  @Test void mainCodeImportsOnlyAllowedPackages() throws IOException {
    Path root = Paths.get("src/main/java/org/hl7/fhir/sushi");
    org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(root));
    Pattern imp = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)", Pattern.MULTILINE);
    List<String> bad = new ArrayList<>();
    try (Stream<Path> s = Files.walk(root)) {
      for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
        String rel = root.relativize(p).toString().replace('\\', '/');
        String src = Files.readString(p);
        if (!MODEL_ONLY_IN.contains(rel) && MODEL_FQN.matcher(src).find()) bad.add(rel + ": fully qualified FHIR model use");
        Matcher m = imp.matcher(src);
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

  /**
   * The pom's non-test dependencies are exactly fhir-ig-common, core's utilities and r4/r4b/r5 model modules, xpp3
   * and (runtime) commons-compress; core needs the last two but declares them optional.
   */
  @Test void pomRuntimeDependenciesAreCoreModulesOnly() throws IOException {
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
    assertEquals(List.of("fhir-ig-common", "org.hl7.fhir.utilities", "org.hl7.fhir.r4", "org.hl7.fhir.r4b",
        "org.hl7.fhir.r5", "xpp3", "commons-compress"), nonTest);
  }
}

package org.hl7.fhir.sushi.fhirtypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.common.js.Json;
import org.junit.jupiter.api.Test;

/**
 * WP-5 gate (PORTING.md D2, key-order fidelity): every resource TS SUSHI 3.21.1 wrote for the reference IGs is read
 * back into the WP-5 model class that produced it (per fsh-index.json fshType) and serialized again; the bytes must
 * be identical.
 *
 * <ul>
 *   <li>Profile/Extension/Logical/Resource: StructureDefinition.fromJSON then toJSON (snapshot = ElementDefinition
 *       round trip, WP-4) with the original differential substituted (a differential is computed against the parent,
 *       which a read-back cannot know); each differential element is checked separately via
 *       ElementDefinition.fromJSON/toJSON (WP-4).</li>
 *   <li>CodeSystem / ValueSet (FSH CodeSystem/ValueSet): new CodeSystem()/ValueSet() (initialised keys first) then
 *       every key assigned in file order, then toJSON: proves the "initialised fields first, then assignment order,
 *       then _x after x" model reproduces SUSHI's order.</li>
 *   <li>Instances: InstanceDefinition.fromJSON then toJSON (resourceType/id/meta first, _x after x).</li>
 * </ul>
 * Input: {@code -Dsushi.wp5.gate.dir} (default /srv/ig/sushi-port/wp5-gate, the extracted reference runs); skipped
 * when absent. Writes a summary to stdout.
 */
class KeyOrderGateTest {
  static final Path GATE = Path.of(System.getProperty("sushi.wp5.gate.dir", "/srv/ig/sushi-port/wp5-gate"));

  record Result(String ig, String file, String kind, String firstDiff) {}

  @Test
  void referenceOutputRoundTripsByteIdentically() throws IOException {
    assumeTrue(Files.isDirectory(GATE), "gate dir not available: " + GATE);
    List<Result> failures = new ArrayList<>();
    Map<String, int[]> counts = new TreeMap<>();
    List<Path> igs;
    try (Stream<Path> s = Files.list(GATE)) {
      igs = s.filter(Files::isDirectory).sorted().toList();
    }
    for (Path ig : igs) {
      Path res = ig.resolve("fsh-generated/resources");
      Path index = ig.resolve("fsh-generated/data/fsh-index.json");
      if (!Files.isDirectory(res)) continue;
      Map<String, String> fshType = new LinkedHashMap<>();
      if (Files.exists(index)) {
        for (Object e : (List<?>) Json.parseFile(Files.readString(index))) {
          fshType.put((String) Common.getProp(e, "outputFile"), (String) Common.getProp(e, "fshType"));
        }
      }
      List<Path> files;
      try (Stream<Path> s = Files.list(res)) {
        files = s.filter(p -> p.toString().endsWith(".json")).sorted().toList();
      }
      for (Path f : files) {
        String name = f.getFileName().toString();
        String text = Files.readString(f);
        Object json = Json.parseFile(text);
        String type = fshType.getOrDefault(name, "?");
        String kind = switch (type) {
          case "Profile", "Extension", "Logical", "Resource" -> "SD";
          case "CodeSystem" -> "CodeSystem";
          case "ValueSet" -> "ValueSet";
          case "Instance" -> "Instance";
          default -> name.startsWith("ImplementationGuide-") ? "IG" : "other:" + type;
        };
        String diff = check(kind, json, text);
        int[] c = counts.computeIfAbsent(kind, k -> new int[2]);
        c[0]++;
        if (diff == null) {
          c[1]++;
        } else {
          failures.add(new Result(ig.getFileName().toString(), name, kind, diff));
        }
        if (kind.equals("SD")) {
          for (Object el : (List<?>) Common.getProp(Common.getProp(json, "differential"), "element")) {
            String expected = Json.outputJson(el);
            String actual = Json.outputJson(ElementDefinition.fromJSON(el, false).toJSON());
            int[] d = counts.computeIfAbsent("SD.differential.element", k -> new int[2]);
            d[0]++;
            if (expected.equals(actual)) {
              d[1]++;
            } else {
              failures.add(new Result(ig.getFileName().toString(), name, "SD.differential.element",
                  firstDiff(expected, actual)));
            }
          }
        }
      }
    }
    StringBuilder sb = new StringBuilder("WP-5 key-order gate (identical/total per kind):\n");
    counts.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v[1]).append('/').append(v[0])
        .append('\n'));
    int shown = 0;
    for (Result r : failures) {
      if (shown++ >= 40) break;
      sb.append("  DIFF ").append(r.ig).append(' ').append(r.file).append(" [").append(r.kind).append("] ")
          .append(r.firstDiff).append('\n');
    }
    System.out.println(sb);
    Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "wp5-key-order-gate.txt"), sb.toString());
    assertEquals(0, failures.size(), sb.toString());
  }

  static String check(String kind, Object json, String text) {
    String actual;
    switch (kind) {
      case "SD" -> {
        // SUSHI writes snapshot-less SDs unless configured otherwise: read those back with the differential
        // elements standing in for the snapshot, and serialize without one
        boolean hasSnapshot = Common.getProp(json, "snapshot") != null;
        Object in = json;
        if (!hasSnapshot) {
          JsObject copy = new JsObject(asMap((Map<?, ?>) json));
          copy.put("snapshot", new JsObject().with("element", Common.getProp(copy.get("differential"), "element")));
          in = copy;
        }
        StructureDefinition sd = StructureDefinition.fromJSON(in);
        JsObject out = (JsObject) sd.toJSON(hasSnapshot);
        Object origDiff = Common.getProp(json, "differential");
        out.put("differential", origDiff);
        actual = Json.outputJson(out);
      }
      case "CodeSystem" -> {
        CodeSystem cs = new CodeSystem();
        Map<?, ?> m = (Map<?, ?>) json;
        for (String k : Js.ownKeys(asMap(m))) cs.put(k, m.get(k));
        actual = Json.outputJson(cs);
      }
      case "ValueSet" -> {
        ValueSet vs = new ValueSet();
        Map<?, ?> m = (Map<?, ?>) json;
        for (String k : Js.ownKeys(asMap(m))) vs.put(k, m.get(k));
        actual = Json.outputJson(vs);
      }
      case "Instance" -> actual = Json.outputJson(InstanceDefinition.fromJSON(asMap((Map<?, ?>) json)));
      case "IG" -> actual = Json.outputJson(new ImplementationGuide(asMap((Map<?, ?>) json)));
      default -> {
        return null;
      }
    }
    return text.equals(actual) ? null : firstDiff(text, actual);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> asMap(Map<?, ?> m) {
    return (Map<String, Object>) m;
  }

  static String firstDiff(String expected, String actual) {
    String[] e = expected.split("\n", -1);
    String[] a = actual.split("\n", -1);
    for (int i = 0; i < Math.max(e.length, a.length); i++) {
      String x = i < e.length ? e[i] : "<EOF>";
      String y = i < a.length ? a[i] : "<EOF>";
      if (!x.equals(y)) {
        return "line " + (i + 1) + ": expected <" + trim(x) + "> got <" + trim(y) + ">";
      }
    }
    return "?";
  }

  static String trim(String s) {
    return s.length() > 160 ? s.substring(0, 160) + "..." : s;
  }
}

package org.hl7.fhir.sushi.importer.config;

import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.testhelpers.LoggerSpy;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.yaml.Yaml;
import org.hl7.fhir.sushi.yaml.YamlError;

/**
 * Runs the Java port on the inputs of scripts/oracle/config-oracle.js and compares with its output (TS SUSHI 3.21.1):
 * the YAML.parse value or error, the configuration object (tagged JSON: key order and undefined-valued keys count),
 * the thrown error, the log messages, and the typed round trip (Configuration back to the TS object).
 */
final class ConfigOracle {
  private ConfigOracle() {}

  /** One input's comparison: empty difference lists mean identical. */
  record Result(String name, List<String> diffs, List<String> typedDiffs) {}

  /** The oracle dir (VM): corpus-inputs.json + corpus-oracle.json, mutation-inputs.json + mutation-oracle.json. */
  static Path dir() {
    return Path.of(System.getProperty("sushi.config.oracle.dir", "/srv/ig/sushi-wp3"));
  }

  @SuppressWarnings("unchecked")
  static List<Result> run(Path inputs, Path oracle) throws IOException {
    List<Object> in = (List<Object>) Json.parse(Files.readString(inputs));
    Map<String, Object> out = (Map<String, Object>) Json.parse(Files.readString(oracle));
    List<Result> results = new ArrayList<>();
    for (Object o : in) {
      Map<String, Object> inp = (Map<String, Object>) o;
      String name = (String) inp.get("name");
      results.add(compare(name, (String) inp.get("text"), (String) inp.get("file"),
          (Map<String, Object>) out.get(name)));
    }
    return results;
  }

  @SuppressWarnings("unchecked")
  static Result compare(String name, String text, String file, Map<String, Object> ts) {
    List<String> diffs = new ArrayList<>();
    List<String> typedDiffs = new ArrayList<>();
    // 1. YAML.parse
    try {
      Object v = Yaml.parse(text);
      if (ts.containsKey("yamlError")) {
        diffs.add("yaml: TS error " + ((Map<String, Object>) ts.get("yamlError")).get("message") + ", Java parsed");
      } else if (!Json.stringify(dump(v)).equals(Json.stringify(ts.get("yaml")))) {
        diffs.add("yaml: value differs: " + firstDiff(dump(v), ts.get("yaml"), ""));
      }
    } catch (YamlError e) {
      if (!ts.containsKey("yamlError")) {
        diffs.add("yaml: Java error " + e.getMessage() + ", TS parsed");
      } else {
        Map<String, Object> te = (Map<String, Object>) ts.get("yamlError");
        if (!e.getMessage().equals(te.get("message"))) {
          diffs.add("yaml: error message: TS '" + te.get("message") + "' Java '" + e.getMessage() + "'");
        }
      }
    }
    // 2. importConfiguration
    LoggerSpy spy = loggerSpy;
    spy.reset();
    FshLogger.logger.resetForTest();
    JsObject config = null;
    String threw = null;
    try {
      config = ImportConfiguration.importConfigurationJs(text, file);
    } catch (ConfigurationError e) {
      threw = "Error: " + e.getMessage();
    } catch (JsOps.JsTypeError e) {
      threw = "TypeError: " + e.getMessage();
    }
    List<Object> logs = new ArrayList<>();
    for (LoggerSpy.Log l : spy.getAllLogs()) {
      if (!l.level().equals("debug")) logs.add(new JsObject().with("level", l.level()).with("message", l.message()));
    }
    if (ts.containsKey("threw")) {
      Map<String, Object> t = (Map<String, Object>) ts.get("threw");
      String tsThrew = t.get("name") + ": " + t.get("message");
      if (!tsThrew.equals(threw)) diffs.add("threw: TS '" + tsThrew + "' Java '" + threw + "'");
    } else if (threw != null) {
      diffs.add("threw: TS returned, Java '" + threw + "'");
    } else {
      Object tc = ts.get("config");
      if (!Json.stringify(dump(config)).equals(Json.stringify(tc))) {
        diffs.add("config: " + firstDiff(dump(config), tc, ""));
      }
      JsObject back = ConfigurationJs.toJs(ConfigurationJs.fromJs(config));
      if (!Json.stringify(dump(back)).equals(Json.stringify(dump(config)))) {
        typedDiffs.add("typed: " + firstDiff(dump(back), dump(config), ""));
      }
    }
    // the TS transport spy also sees debug entries (e.g. YAML error stacks, which differ by nature): not compared
    List<Object> tsLogs = new ArrayList<>();
    for (Object l : (List<Object>) ts.get("logs")) {
      if (!"debug".equals(((Map<String, Object>) l).get("level"))) tsLogs.add(l);
    }
    if (!Json.stringify(logs).equals(Json.stringify(tsLogs))) {
      diffs.add("logs: TS " + Json.stringify(tsLogs) + " Java " + Json.stringify(logs));
    }
    return new Result(name, diffs, typedDiffs);
  }

  /** The oracle's tagged JSON (config-oracle.js dump()). */
  static Object dump(Object v) {
    if (v == Js.UNDEFINED) return new JsObject().with("$u", 1.0);
    if (v == null || v instanceof String || v instanceof Boolean) return v;
    if (v instanceof Double d) {
      if (d.isNaN() || d.isInfinite() || (d == 0 && 1 / d < 0)) {
        return new JsObject().with("$n", d.isNaN() ? "NaN" : d == 0 ? "-0" : d > 0 ? "Infinity" : "-Infinity");
      }
      return d;
    }
    if (v instanceof List<?> l) {
      List<Object> out = new ArrayList<>();
      for (Object o : l) out.add(dump(o));
      return out;
    }
    if (v instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked") Map<String, Object> mm = (Map<String, Object>) m;
      List<Object> entries = new ArrayList<>();
      for (String k : Js.ownKeys(mm)) entries.add(java.util.Arrays.asList(k, dump(mm.get(k))));
      return new JsObject().with("$o", entries);
    }
    return new JsObject().with("$other", String.valueOf(v));
  }

  /** The first differing path, for readable reports. */
  @SuppressWarnings("unchecked")
  static String firstDiff(Object a, Object b, String path) {
    String sa = Json.stringify(a), sb = Json.stringify(b);
    if (sa.equals(sb)) return null;
    if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb && ma.containsKey("$o") && mb.containsKey("$o")) {
      List<Object> ea = (List<Object>) ma.get("$o"), eb = (List<Object>) mb.get("$o");
      for (int i = 0; i < Math.max(ea.size(), eb.size()); i++) {
        if (i >= ea.size() || i >= eb.size()) return path + ": key count Java " + ea.size() + " TS " + eb.size();
        List<Object> pa = (List<Object>) ea.get(i), pb = (List<Object>) eb.get(i);
        if (!pa.get(0).equals(pb.get(0))) return path + ": key #" + i + " Java " + pa.get(0) + " TS " + pb.get(0);
        String d = firstDiff(pa.get(1), pb.get(1), path + "/" + pa.get(0));
        if (d != null) return d;
      }
    }
    if (a instanceof List<?> la && b instanceof List<?> lb) {
      for (int i = 0; i < Math.max(la.size(), lb.size()); i++) {
        if (i >= la.size() || i >= lb.size()) return path + ": length Java " + la.size() + " TS " + lb.size();
        String d = firstDiff(la.get(i), lb.get(i), path + "/" + i);
        if (d != null) return d;
      }
    }
    return path + ": Java " + abbreviate(sa) + " TS " + abbreviate(sb);
  }

  private static String abbreviate(String s) {
    return s.length() > 200 ? s.substring(0, 200) + "..." : s;
  }

  static Map<String, Integer> summary(List<Result> results) {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put("inputs", results.size());
    m.put("identical", (int) results.stream().filter(r -> r.diffs().isEmpty()).count());
    m.put("typedRoundTripExact", (int) results.stream().filter(r -> r.typedDiffs().isEmpty()).count());
    return m;
  }
}

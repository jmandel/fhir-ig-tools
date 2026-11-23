package org.hl7.fhir.sushi.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.common.js.Js;

// Replaces the YAML.parseDocument(...).set(...).toString() round trip of TS updateExternalDependencies
// (sushi/src/utils/Processing.ts, SUSHI 3.21.1).
/**
 * Writes updated dependency versions into sushi-config.yaml text. TS re-serialises the whole document with the yaml
 * library (which may reformat the file, as its prompt warns); the port edits only the version scalars of the
 * top-level {@code dependencies} block in place and keeps everything else byte for byte (docs/core-divergences.md T4).
 * Supported forms: {@code id: 1.2.3}, a nested block with or without a {@code version:} line, and a one-line flow
 * map {@code id: { ..., version: 1.2.3 }}. Returns null when the file has no block-style top-level
 * {@code dependencies} mapping (TS: no write when the node is not a YAMLMap).
 */
final class ConfigDependencyEditor {
  private ConfigDependencyEditor() {}

  private static final Pattern DEPS_LINE = Pattern.compile("^dependencies\\s*:\\s*(#.*)?$");
  private static final Pattern KEY_LINE = Pattern.compile(
      "^(\\s+)(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^']|'')*'|[^\\s#'\"][^:#]*?)\\s*:(\\s*)(.*?)(\\s+#.*)?$");

  static String updateDependencyVersions(String text, List<? extends Map<String, Object>> dependencies) {
    List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
    int start = -1;
    for (int i = 0; i < lines.size(); i++) {
      if (DEPS_LINE.matcher(stripCr(lines.get(i))).find()) {
        start = i;
        break;
      }
    }
    if (start < 0) return null;
    // block lines: indented (or blank/comment) until the next top-level line
    int end = start + 1;
    while (end < lines.size()) {
      String l = stripCr(lines.get(end));
      if (!l.isBlank() && !Character.isWhitespace(l.charAt(0)) && !l.startsWith("#")) break;
      end++;
    }
    int entryIndent = -1;
    for (int i = start + 1; i < end; i++) {
      String raw = lines.get(i);
      String l = stripCr(raw);
      String cr = raw.substring(l.length());
      if (l.isBlank() || l.trim().startsWith("#")) continue;
      Matcher m = KEY_LINE.matcher(l);
      if (!m.find()) continue;
      int indent = m.group(1).length();
      if (entryIndent < 0) entryIndent = indent;
      if (indent != entryIndent) continue;
      String key = unquote(m.group(2));
      Map<String, Object> dep = find(dependencies, key);
      if (dep == null) continue;
      String version = Js.toStr(dep.get("version"));
      String value = m.group(4);
      String comment = m.group(5) == null ? "" : m.group(5);
      if (!value.isEmpty() && !value.startsWith("{")) {
        lines.set(i, l.substring(0, m.start(4)) + version + comment + cr);
      } else if (value.startsWith("{")) {
        Matcher vm = Pattern.compile("(version\\s*:\\s*)([^,}]*?)(\\s*[,}])").matcher(value);
        String nv = vm.find() ? value.substring(0, vm.start(2)) + version + value.substring(vm.end(2))
            : value.replaceFirst("\\s*}\\s*$", ", version: " + version + " }");
        lines.set(i, l.substring(0, m.start(4)) + nv + comment + cr);
      } else {
        // nested block: find its version line, else add one
        int j = i + 1;
        int childIndent = -1;
        boolean done = false;
        for (; j < end; j++) {
          String cl = stripCr(lines.get(j));
          if (cl.isBlank() || cl.trim().startsWith("#")) continue;
          Matcher cm = KEY_LINE.matcher(cl);
          int ci = cl.length() - cl.stripLeading().length();
          if (ci <= entryIndent) break;
          if (childIndent < 0) childIndent = ci;
          if (cm.find() && ci == childIndent && unquote(cm.group(2)).equals("version")) {
            String ccr = lines.get(j).substring(cl.length());
            String cc = cm.group(5) == null ? "" : cm.group(5);
            lines.set(j, cl.substring(0, cm.start(4)) + version + cc + ccr);
            done = true;
            break;
          }
        }
        if (!done) {
          int ci = childIndent < 0 ? entryIndent + 2 : childIndent;
          lines.add(j, " ".repeat(ci) + "version: " + version + cr);
          end++;
        }
      }
    }
    return String.join("\n", lines);
  }

  private static String stripCr(String l) {
    return l.endsWith("\r") ? l.substring(0, l.length() - 1) : l;
  }

  private static String unquote(String k) {
    if (k.length() >= 2 && k.startsWith("\"") && k.endsWith("\"")) return k.substring(1, k.length() - 1);
    if (k.length() >= 2 && k.startsWith("'") && k.endsWith("'")) {
      return k.substring(1, k.length() - 1).replace("''", "'");
    }
    return k.trim();
  }

  private static Map<String, Object> find(List<? extends Map<String, Object>> deps, String key) {
    for (Map<String, Object> d : deps) {
      if (key.equals(d.get("packageId"))) return d;
    }
    return null;
  }
}

package org.hl7.fhir.jekyll.site;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ruby CSV.read(path, headers: true) + row.to_hash, as Jekyll's DataReader uses for .csv/.tsv. */
final class Csv {
  private Csv() {}

  static List<Object> readWithHeaders(String text, char sep) {
    List<List<String>> rows = parse(text, sep);
    List<Object> out = new ArrayList<>();
    if (rows.isEmpty()) {
      return out;
    }
    List<String> header = rows.get(0);
    for (int r = 1; r < rows.size(); r++) {
      Map<String, Object> m = new LinkedHashMap<>();
      List<String> row = rows.get(r);
      for (int c = 0; c < header.size(); c++) {
        String key = header.get(c);
        if (!m.containsKey(key)) { // CSV::Row#to_hash keeps the first of duplicate headers
          m.put(key, c < row.size() ? row.get(c) : null);
        }
      }
      out.add(m);
    }
    return out;
  }

  private static List<List<String>> parse(String text, char sep) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder f = new StringBuilder();
    boolean quoted = false, fieldStarted = false, wasQuoted = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            f.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          f.append(c);
        }
      } else if (c == '"' && !fieldStarted) {
        quoted = true;
        fieldStarted = true;
        wasQuoted = true;
      } else if (c == sep) {
        row.add(fieldStarted || wasQuoted ? f.toString() : null);
        f.setLength(0);
        fieldStarted = wasQuoted = false;
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
          i++;
        }
        row.add(fieldStarted || wasQuoted ? f.toString() : null);
        rows.add(row);
        row = new ArrayList<>();
        f.setLength(0);
        fieldStarted = wasQuoted = false;
      } else {
        f.append(c);
        fieldStarted = true;
      }
    }
    if (fieldStarted || wasQuoted || !row.isEmpty()) {
      row.add(fieldStarted || wasQuoted ? f.toString() : null);
      rows.add(row);
    }
    return rows;
  }
}

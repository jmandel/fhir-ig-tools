package org.hl7.fhir.sushi.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// port of text-table 0.2.0 (node_modules/text-table/index.js), as used for fsh-generated/fsh-index.txt
/** Column-aligned plain-text table. Default options only use left alignment; 'r', 'c', '.' are ported too. */
public final class TextTable {
  private TextTable() {}

  private static final Pattern DOT = Pattern.compile("\\.[^.]*\\z");
  // JS /\s+$/ (no m flag): trailing JS whitespace at end of input
  private static final Pattern TRAILING_WS = Pattern.compile(org.hl7.fhir.sushi.fshtypes.Common.JS_WS + "+\\z");

  /** text-table with defaults: hsep two spaces, all columns left-aligned. */
  public static String table(List<? extends List<?>> rows) {
    return table(rows, "  ", List.of());
  }

  /** Cells are converted with JS String(); align entries "l" (default), "r", "c", "." per column. */
  public static String table(List<? extends List<?>> rowsIn, String hsep, List<String> align) {
    List<Integer> dotsizes = new ArrayList<>();
    for (List<?> row : rowsIn) {
      for (int ix = 0; ix < row.size(); ix++) {
        int n = dotindex(org.hl7.fhir.sushi.js.Js.toStr(row.get(ix)));
        grow(dotsizes, ix, n);
      }
    }
    List<List<String>> rows = new ArrayList<>();
    for (List<?> row : rowsIn) {
      List<String> r = new ArrayList<>();
      for (int ix = 0; ix < row.size(); ix++) {
        String c = org.hl7.fhir.sushi.js.Js.toStr(row.get(ix));
        if (".".equals(at(align, ix))) {
          int index = dotindex(c);
          int size = dotsizes.get(ix) + (c.contains(".") ? 1 : 2) - (c.length() - index);
          c = c + " ".repeat(Math.max(0, size - 1)); // JS Array(size).join(' ')
        }
        r.add(c);
      }
      rows.add(r);
    }
    List<Integer> sizes = new ArrayList<>();
    for (List<String> row : rows) for (int ix = 0; ix < row.size(); ix++) grow(sizes, ix, row.get(ix).length());
    StringBuilder out = new StringBuilder();
    for (int ri = 0; ri < rows.size(); ri++) {
      List<String> row = rows.get(ri);
      StringBuilder line = new StringBuilder();
      for (int ix = 0; ix < row.size(); ix++) {
        String c = row.get(ix);
        int n = sizes.get(ix) - c.length();
        String s = " ".repeat(Math.max(n, 0));
        String a = at(align, ix);
        String cell;
        if ("r".equals(a) || ".".equals(a)) cell = s + c;
        else if ("c".equals(a)) cell = " ".repeat((int) Math.ceil(n / 2.0)) + c + " ".repeat((int) Math.floor(n / 2.0));
        else cell = c + s;
        if (ix > 0) line.append(hsep);
        line.append(cell);
      }
      if (ri > 0) out.append('\n');
      out.append(TRAILING_WS.matcher(line).replaceFirst(""));
    }
    return out.toString();
  }

  private static String at(List<String> align, int ix) {
    return ix < align.size() ? align.get(ix) : null;
  }

  private static void grow(List<Integer> acc, int ix, int n) {
    while (acc.size() <= ix) acc.add(null);
    if (acc.get(ix) == null || n > acc.get(ix)) acc.set(ix, n);
  }

  private static int dotindex(String c) {
    Matcher m = DOT.matcher(c);
    return m.find() ? m.start() + 1 : c.length();
  }
}

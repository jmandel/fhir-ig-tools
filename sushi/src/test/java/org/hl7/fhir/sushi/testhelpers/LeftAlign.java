package org.hl7.fhir.sushi.testhelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// port of sushi/test/utils/leftAlign.ts (SUSHI 3.21.1)
public final class LeftAlign {
  private LeftAlign() {}

  private static final Pattern LETTER = Pattern.compile("[a-z]", Pattern.CASE_INSENSITIVE);
  private static final Pattern NON_WS = Pattern.compile("\\S");

  /**
   * Removes the indentation of the first line containing a letter from every line that is long enough. Port tip:
   * TS {@code leftAlign(`\n    Profile: X\n    `)} is {@code leftAlign("""\n\n    Profile: X\n    """)}-shaped;
   * a Java text block starting with an empty line gives the same leading "\n" (and so the same line numbers).
   */
  public static String leftAlign(String inputFSH) {
    String[] lineArray = inputFSH.split("\\r?\\n", -1);
    Integer offsetAmount = null;
    for (String line : lineArray) {
      if (LETTER.matcher(line).find()) {
        Matcher m = NON_WS.matcher(line);
        offsetAmount = m.find() ? m.start() : -1;
        break;
      }
    }
    List<String> out = new ArrayList<>();
    for (String l : lineArray) {
      // JS: l.length >= undefined is false
      out.add(offsetAmount != null && l.length() >= offsetAmount ? l.substring(Math.max(0, offsetAmount)) : l);
    }
    return String.join("\n", out);
  }
}

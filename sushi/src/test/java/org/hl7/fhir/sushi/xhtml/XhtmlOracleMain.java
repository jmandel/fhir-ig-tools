package org.hl7.fhir.sushi.xhtml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hl7.fhir.common.js.Json;

/**
 * Java half of the WP-9 differential (scripts/xhtml/oracle.js is the JS half): reads JSON-string lines on stdin,
 * writes {@code [minified, minifyError, saxError, saxThrow]} lines in exactly the oracle's format.
 */
public final class XhtmlOracleMain {
  private XhtmlOracleMain() {}

  static String run(String v) {
    String m = null, me = null, s = null, st = null;
    try {
      m = HtmlMinifier.minify(v, HtmlMinifier.Options.SUSHI);
    } catch (HtmlMinifier.MinifyException e) {
      me = e.getMessage();
    } catch (RuntimeException | StackOverflowError e) {
      me = "JAVA " + e;
    }
    try {
      s = Sax.parser(true).write(v).error();
    } catch (SaxParser.SaxException e) {
      st = e.getMessage();
    } catch (RuntimeException | StackOverflowError e) {
      st = "JAVA " + e;
    }
    List<Object> row = new ArrayList<>(Arrays.asList(m, me, s, st));
    return Json.stringify(row);
  }

  public static void main(String[] args) throws Exception {
    try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
      String line;
      while ((line = in.readLine()) != null) {
        if (line.isEmpty()) continue;
        out.write(run((String) Json.parse(line)));
        out.write('\n');
      }
    }
  }
}

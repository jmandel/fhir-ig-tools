package org.hl7.fhir.sushi.xhtml;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.js.Json;
import org.junit.jupiter.api.Test;

/**
 * Oracle tests for the html-minifier-terser 5.1.1 / sax 1.6.1 ports. vectors.json is a deterministic sample of the
 * WP-9 differential (scripts/xhtml: corpus xhtml values + fuzz cases) with the JS results: [input, minified,
 * minifyError, saxError, saxThrow]. The full differential (corpus + 200k fuzz cases) runs via scripts/xhtml/diff.sh.
 */
class XhtmlTest {
  @SuppressWarnings("unchecked")
  @Test void matchesJsOracleVectors() throws IOException {
    List<Object> vectors;
    try (InputStream in = XhtmlTest.class.getResourceAsStream("/xhtml/vectors.json")) {
      vectors = (List<Object>) Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
    List<String> failures = new ArrayList<>();
    for (Object o : vectors) {
      List<Object> v = (List<Object>) o;
      String input = (String) v.get(0);
      String expected = Json.stringify(v.subList(1, 5));
      String actual = XhtmlOracleMain.run(input);
      if (!expected.equals(actual)) failures.add(Json.stringify(input) + "\n  js:   " + expected + "\n  java: " + actual);
    }
    assertTrue(vectors.size() > 1000);
    assertEquals(List.of(), failures.subList(0, Math.min(5, failures.size())), failures.size() + " failures");
  }

  private static String min(String s) {
    return HtmlMinifier.minify(s, HtmlMinifier.Options.SUSHI);
  }

  @Test void minifiesLikeSushi() {
    assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>Hello <b>World</b> !</p></div>",
        min("  <div xmlns=\"http://www.w3.org/1999/xhtml\">\n  <p>Hello   <b>World</b>\n !</p>\n</div>\n"));
    // keepClosingSlash; void elements without a slash stay without one
    assertEquals("<div>a<br/>b<br>c</div>", min("<div>a<br/>b<br>c</div>"));
    // html5=false: open inline elements are closed before a block element
    assertEquals("<div><span>a</span><p>b</p></div>", min("<div><span>a<p>b</p></span></div>"));
    // quote selection
    assertEquals("<a title='say \"hi\"'>x</a>", min("<a title='say \"hi\"'>x</a>"));
  }

  @Test void throwsParseErrorsLikeJs() {
    HtmlMinifier.MinifyException e = assertThrows(HtmlMinifier.MinifyException.class, () -> min("<div>a < b</div>"));
    assertEquals("Parse Error: < b</div>", e.getMessage());
  }

  @Test void saxErrorsLikeJs() {
    assertNull(Sax.parser(true).write("<div xmlns=\"http://www.w3.org/1999/xhtml\">x&nbsp;y</div>").error());
    // no close(): unclosed elements are not an error
    assertNull(Sax.parser(true).write("<div><p>").error());
    assertEquals("Unexpected close tag\nLine: 0\nColumn: 14\nChar: >", Sax.parser(true).write("<div><b></div>").error());
    assertEquals("Unquoted attribute value\nLine: 0\nColumn: 8\nChar: 1", Sax.parser(true).write("<div a=1></div>").error());
    assertThrows(SaxParser.SaxException.class,
        () -> Sax.parser(true).write("<div hasOwnProperty=\"1\" a=\"2\"></div>"));
  }
}

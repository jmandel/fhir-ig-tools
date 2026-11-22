package org.hl7.fhir.sushi.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.Json;
import org.junit.jupiter.api.Test;

/** Node oracle (scripts/oracle/util-vectors.js over SUSHI's node_modules, 5,000 real corpus page names + edge cases). */
@SuppressWarnings("unchecked")
class UtilLeavesOracleTest {
  static Map<String, Object> v;

  static synchronized Map<String, Object> vectors() throws IOException {
    if (v == null) {
      try (InputStream in = UtilLeavesOracleTest.class.getResourceAsStream("/js/util-vectors.json")) {
        v = (Map<String, Object>) Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return v;
  }

  static List<List<Object>> rows(String k) throws IOException {
    return (List<List<Object>>) vectors().get(k);
  }

  @Test void lodashWordsAndTitleCase() throws IOException {
    List<String> bad = new ArrayList<>();
    for (List<Object> r : rows("words")) {
      String s = (String) r.get(0);
      if (!r.get(1).equals(Js.words(s))) bad.add("words(" + s + ") " + r.get(1) + " vs " + Js.words(s));
      String t = TitleCase.titleCase(String.join(" ", Js.words(s.replaceFirst("\\.[^.]+\\z", ""))));
      if (!r.get(2).equals(t)) bad.add("title(words(" + s + ")) " + r.get(2) + " vs " + t);
      if (!r.get(3).equals(TitleCase.titleCase(s))) bad.add("title(" + s + ") " + r.get(3) + " vs " + TitleCase.titleCase(s));
    }
    assertEquals(List.of(), bad.subList(0, Math.min(20, bad.size())), bad.size() + " mismatches");
  }

  @Test void sanitizeFilename() throws IOException {
    for (List<Object> r : rows("san")) {
      String s = (String) r.get(0);
      assertEquals(r.get(1), SanitizeFilename.sanitize(s), s);
      assertEquals(r.get(2), SanitizeFilename.sanitize(s, "-"), s);
    }
  }

  @Test void lodashPadding() throws IOException {
    for (List<Object> r : rows("pads")) {
      String s = (String) r.get(0);
      assertEquals(r.get(1), Js.pad(s, 13));
      assertEquals(r.get(2), Js.padStart(s, 12));
      assertEquals(r.get(3), Js.padEnd(s, 36));
      assertEquals(r.get(4), Js.upperFirst(s));
      assertEquals(r.get(5), Js.escapeRegExp(s + ".*+?^$(){}[]|\\"));
    }
  }

  @Test void validUrlIsUri() throws IOException {
    for (List<Object> r : rows("uris")) assertEquals(r.get(1), ValidUrl.isUri((String) r.get(0)), (String) r.get(0));
  }

  @Test void textTable() throws IOException {
    for (List<Object> r : rows("tbl")) assertEquals(r.get(1), TextTable.table((List<List<Object>>) r.get(0)));
  }

  @Test void puns() {
    assertEquals(35 + 22 + 34, Puns.getPuns().size());
    assertTrue(Puns.getPuns(true, false, false).contains(Puns.getRandomPun(1, 0)));
    assertTrue(Puns.getPuns(false, true, false).contains(Puns.getRandomPun(0, 3)));
  }

  @Test void lodashIsMatchAndMerge() {
    Object o = Json.parse("{\"a\":1,\"b\":[{\"x\":1,\"y\":2},{\"x\":3}],\"c\":{\"d\":4,\"e\":5}}");
    assertTrue(Js.isMatch(o, Json.parse("{\"b\":[{\"x\":3}],\"c\":{\"d\":4}}")));
    assertFalse(Js.isMatch(o, Json.parse("{\"b\":[{\"x\":4}]}")));
    Object d = Json.parse("{\"a\":[1,{\"k\":1}],\"b\":{\"c\":1}}");
    Js.merge(d, Json.parse("{\"a\":[2,{\"j\":2}],\"b\":{\"d\":2},\"e\":3}"));
    assertEquals("{\"a\":[2,{\"k\":1,\"j\":2}],\"b\":{\"c\":1,\"d\":2},\"e\":3}", Json.stringify(d));
  }
}

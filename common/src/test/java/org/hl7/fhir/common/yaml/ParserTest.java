package org.hl7.fhir.common.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks of the public event-layer API from outside its implementation. Byte-for-byte equivalence with
 * libyaml is covered by the jekyll module's differential YAML suites (yaml-test-suite, typing, real IG files).
 */
class ParserTest {
  private static List<Event> events(String yaml) {
    return events(yaml, false);
  }

  private static List<Event> events(String yaml, boolean yaml110) {
    Parser p = new Parser(yaml.getBytes(StandardCharsets.UTF_8), Parser.UTF8_ENCODING, yaml110);
    List<Event> out = new ArrayList<>();
    for (Event e; (e = p.parse()) != null; ) {
      out.add(e);
    }
    return out;
  }

  private static String str(byte[] b) {
    return b == null ? null : new String(b, StandardCharsets.UTF_8);
  }

  @Test
  void eventStream() {
    List<Event> ev = events("a: [1, 'x']\n");
    List<Event.Type> types = new ArrayList<>();
    ev.forEach(e -> types.add(e.type));
    assertEquals(List.of(Event.Type.STREAM_START, Event.Type.DOCUMENT_START, Event.Type.MAPPING_START, Event.Type.SCALAR,
        Event.Type.SEQUENCE_START, Event.Type.SCALAR, Event.Type.SCALAR, Event.Type.SEQUENCE_END, Event.Type.MAPPING_END,
        Event.Type.DOCUMENT_END, Event.Type.STREAM_END), types);
    assertTrue(ev.get(1).implicit);
    assertEquals(Event.BLOCK_STYLE, ev.get(2).style);
    assertEquals("a", str(ev.get(3).value));
    assertEquals(Event.PLAIN_SCALAR_STYLE, ev.get(3).style);
    assertTrue(ev.get(3).plainImplicit);
    assertEquals(Event.FLOW_STYLE, ev.get(4).style);
    assertEquals("x", str(ev.get(6).value));
    assertEquals(Event.SINGLE_QUOTED_SCALAR_STYLE, ev.get(6).style);
    assertTrue(ev.get(6).quotedImplicit);
    assertEquals(0, ev.get(4).startMark.line);
    assertEquals(3, ev.get(4).startMark.column);
  }

  @Test
  void anchorsAliasesAndTags() {
    List<Event> ev = events("- &x !!str 5\n- *x\n");
    Event scalar = ev.get(3);
    assertEquals(Event.Type.SCALAR, scalar.type);
    assertEquals("x", str(scalar.anchor));
    assertEquals("tag:yaml.org,2002:str", str(scalar.tag));
    assertEquals("5", str(scalar.value));
    Event alias = ev.get(4);
    assertEquals(Event.Type.ALIAS, alias.type);
    assertEquals("x", str(alias.anchor));
  }

  @Test
  void bomDetection() {
    byte[] utf16le = {(byte) 0xFF, (byte) 0xFE, 'k', 0, ':', 0, ' ', 0, 'v', 0, '\n', 0};
    Parser p = new Parser(utf16le, Parser.ANY_ENCODING);
    Event stream = p.parse();
    assertEquals(Event.Type.STREAM_START, stream.type);
    assertEquals(Parser.UTF16LE_ENCODING, stream.encoding);
    p.parse(); // DOCUMENT_START
    p.parse(); // MAPPING_START
    assertEquals("k", str(p.parse().value));
    assertEquals("v", str(p.parse().value));
  }

  @Test
  void errorsCarryLibyamlState() {
    LibyamlError e = assertThrows(LibyamlError.class, () -> events("a: [1\n"));
    assertEquals(LibyamlError.PARSER_ERROR, e.error);
    assertEquals("did not find expected ',' or ']'", e.problem);
    assertEquals("while parsing a flow sequence", e.context);
    assertEquals(0, e.contextMark.line);
    assertEquals(3, e.contextMark.column);
  }

  @Test
  void endOfStream() {
    Parser p = new Parser(new byte[0], Parser.UTF8_ENCODING);
    assertEquals(Event.Type.STREAM_START, p.parse().type);
    assertEquals(Event.Type.STREAM_END, p.parse().type);
    assertNull(p.parse());
  }

  @Test
  void yaml110IsOptIn() {
    // libyaml 0.2.5 (the default) rejects each of these; the opt-in yaml110 mode accepts them as yaml@1.10 does
    assertThrows(LibyamlError.class, () -> events("a: 1\n: x\n"));
    assertEquals("x", str(events("a: 1\n: x\n", true).get(6).value));
    assertThrows(LibyamlError.class, () -> events("a: b\u0001c\n"));
    assertEquals("b\u0001c", str(events("a: b\u0001c\n", true).get(4).value));
    assertThrows(LibyamlError.class, () -> events("a: %y\n"));
    assertEquals("%y", str(events("a: %y\n", true).get(4).value));
    // NEL is a line break only in libyaml mode
    assertEquals("x\u0085y", str(events("a: x\u0085y\n", true).get(4).value));
  }
}

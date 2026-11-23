package org.hl7.fhir.sushi.fhirdefs;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.common.js.JsObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Predefined FHIR XML read with core's parsers (docs/core-divergences.md C6). */
class FhirXmlTest {
  static final String OBS = """
      <Observation xmlns="http://hl7.org/fhir">
        <!-- a comment -->
        <id value="o1"/>
        <status value="final"/>
        <code><text value="weight"/></code>
        <valueQuantity><value value="70.50"/><unit value="kg"/></valueQuantity>
        <note><text value="two
      lines"/></note>
      </Observation>""";

  @Test
  void modelPerFhirVersion() {
    assertEquals("R4", FhirXml.model("4.0.1"));
    assertEquals("R4B", FhirXml.model("4.3.0"));
    assertEquals("R5", FhirXml.model("5.0.0"));
    assertEquals("R5", FhirXml.model("6.0.0-snapshot1"));
    assertEquals("R4", FhirXml.model(null));
  }

  @Test
  @SuppressWarnings("unchecked")
  void fhirJsonAsJsValues() throws Exception {
    Map<String, Object> o = (Map<String, Object>) FhirXml.xmlToObj(OBS, "4.0.1");
    assertEquals("Observation", o.get("resourceType"));
    assertNull(o.get("fhir_comments")); // comments are not FHIR JSON
    // a decimal is a JSON number (fhir-tool kept the text "70.50" as a string)
    assertEquals(70.5, ((Map<String, Object>) o.get("valueQuantity")).get("value"));
    // XML attribute-value normalisation: a literal newline in an attribute is a space (XML 1.0 3.3.3)
    Map<String, Object> note = (Map<String, Object>) ((List<Object>) o.get("note")).get(0);
    assertEquals("two lines", note.get("text"));
  }

  @Test
  void typesOfAnotherFhirVersionAreRejected() throws Exception {
    String actor = "<ActorDefinition xmlns=\"http://hl7.org/fhir\"><status value=\"draft\"/><type value=\"person\"/></ActorDefinition>";
    assertEquals("ActorDefinition", ((Map<?, ?>) FhirXml.xmlToObj(actor, "5.0.0")).get("resourceType"));
    Exception e = assertThrows(Exception.class, () -> FhirXml.xmlToObj(actor, "4.0.1"));
    assertTrue(e.getMessage().contains("Unknown resource type"), e.getMessage());
  }

  @Test
  void unreadableXmlIsSkippedWithAWarning(@TempDir Path dir) throws Exception {
    Files.writeString(dir.resolve("good.xml"), OBS);
    Files.writeString(dir.resolve("cda.xml"), "<ClinicalDocument xmlns=\"urn:hl7-org:v3\"/>");
    List<String> logs = new ArrayList<>();
    List<String> registered = new ArrayList<>();
    DiskBasedVirtualPackage pkg = new DiskBasedVirtualPackage(new JsObject().with("name", "t").with("version", "1"),
        List.of(dir.toString()), (level, msg) -> logs.add(level + " " + msg), true, true, "4.0.1");
    pkg.registerResources((key, resource, allowNonResources) -> registered.add(Path.of(key).getFileName().toString()));
    assertEquals(List.of("good.xml"), registered);
    assertTrue(logs.stream().anyMatch(l -> l.startsWith("warn Skipped predefined XML file ") && l.contains("cda.xml: the FHIR R4 XML parser could not read it (")), logs.toString());
    assertTrue(logs.stream().noneMatch(l -> l.startsWith("error")), logs.toString());
  }
}

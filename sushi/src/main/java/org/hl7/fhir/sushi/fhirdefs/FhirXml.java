package org.hl7.fhir.sushi.fhirdefs;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.utils.FHIRVersionUtils;

/**
 * Predefined FHIR XML resources to the JS value model, with org.hl7.fhir.core's XML parser and JSON composer for the
 * IG's FHIR version: R4 = {@code org.hl7.fhir.r4.formats}, R4B = {@code org.hl7.fhir.r4b.formats}, R5 and later =
 * {@code org.hl7.fhir.r5.formats} (core has no R6 model; R6 IGs are read as R5). Not a port: TypeScript SUSHI reads
 * XML through fhir-package-loader with fhir-tool's {@code xmlToObj} and R4 type data for every version; this differs
 * on some inputs, see docs/core-divergences.md C6.
 */
public final class FhirXml {
  private FhirXml() {}

  /** The FHIR release whose model reads the XML: R4, R4B or R5. */
  public static String model(String fhirVersion) {
    String name = fhirVersion == null ? "R4" : FHIRVersionUtils.getFHIRVersionInfo(fhirVersion).name();
    return switch (name) {
      case "R4B" -> "R4B";
      case "R5", "R6" -> "R5";
      default -> "R4";
    };
  }

  /** Parses FHIR XML as a resource of {@code fhirVersion} (null: R4) and returns its FHIR JSON as JS values. */
  public static Object xmlToObj(String xml, String fhirVersion) throws Exception {
    InputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    String json = switch (model(fhirVersion)) {
      case "R4B" -> new org.hl7.fhir.r4b.formats.JsonParser().composeString(
          new org.hl7.fhir.r4b.formats.XmlParser().parse(in));
      case "R5" -> new org.hl7.fhir.r5.formats.JsonParser().composeString(
          new org.hl7.fhir.r5.formats.XmlParser().parse(in));
      default -> new org.hl7.fhir.r4.formats.JsonParser().composeString(
          new org.hl7.fhir.r4.formats.XmlParser().parse(in));
    };
    return Json.parse(json);
  }
}

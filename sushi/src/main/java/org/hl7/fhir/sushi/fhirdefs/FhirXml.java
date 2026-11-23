package org.hl7.fhir.sushi.fhirdefs;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.utils.FHIRVersionUtils;

/**
 * Predefined FHIR XML resources to the JS value model, with org.hl7.fhir.core's XML parser and JSON composer for the
 * IG's FHIR version: R4 = {@code org.hl7.fhir.r4.formats}, R4B = {@code org.hl7.fhir.r4b.formats}, R5 and later =
 * {@code org.hl7.fhir.r5.formats} (core has no R6 model; R6 IGs are read as R5). Not a port: TypeScript SUSHI reads
 * XML through fhir-package-loader with fhir-tool's {@code xmlToObj} and R4 type data for every version; this differs
 * on some inputs, see docs/core-divergences.md C6.
 *
 * <p>The strict parse is tried first. If it fails, the parse is repeated with core's {@code allowUnknownContent}
 * (elements the model does not have at that place are skipped); when that succeeds the resource is used and
 * {@code partial} is told what the strict parse rejected, since content was dropped.
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

  /** {@link #xmlToObj(String, String, Consumer)} that ignores partial reads. */
  public static Object xmlToObj(String xml, String fhirVersion) throws Exception {
    return xmlToObj(xml, fhirVersion, m -> {});
  }

  /**
   * Parses FHIR XML as a resource of {@code fhirVersion} (null: R4) and returns its FHIR JSON as JS values. Throws
   * the strict parse's exception when even the lenient parse fails.
   */
  public static Object xmlToObj(String xml, String fhirVersion, Consumer<String> partial) throws Exception {
    byte[] b = xml.getBytes(StandardCharsets.UTF_8);
    String model = model(fhirVersion);
    try {
      return Json.parse(toJson(b, model, false));
    } catch (Exception strict) {
      String json;
      try {
        json = toJson(b, model, true);
      } catch (Exception lenient) {
        throw strict;
      }
      partial.accept(strict.getMessage());
      return Json.parse(json);
    }
  }

  private static String toJson(byte[] xml, String model, boolean lenient) throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(xml);
    return switch (model) {
      case "R4B" -> {
        org.hl7.fhir.r4b.formats.XmlParser p = new org.hl7.fhir.r4b.formats.XmlParser();
        p.setAllowUnknownContent(lenient);
        yield new org.hl7.fhir.r4b.formats.JsonParser().composeString(p.parse(in));
      }
      case "R5" -> {
        org.hl7.fhir.r5.formats.XmlParser p = new org.hl7.fhir.r5.formats.XmlParser();
        p.setAllowUnknownContent(lenient);
        yield new org.hl7.fhir.r5.formats.JsonParser().composeString(p.parse(in));
      }
      default -> {
        org.hl7.fhir.r4.formats.XmlParser p = new org.hl7.fhir.r4.formats.XmlParser();
        p.setAllowUnknownContent(lenient);
        yield new org.hl7.fhir.r4.formats.JsonParser().composeString(p.parse(in));
      }
    };
  }
}

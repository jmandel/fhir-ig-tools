// Harness (not part of sushi.jar): FHIR XML -> JSON with org.hl7.fhir.core's parsers, next to the port's
// fhir-tool xmlToObj (fhirdefs.xml.FhirXmlToJs). Run with java's source launcher; class path = sushi.jar + the
// r4/r5 model jars (scripts/corexml/pom.xml). See scripts/corexml/run.sh.
//
//   convert <inputs.tsv> <out.ndjson> [r6-core-package-dir]
//       inputs.tsv: id \t xml path \t baseline (r4 | r5 | r6). Writes {id, baseline, port, core} per line, where
//       port/core are the JSON text or "ERR: <message>".
//   sushi <baseline> <r6-core-package-dir|-> <sushi args...>
//       runs SUSHI (App.main) with core's conversion swapped in for predefined XML resources
//       (DiskBasedVirtualPackage.setXmlConverter); the default converter is not changed anywhere else.
//
// Baselines: r4 = org.hl7.fhir.r4.formats.XmlParser -> JsonParser (FHIR 4.0.1); r5 = the same in org.hl7.fhir.r5
// (5.0.0); r6 = r5's element model (elementmodel.XmlParser -> JsonParser) over a SimpleWorkerContext loaded from
// hl7.fhir.r6.core#6.0.0-snapshot1 (core 7.0.6 has no R6 class model).
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.elementmodel.Element;
import org.hl7.fhir.r5.elementmodel.ParserBase.ValidationPolicy;
import org.hl7.fhir.r5.elementmodel.ValidatedFragment;
import org.hl7.fhir.r5.formats.IParser.OutputStyle;
import org.hl7.fhir.sushi.fhirdefs.DiskBasedVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.xml.FhirXmlToJs;
import org.hl7.fhir.utilities.npm.NpmPackage;

public class CoreXml {
  interface Conv {
    String json(String xml) throws Exception;
  }

  static SimpleWorkerContext r6;
  /** non-fatal messages of the last element-model parse (r6), "" when none */
  static String lastErrors = "";

  static Conv conv(String baseline, String r6dir) throws Exception {
    switch (baseline) {
      case "r4":
        return xml -> new org.hl7.fhir.r4.formats.JsonParser().composeString(
            new org.hl7.fhir.r4.formats.XmlParser().parse(bytes(xml)));
      case "r5":
        return xml -> new org.hl7.fhir.r5.formats.JsonParser().composeString(
            new org.hl7.fhir.r5.formats.XmlParser().parse(bytes(xml)));
      case "r6": {
        if (r6 == null) {
          r6 = new SimpleWorkerContext.SimpleWorkerContextBuilder().withAllowLoadingDuplicates(true)
              .fromPackage(NpmPackage.fromFolder(r6dir));
        }
        return xml -> {
          org.hl7.fhir.r5.elementmodel.XmlParser p = new org.hl7.fhir.r5.elementmodel.XmlParser(r6);
          p.setupValidation(ValidationPolicy.NONE);
          List<ValidatedFragment> fs = p.parse(bytes(xml));
          Element e = fs.isEmpty() ? null : fs.get(0).getElement();
          lastErrors = fs.isEmpty() ? "" : fs.get(0).getErrors().stream()
              .filter(m -> m.getLevel() != org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity.INFORMATION)
              .map(m -> m.getLocation() + ": " + m.getMessage()).collect(java.util.stream.Collectors.joining(" | "));
          if (e == null) {
            throw new Exception(fs.isEmpty() ? "no content" : String.valueOf(fs.get(0).getErrors()));
          }
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          new org.hl7.fhir.r5.elementmodel.JsonParser(r6).compose(e, out, OutputStyle.NORMAL, null);
          return out.toString(StandardCharsets.UTF_8);
        };
      }
      default:
        throw new IllegalArgumentException("baseline " + baseline);
    }
  }

  static ByteArrayInputStream bytes(String s) {
    return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
  }

  static String err(Throwable t) {
    String m = t.getMessage();
    return "ERR: " + t.getClass().getSimpleName() + ": " + (m == null ? "" : m.replace('\n', ' '));
  }

  public static void main(String[] args) throws Exception {
    // as App.main: keep slf4j's "no providers" notice off stderr (the r6 context initialises slf4j before App.main)
    System.setProperty("slf4j.internal.verbosity", "ERROR");
    if (args[0].equals("convert")) {
      String r6dir = args.length > 3 ? args[3] : null;
      // fhir-tool's console.log("do something") and core's chatter must not reach the ndjson
      PrintStream realOut = System.out;
      System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (String line : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
        if (line.isBlank()) continue;
        String[] f = line.split("\t");
        String xml = new String(Files.readAllBytes(Path.of(f[1])), StandardCharsets.UTF_8);
        String port, core;
        try {
          port = Json.stringify(FhirXmlToJs.xmlToObj(xml));
        } catch (Throwable t) {
          port = err(t);
        }
        lastErrors = "";
        try {
          core = conv(f[2], r6dir).json(xml);
        } catch (Throwable t) {
          core = err(t);
        }
        sb.append(Json.stringify(new JsObject().with("id", f[0]).with("baseline", f[2]).with("port", port)
            .with("core", core).with("coreErrors", lastErrors))).append('\n');
      }
      Files.writeString(Path.of(args[2]), sb.toString(), StandardCharsets.UTF_8);
      realOut.println("wrote " + args[2]);
    } else if (args[0].equals("sushi")) {
      Conv c = conv(args[1], args[2]);
      DiskBasedVirtualPackage.setXmlConverter(xml -> {
        try {
          return Json.parse(c.json(xml));
        } catch (Exception e) {
          String m = String.valueOf(e.getMessage());
          // keep SUSHI's skip of non-FHIR (logical-model) instances: DiskBasedVirtualPackage looks for this text
          if (m.contains("Unknown resource type") || m.contains("unknown or unrecognized XML Root element")
              || m.contains("wrong namespace")) {
            throw new Exception("Unknown resource type: " + m);
          }
          throw e;
        }
      });
      org.hl7.fhir.sushi.App.main(java.util.Arrays.copyOfRange(args, 3, args.length));
    } else {
      throw new IllegalArgumentException(args[0]);
    }
  }
}

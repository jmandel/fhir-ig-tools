package org.hl7.fhir.sushi.fhirdefs.xml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.hl7.fhir.sushi.js.JsObject;
import org.hl7.fhir.sushi.js.Json;

/**
 * Java half of the fhir-tool xmlToObj differential (scripts/fhirtool/oracle.js is the JS half): reads {id, xml}
 * NDJSON, writes {id, out} with out = Json.stringify(xmlToObj(xml)) or "ERR:" + message, prefixed by "STDOUT:" lines
 * for anything printed (fhir-tool's console.log). usage: FhirXmlOracleMain &lt;inputs.ndjson&gt; &lt;out.ndjson&gt;
 */
public final class FhirXmlOracleMain {
  private FhirXmlOracleMain() {}

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws Exception {
    PrintStream realOut = System.out;
    int n = 0;
    try (BufferedReader r = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8);
        BufferedWriter w = Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.UTF_8)) {
      String line;
      while ((line = r.readLine()) != null) {
        if (line.isEmpty()) continue;
        Map<String, Object> in = (Map<String, Object>) Json.parse(line);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        String res;
        try {
          Object o = FhirXmlToJs.xmlToObj((String) in.get("xml"));
          res = o == null ? "UNDEFINED" : Json.stringify(o);
        } catch (RuntimeException e) {
          res = "ERR:" + e.getMessage();
        } finally {
          System.setOut(realOut);
        }
        String printed = captured.toString(StandardCharsets.UTF_8);
        if (!printed.isEmpty()) {
          StringBuilder sb = new StringBuilder();
          for (String p : printed.split("\n", -1)) {
            if (!p.isEmpty()) sb.append("STDOUT:").append(p).append('\n');
          }
          res = sb + res;
        }
        w.write(Json.stringify(new JsObject().with("id", in.get("id")).with("out", res)));
        w.write('\n');
        n++;
      }
    }
    realOut.println(n + " results");
  }
}

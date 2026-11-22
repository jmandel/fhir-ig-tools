package org.hl7.fhir.sushi.fshtypes;

import java.util.Arrays;
import org.hl7.fhir.common.js.JsObject;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

// port of sushi/src/fshtypes/FshCode.ts (SUSHI 3.21.1)
/** FHIR-side results (Coding, CodeableConcept, Quantity) are JS-model maps; key insertion order matches the TS. */
public class FshCode extends FshEntity {
  static final Pattern NEEDS_QUOTES = Pattern.compile("^\"|" + Common.JS_WS);

  public String code;
  public String system;
  public String display;

  public FshCode(String code) {
    this(code, null, null);
  }

  public FshCode(String code, String system) {
    this(code, system, null);
  }

  public FshCode(String code, String system, String display) {
    this.code = code;
    this.system = system;
    this.display = display;
  }

  @Override public String toString() {
    String codeToShow = code;
    if (NEEDS_QUOTES.matcher(code).find()) {
      codeToShow = "\"" + Common.fshifyString(code) + "\"";
    }
    String str = (system != null ? system : "") + "#" + codeToShow;
    return Common.truthy(display) ? str + " \"" + Common.fshifyString(display) + "\"" : str;
  }

  public JsObject toFHIRCoding() {
    JsObject coding = new JsObject();
    if (Common.truthy(code)) {
      coding.put("code", code);
    }
    if (Common.truthy(system)) {
      if (system.indexOf('|') > -1) {
        // JS split('|') keeps trailing empty strings; Java needs limit -1
        List<String> parts = Arrays.asList(system.split("\\|", -1));
        coding.put("system", parts.get(0));
        coding.put("version", String.join("|", parts.subList(1, parts.size())));
      } else {
        coding.put("system", system);
      }
    }
    if (Common.truthy(display)) {
      coding.put("display", display);
    }
    return coding;
  }

  public JsObject toFHIRCodeableConcept() {
    JsObject cc = new JsObject();
    List<Object> codings = new ArrayList<>();
    codings.add(toFHIRCoding());
    cc.put("coding", codings);
    return cc;
  }

  public JsObject toFHIRQuantity() {
    JsObject quantity = new JsObject();
    if (Common.truthy(code)) {
      quantity.put("code", code);
    }
    if (Common.truthy(system)) {
      quantity.put("system", system);
    }
    if (Common.truthy(display)) {
      quantity.put("unit", display);
    }
    return quantity;
  }
}

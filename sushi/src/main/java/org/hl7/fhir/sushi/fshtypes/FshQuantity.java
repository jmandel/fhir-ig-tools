package org.hl7.fhir.sushi.fshtypes;

import java.util.ArrayList;
import org.hl7.fhir.sushi.js.JsObject;
import java.util.List;
import java.util.Objects;

// port of sushi/src/fshtypes/FshQuantity.ts (SUSHI 3.21.1)
public class FshQuantity extends FshEntity {
  /** JS number (nullable). */
  public Double value;
  public FshCode unit;

  public FshQuantity(Double value) {
    this(value, null);
  }

  public FshQuantity(Double value, FshCode unit) {
    this.value = value;
    this.unit = unit;
  }

  @Override public String toString() {
    List<String> stringParts = new ArrayList<>();
    if (value != null) {
      stringParts.add(Common.jsNumberToString(value));
    }
    if (unit != null && unit.code != null) {
      if ("http://unitsofmeasure.org".equals(unit.system)) {
        stringParts.add("'" + unit.code + "'");
        if (Common.truthy(unit.display)) {
          stringParts.add("\"" + Common.fshifyString(unit.display) + "\"");
        }
      } else {
        stringParts.add(unit.toString());
      }
    }
    return String.join(" ", stringParts);
  }

  public JsObject toFHIRQuantity() {
    JsObject quantity = new JsObject();
    if (value != null) {
      quantity.put("value", value);
    }
    if (unit != null && Common.truthy(unit.code)) {
      quantity.put("code", unit.code);
    }
    if (unit != null && Common.truthy(unit.system)) {
      quantity.put("system", unit.system);
    }
    if (unit != null && Common.truthy(unit.display)) {
      quantity.put("unit", unit.display);
    }
    return quantity;
  }

  /** TS equals(other). Renamed: Java equals(Object) keeps TS identity semantics for collections. */
  public boolean equalsFsh(FshQuantity other) {
    return Common.jsStrictEquals(value, other.value)
        && Objects.equals(unit == null ? null : unit.code, other.unit == null ? null : other.unit.code)
        && Objects.equals(unit == null ? null : unit.system, other.unit == null ? null : other.unit.system)
        && Objects.equals(unit == null ? null : unit.display, other.unit == null ? null : other.unit.display);
  }
}

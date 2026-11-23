package org.hl7.fhir.sushi.fshtypes;

import org.hl7.fhir.common.js.JsObject;
import java.util.Objects;

// port of sushi/src/fshtypes/FshReference.ts (SUSHI 3.21.1)
public class FshReference extends FshEntity {
  public String sdType;
  public String reference;
  public String display;

  public FshReference(String reference) {
    this(reference, null);
  }

  public FshReference(String reference, String display) {
    this.reference = reference;
    this.display = display;
  }

  @Override public String toString() {
    return "Reference(" + reference + ")" + (Common.truthy(display) ? " \"" + display + "\"" : "");
  }

  public JsObject toFHIRReference() {
    JsObject ref = new JsObject();
    ref.put("reference", reference);
    if (Common.truthy(display)) {
      ref.put("display", display);
    }
    return ref;
  }

  /** TS equals(other, ignoreOtherDisplay = false); see FshQuantity.equalsFsh. */
  public boolean equalsFsh(FshReference other) {
    return equalsFsh(other, false);
  }

  public boolean equalsFsh(FshReference other, boolean ignoreOtherDisplay) {
    return Objects.equals(reference, other.reference)
        && (Objects.equals(display, other.display) || (ignoreOtherDisplay && other.display == null));
  }
}

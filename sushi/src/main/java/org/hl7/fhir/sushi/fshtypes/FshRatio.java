package org.hl7.fhir.sushi.fshtypes;

import org.hl7.fhir.common.js.JsObject;

// port of sushi/src/fshtypes/FshRatio.ts (SUSHI 3.21.1)
public class FshRatio extends FshEntity {
  public FshQuantity numerator;
  public FshQuantity denominator;

  public FshRatio(FshQuantity numerator, FshQuantity denominator) {
    this.numerator = numerator;
    this.denominator = denominator;
  }

  @Override public String toString() {
    return numerator.toString() + " : " + denominator.toString();
  }

  public JsObject toFHIRRatio() {
    JsObject ratio = new JsObject();
    if (numerator != null) {
      ratio.put("numerator", numerator.toFHIRQuantity());
    }
    if (denominator != null) {
      ratio.put("denominator", denominator.toFHIRQuantity());
    }
    return ratio;
  }

  /** TS equals(other); see FshQuantity.equalsFsh. */
  public boolean equalsFsh(FshRatio other) {
    return numerator.equalsFsh(other.numerator) && denominator.equalsFsh(other.denominator);
  }
}

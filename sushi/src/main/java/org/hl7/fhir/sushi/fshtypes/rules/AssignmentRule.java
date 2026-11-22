package org.hl7.fhir.sushi.fshtypes.rules;

import java.math.BigInteger;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fshtypes.Common;
import org.hl7.fhir.sushi.fshtypes.FshCanonical;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshQuantity;
import org.hl7.fhir.sushi.fshtypes.FshRatio;
import org.hl7.fhir.sushi.fshtypes.FshReference;

// port of sushi/src/fshtypes/rules/AssignmentRule.ts (SUSHI 3.21.1)
public class AssignmentRule extends Rule implements SdRule, LrRule {
  /**
   * TS AssignmentValueType: Boolean | Double (JS number) | BigInteger (JS bigint) | String | FshCanonical | FshCode |
   * FshQuantity | FshRatio | FshReference | InstanceDefinition. A String with isInstance=true names an Instance.
   */
  public Object value;
  /** The literal FSH text of a boolean/number value (keeps e.g. "1.50"); null = undefined. */
  public String rawValue;
  public boolean exactly;
  public boolean isInstance;

  public AssignmentRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "AssignmentRule";
  }

  @Override public String toFSH() {
    String printableValue;
    if (value instanceof Boolean || value instanceof Double || value instanceof BigInteger) {
      printableValue = rawValue != null ? rawValue : Common.jsString(value);
    } else if (value instanceof String s) {
      printableValue = isInstance ? s : "\"" + Common.fshifyString(s) + "\"";
    } else if (value instanceof FshCode || value instanceof FshQuantity || value instanceof FshRatio
        || value instanceof FshReference || value instanceof FshCanonical) {
      printableValue = value.toString();
    } else if (value instanceof InstanceDefinition inst) {
      printableValue = Common.jsString(inst.id());
    } else {
      throw new IllegalStateException("unexpected AssignmentRule value " + value);
    }
    return "* " + path + " = " + printableValue + (exactly ? " (exactly)" : "");
  }
}

package org.hl7.fhir.sushi.fshtypes.rules;

import java.util.Objects;

// port of sushi/src/fshtypes/rules/OnlyRule.ts type OnlyRuleType (SUSHI 3.21.1)
/** Flags are optional (null = undefined). Value semantics (TS tests compare with toEqual). */
public class OnlyRuleType {
  public String type;
  public Boolean isReference;
  public Boolean isCanonical;
  public Boolean isCodeableReference;

  public OnlyRuleType(String type) {
    this.type = type;
  }

  public static OnlyRuleType reference(String type) {
    OnlyRuleType t = new OnlyRuleType(type);
    t.isReference = true;
    return t;
  }

  public static OnlyRuleType canonical(String type) {
    OnlyRuleType t = new OnlyRuleType(type);
    t.isCanonical = true;
    return t;
  }

  public static OnlyRuleType codeableReference(String type) {
    OnlyRuleType t = new OnlyRuleType(type);
    t.isCodeableReference = true;
    return t;
  }

  @Override public boolean equals(Object o) {
    return o instanceof OnlyRuleType t && Objects.equals(type, t.type) && Objects.equals(isReference, t.isReference)
        && Objects.equals(isCanonical, t.isCanonical) && Objects.equals(isCodeableReference, t.isCodeableReference);
  }

  @Override public int hashCode() {
    return Objects.hash(type, isReference, isCanonical, isCodeableReference);
  }
}

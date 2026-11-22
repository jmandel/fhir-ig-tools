package org.hl7.fhir.sushi.errors;

import java.util.List;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;

// port of sushi/src/errors/InvalidChoiceTypeRulePathError.ts (SUSHI 3.21.1)
public class InvalidChoiceTypeRulePathError extends RuntimeException implements Annotated {
  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/formats.html#choice");
  public final Rule rule;

  public InvalidChoiceTypeRulePathError(Rule rule) {
    super("As a FHIR choice data type, the specified " + s(rule.path) + " for " + rule.constructorName() + " must end with '[x]'.");
    this.rule = rule;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  private static String s(Object v) {
    return Errors.str(v);
  }
}

package org.hl7.fhir.sushi.importer;

import java.util.List;
import org.hl7.fhir.sushi.fshtypes.ParamRuleSet;

// port of sushi/src/import/MiniFSHImporter.ts (SUSHI 3.21.1)
/** Substitutes insert-rule parameter values into a parameterized RuleSet's raw text (re-parsed by FSHImporter). */
public final class MiniFSHImporter {
  private MiniFSHImporter() {}

  /** TS applyRuleSetSubstitutions: the FSH text {@code RuleSet: <name>\n<rules with params substituted>}. */
  public static String applyRuleSetSubstitutions(ParamRuleSet ruleSet, List<String> values) {
    throw new UnsupportedOperationException("TODO WP-2");
  }
}

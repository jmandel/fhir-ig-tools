package org.hl7.fhir.sushi.fshtypes.rules;

import org.hl7.fhir.sushi.fshtypes.FshEntity;

// port of sushi/src/fshtypes/rules/Rule.ts (SUSHI 3.21.1)
/**
 * Base of every rule. Not sealed: RuleSet insertion clones and re-paths rules freely, and tests construct bare
 * Rules. {@link #constructorName()} is the TS class name (used in messages and AllowedRules).
 */
public class Rule extends FshEntity {
  public String path;

  public Rule(String path) {
    this.path = path;
  }

  public String constructorName() {
    return "Rule";
  }

  /** FSH text of the rule. TS defines toFSH only on concrete rules; the base has none. */
  public String toFSH() {
    throw new UnsupportedOperationException(constructorName() + " has no toFSH");
  }
}

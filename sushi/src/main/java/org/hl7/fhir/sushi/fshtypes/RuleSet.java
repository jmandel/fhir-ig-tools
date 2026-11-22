package org.hl7.fhir.sushi.fshtypes;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;

// port of sushi/src/fshtypes/RuleSet.ts (SUSHI 3.21.1)
/** A re-usable group of rules (any rule type; validity is checked where it is inserted). */
public class RuleSet extends FshEntity {
  public String name;
  public List<Rule> rules = new ArrayList<>();

  public RuleSet(String name) {
    this.name = name;
  }

  public String constructorName() {
    return "RuleSet";
  }

  /** Read-only: the name. */
  public String getId() {
    return name;
  }
}

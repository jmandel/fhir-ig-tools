package org.hl7.fhir.sushi.fshtypes.rules;

import java.util.Objects;

// port of sushi/src/fshtypes/rules/ContainsRule.ts type ContainsRuleItem (SUSHI 3.21.1)
/** A slice name, and (for {@code Type named name}) the type/extension it is defined by. */
public class ContainsRuleItem {
  public String name;
  /** optional */
  public String type;

  public ContainsRuleItem(String name) {
    this.name = name;
  }

  public ContainsRuleItem(String name, String type) {
    this.name = name;
    this.type = type;
  }

  @Override public boolean equals(Object o) {
    return o instanceof ContainsRuleItem i && Objects.equals(name, i.name) && Objects.equals(type, i.type);
  }

  @Override public int hashCode() {
    return Objects.hash(name, type);
  }
}

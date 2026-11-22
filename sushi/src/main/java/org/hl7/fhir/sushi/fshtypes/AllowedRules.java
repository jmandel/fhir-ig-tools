package org.hl7.fhir.sushi.fshtypes;

import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.fshtypes.rules.*;

// port of sushi/src/fshtypes/AllowedRules.ts (SUSHI 3.21.1)
/**
 * "Allowed" means allowed to be exported: InsertRule is allowed nowhere (it must be expanded first). Keyed by
 * constructorName(); subclass instances match (ValueSetConceptComponentRule is a ValueSetComponentRule).
 */
public final class AllowedRules {
  private AllowedRules() {}

  private static final List<Class<? extends Rule>> SD = List.of(CardRule.class, CaretValueRule.class,
      ContainsRule.class, AssignmentRule.class, FlagRule.class, ObeysRule.class, OnlyRule.class, BindingRule.class);
  // NO ContainsRule on Logical/Resource
  private static final List<Class<? extends Rule>> LR = List.of(AddElementRule.class, CardRule.class,
      CaretValueRule.class, AssignmentRule.class, FlagRule.class, ObeysRule.class, OnlyRule.class, BindingRule.class);

  private static final Map<String, List<Class<? extends Rule>>> ALLOWED = Map.of(
      "Profile", SD,
      "Extension", SD,
      "Instance", List.of(AssignmentRule.class, PathRule.class),
      "FshValueSet", List.of(ValueSetComponentRule.class, CaretValueRule.class),
      "FshCodeSystem", List.of(ConceptRule.class, CaretValueRule.class),
      "Invariant", List.of(AssignmentRule.class),
      "Mapping", List.of(MappingRule.class),
      "RuleSet", List.of(AddElementRule.class, CardRule.class, CaretValueRule.class, ConceptRule.class,
          ContainsRule.class, AssignmentRule.class, FlagRule.class, MappingRule.class, ObeysRule.class,
          OnlyRule.class, ValueSetComponentRule.class, PathRule.class, BindingRule.class),
      "Logical", LR,
      "Resource", LR);

  public static boolean isAllowedRule(FshEntity fshDefinition, Rule rule) {
    List<Class<? extends Rule>> allowed = ALLOWED.get(fshDefinition.constructorName());
    return allowed != null && allowed.stream().anyMatch(c -> c.isInstance(rule));
  }
}

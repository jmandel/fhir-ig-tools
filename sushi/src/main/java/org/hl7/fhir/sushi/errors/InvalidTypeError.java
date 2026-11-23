package org.hl7.fhir.sushi.errors;

import java.util.ArrayList;
import java.util.List;

// port of sushi/src/errors/InvalidTypeError.ts (SUSHI 3.21.1)
public class InvalidTypeError extends RuntimeException implements Annotated {
  /**
   * The view of a TS {@code ElementDefinitionType} this message needs (its {@code code} getter, {@code profile},
   * {@code targetProfile}); {@code fhirtypes.ElementDefinitionType} (WP-4) implements it. Lists may be null (TS
   * undefined).
   */
  public interface AllowedType {
    String getCode();

    List<String> getProfile();

    List<String> getTargetProfile();
  }

  public final List<String> specReferences = List.of(
      "http://hl7.org/fhir/R4/elementdefinition-definitions.html#ElementDefinition.type");
  public final String invalidType;
  public final List<? extends AllowedType> allowedTypes;

  public InvalidTypeError(String invalidType, List<? extends AllowedType> allowedTypes) {
    super("The type \"" + Errors.str(invalidType) + "\" does not match any of the allowed types: "
        + allowedTypesToString(allowedTypes));
    this.invalidType = invalidType;
    this.allowedTypes = allowedTypes;
  }

  @Override public List<String> getSpecReferences() {
    return specReferences;
  }

  /**
   * Represents allowed types as a string of choices, e.g. {@code Condition or Procedure},
   * {@code Reference(Patient | Practitioner)}.
   */
  static String allowedTypesToString(List<? extends AllowedType> types) {
    if (types == null || types.isEmpty()) {
      return "<none>";
    }
    List<String> strings = new ArrayList<>();
    for (AllowedType t : types) {
      String code = t.getCode();
      if ("Reference".equals(code)) {
        strings.add("Reference(" + joinTargets(t) + ")");
      } else if ("CodeableReference".equals(code)) {
        strings.add("CodeableReference(" + joinTargets(t) + ")");
      } else if ("canonical".equals(code)) {
        strings.add("Canonical(" + joinTargets(t) + ")");
      } else if (t.getProfile() != null && !t.getProfile().isEmpty()) {
        strings.addAll(t.getProfile());
      } else {
        strings.add(code);
      }
    }
    return Errors.join(" or ", strings);
  }

  // Array.prototype.join renders null/undefined elements as ""
  private static String joinTargets(AllowedType t) {
    List<String> tp = t.getTargetProfile();
    return tp == null ? "" : Errors.join(" | ", tp);
  }
}

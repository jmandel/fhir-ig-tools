package org.hl7.fhir.sushi.fhirtypes;

import java.util.List;

// port of sushi/src/fhirtypes/ElementDefinition.ts -- CONTRACT SKELETON, owned by WP-3 (fhirtypes core).
/** Properties serialize in {@link #PROPS} order; "[x]" props expand to the concrete choice key present. */
public class ElementDefinition {
  /** TS PROPS, verbatim. */
  public static final List<String> PROPS = List.of("id", "extension", "modifierExtension", "path", "representation",
      "sliceName", "sliceIsConstraining", "label", "code", "slicing", "short", "definition", "comment",
      "requirements", "alias", "min", "max", "base", "contentReference", "type", "defaultValue[x]",
      "meaningWhenMissing", "orderMeaning", "fixed[x]", "pattern[x]", "example", "minValue[x]", "maxValue[x]",
      "maxLength", "condition", "constraint", "mustSupport", "isModifier", "isModifierReason", "isSummary",
      "binding", "mapping");
  /** TS ADDITIVE_PROPS: merged, not replaced, when computing diffs. */
  public static final List<String> ADDITIVE_PROPS = List.of("mapping", "constraint");

  public static ElementDefinition fromJSON(Object json, boolean captureOriginal) {
    throw new UnsupportedOperationException("TODO WP-3");
  }

  public Object toJSON() {
    throw new UnsupportedOperationException("TODO WP-3");
  }
}

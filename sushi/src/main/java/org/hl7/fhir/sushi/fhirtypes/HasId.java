package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.utils.FshLogger.logger;

import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;

// port of the HasId mixin in sushi/src/fhirtypes/mixins.ts (SUSHI 3.21.1)
public interface HasId {
  String getId();

  void setId(String id);

  /** Sets the id and checks it against the FHIR id type; logs an error if it does not match. */
  default void setId(FshEntity fshDefinition) {
    setId(Common.idOf(fshDefinition));
    validateId(fshDefinition);
  }

  /**
   * Checks the current id against the FHIR id regex; logs an error if it does not match. If the id is a valid name,
   * sanitizes it to a valid id and logs a warning.
   */
  default void validateId(FshEntity fshDefinition) {
    Rule idRule = org.hl7.fhir.sushi.fshtypes.Common.findAssignmentByPath(fshDefinition, "id", "", "id");
    Object idToCheck = idRule != null && !HasName.isInstance(idRule) ? HasName.ruleValue(idRule) : getId();
    boolean validId = PrimitiveTypes.ID_REGEX.matcher(HasName.jsString(idToCheck)).find();
    String id = getId();
    if (!validId && idRule == null && HasName.NAME_REGEX.matcher(HasName.jsString(id)).find()
        && !(fshDefinition instanceof Instance)) {
      // A valid name can be turned into a valid id by replacing _ with - and slicing to 64 character limit
      String replaced = id.replace("_", "-");
      String sanitizedId = replaced.substring(0, Math.min(64, replaced.length()));
      if (PrimitiveTypes.ID_REGEX.matcher(sanitizedId).find()) {
        // Use the sanitized id, but warn the user to fix this
        logger.warn("The string \"" + id + "\" represents a valid FHIR name but not a valid FHIR id. FHIR ids cannot "
            + "contain \"_\" and can be at most 64 characters. The id will be exported as \"" + sanitizedId
            + "\". Avoid this warning by specifying a valid id directly using the \"Id\" keyword.",
            fshDefinition.sourceInfo);
        setId(sanitizedId);
        validId = true;
      }
    }
    if (!validId) {
      String message = "The string \"" + HasName.jsString(idToCheck) + "\" does not represent a valid FHIR id. FHIR "
          + "ids only allow ASCII letters (A-Z, a-z), numbers (0-9), hyphens (-), and dots (.), with a length limit "
          + "of 64 characters.";
      if (fshDefinition instanceof Instance) {
        message += " Avoid this warning by changing the Instance declaration to follow the FHIR id requirements.";
      }
      logger.error(message, idRule != null ? idRule.sourceInfo : fshDefinition.sourceInfo);
    }
  }
}

package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.utils.FshLogger.logger;

import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.js.Js;

// port of the HasName mixin in sushi/src/fhirtypes/mixins.ts (SUSHI 3.21.1)
public interface HasName {
  Pattern NAME_REGEX = Pattern.compile("^[A-Z]([A-Za-z0-9_]){0,254}\\z");

  String getName();

  void setName(String name);

  /**
   * Sets the name and checks it against the invariant for "name" properties (1-255 characters, an upper-case ASCII
   * letter followed by letters, digits and '_'); logs a warning if it does not match.
   */
  default void setName(FshEntity fshDefinition) {
    setName(Common.nameOf(fshDefinition));
    Rule nameRule = org.hl7.fhir.sushi.fshtypes.Common.findAssignmentByPath(fshDefinition, "name", "", "name");
    Object nameToCheck = nameRule != null && !isInstance(nameRule) ? ruleValue(nameRule) : getName();
    String s = jsString(nameToCheck);
    if (!NAME_REGEX.matcher(s).find()) {
      logger.warn("The name \"" + s + "\" may not be suitable for machine processing applications such as code "
          + "generation. Valid names start with an upper-case ASCII letter ('A'..'Z') followed by any combination of "
          + "upper- or lower-case ASCII letters ('A'..'Z', and 'a'..'z'), numerals ('0'..'9') and '_', with a length "
          + "limit of 255 characters.", nameRule != null ? nameRule.sourceInfo : fshDefinition.sourceInfo);
    }
  }

  static boolean isInstance(Rule r) {
    if (r instanceof AssignmentRule a) return a.isInstance;
    if (r instanceof CaretValueRule c) return c.isInstance;
    return false;
  }

  static Object ruleValue(Rule r) {
    if (r instanceof AssignmentRule a) return a.value;
    if (r instanceof CaretValueRule c) return c.value;
    return null;
  }

  /** JS String(v) / template interpolation for a possibly-undefined value. */
  static String jsString(Object v) {
    return v == null ? "undefined" : v instanceof String s ? s : Js.toStr(v);
  }
}

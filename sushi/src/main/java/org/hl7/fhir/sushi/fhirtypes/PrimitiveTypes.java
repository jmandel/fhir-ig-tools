package org.hl7.fhir.sushi.fhirtypes;

import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.InvalidDateTimeError;

// port of sushi/src/fhirtypes/primitiveTypes.ts (SUSHI 3.21.1)
public final class PrimitiveTypes {
  private PrimitiveTypes() {}

  private static final Pattern DATE_TIME_REGEX = Pattern.compile(
      "^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|"
          + "2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?(Z|(\\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?\\z");

  /** The FHIR id type: 1-64 ASCII letters, digits, '-' and '.'. */
  public static final Pattern ID_REGEX = Pattern.compile("^[A-Za-z0-9\\-.]{1,64}\\z");

  /** Throws InvalidDateTimeError if the string is not a FHIR dateTime. */
  public static void validateFHIRDateTime(String dateTime) {
    if (dateTime == null || !DATE_TIME_REGEX.matcher(dateTime).find()) {
      throw new InvalidDateTimeError(dateTime);
    }
  }
}

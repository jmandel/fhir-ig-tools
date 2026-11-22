package org.hl7.fhir.sushi.fhirtypes;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hl7.fhir.sushi.errors.InvalidDateTimeError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/FHIRdateTime.test.ts (SUSHI 3.21.1)
@DisplayName("FHIRdateTime")
class FHIRdateTimeTest {
  @Nested
  @DisplayName("#validateFHIRdateTime")
  class Validate {
    @Test
    @DisplayName("should allow just a year")
    void year() {
      PrimitiveTypes.validateFHIRDateTime("1964");
    }

    @Test
    @DisplayName("should allow a year and a month")
    void yearMonth() {
      PrimitiveTypes.validateFHIRDateTime("1975-05");
    }

    @Test
    @DisplayName("should allow a date without a time")
    void date() {
      PrimitiveTypes.validateFHIRDateTime("2006-03-29");
    }

    @Test
    @DisplayName("should allow a date and time with a time zone offset")
    void dateTime() {
      PrimitiveTypes.validateFHIRDateTime("2010-02-07T13:28:17+03:00");
      PrimitiveTypes.validateFHIRDateTime("2017-01-01T00:00:00.000Z");
    }

    @Test
    @DisplayName("should throw an error when given an empty string")
    void empty() {
      assertThrows(InvalidDateTimeError.class, () -> PrimitiveTypes.validateFHIRDateTime(""));
    }

    @Test
    @DisplayName("should raise an error when the time zone offset is missing")
    void noOffset() {
      assertThrows(InvalidDateTimeError.class, () -> PrimitiveTypes.validateFHIRDateTime("2010-02-07T13:28:17"));
    }
  }
}

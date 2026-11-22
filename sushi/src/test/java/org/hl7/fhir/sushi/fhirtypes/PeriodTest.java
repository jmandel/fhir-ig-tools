package org.hl7.fhir.sushi.fhirtypes;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hl7.fhir.sushi.errors.InvalidPeriodError;
import org.hl7.fhir.sushi.js.JsObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/Period.test.ts (SUSHI 3.21.1)
@DisplayName("Period")
class PeriodTest {
  static final String earlier = "2001-11-23";
  static final String later = "2002-01-07";

  @Nested
  @DisplayName("#validatePeriod")
  class Validate {
    @Test
    @DisplayName("Period #validatePeriod should be valid when start and/or end are undefined")
    void undefinedBounds() {
      DataTypes.validatePeriod(new JsObject().with("start", "2006-03-29"));
      DataTypes.validatePeriod(new JsObject().with("end", "2010-02-07T13:28:17+03:00"));
      DataTypes.validatePeriod(new JsObject());
    }

    @Test
    @DisplayName("Period #validatePeriod should be valid when start is less than or equal to end")
    void inOrder() {
      DataTypes.validatePeriod(new JsObject().with("start", earlier).with("end", later));
      DataTypes.validatePeriod(new JsObject().with("start", earlier).with("end", earlier));
    }

    @Test
    @DisplayName("Period #validatePeriod should throw an error when start is greater than end")
    void wrongOrder() {
      assertThrows(InvalidPeriodError.class,
          () -> DataTypes.validatePeriod(new JsObject().with("start", later).with("end", earlier)));
    }
  }
}

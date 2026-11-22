package org.hl7.fhir.sushi.fhirtypes;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hl7.fhir.sushi.errors.CodeAndSystemMismatchError;
import org.hl7.fhir.sushi.errors.InvalidRangeValueError;
import org.hl7.fhir.sushi.errors.UnitMismatchError;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/Range.test.ts (SUSHI 3.21.1)
@DisplayName("Range")
class RangeTest {
  static final JsObject small = new JsObject().with("value", 2.0).with("unit", "UCUM#mm")
      .with("system", "http://unitsofmeasure.org").with("code", "mm");
  static final JsObject large = new JsObject().with("value", 25.0).with("unit", "UCUM#mm")
      .with("system", "http://unitsofmeasure.org").with("code", "mm");

  static JsObject range(JsObject low, JsObject high) {
    JsObject r = new JsObject();
    if (low != null) r.put("low", low);
    if (high != null) r.put("high", high);
    return r;
  }

  @Nested
  @DisplayName("#validateRange")
  class Validate {
    @Test
    @DisplayName("Range #validateRange should be valid when one bound or both bounds are undefined")
    void undefinedBounds() {
      DataTypes.validateRange(range(large, null));
      DataTypes.validateRange(range(null, small));
      DataTypes.validateRange(range(null, null));
    }

    @Test
    @DisplayName("Range #validateRange should require low bound value to be less than or equal to high bound value")
    void inOrder() {
      DataTypes.validateRange(range(small, large));
      DataTypes.validateRange(range(large, large));
    }

    @Test
    @DisplayName("Range #validateRange should throw an error when the low bound value is greater than the high bound value")
    void wrongOrder() {
      assertThrows(InvalidRangeValueError.class, () -> DataTypes.validateRange(range(large, small)));
    }

    @Test
    @DisplayName("Range #validateRange should throw an error when both bounds are defined, but have different units")
    void units() {
      JsObject differentUnits = range(Js.cloneDeep(small), large);
      ((JsObject) differentUnits.get("low")).put("unit", "UCUM#kg");
      JsObject missingUnit = range(small, Js.cloneDeep(large));
      ((JsObject) missingUnit.get("high")).remove("unit");
      assertThrows(UnitMismatchError.class, () -> DataTypes.validateRange(differentUnits));
      assertThrows(UnitMismatchError.class, () -> DataTypes.validateRange(missingUnit));
    }

    @Test
    @DisplayName("Range #validateRange should throw an error when both bounds are defined, but have different codes")
    void codes() {
      JsObject differentCode = range(Js.cloneDeep(small), large);
      ((JsObject) differentCode.get("low")).put("code", "kg");
      JsObject missingCode = range(small, Js.cloneDeep(large));
      ((JsObject) missingCode.get("high")).remove("code");
      assertThrows(CodeAndSystemMismatchError.class, () -> DataTypes.validateRange(differentCode));
      assertThrows(CodeAndSystemMismatchError.class, () -> DataTypes.validateRange(missingCode));
    }

    @Test
    @DisplayName("Range #validateRange should throw an error when both bounds are defined, but have different systems")
    void systems() {
      JsObject differentSystem = range(small, Js.cloneDeep(large));
      ((JsObject) differentSystem.get("high")).put("system", "http://all.wrong");
      JsObject missingSystem = range(Js.cloneDeep(small), large);
      ((JsObject) missingSystem.get("low")).remove("system");
      assertThrows(CodeAndSystemMismatchError.class, () -> DataTypes.validateRange(differentSystem));
      assertThrows(CodeAndSystemMismatchError.class, () -> DataTypes.validateRange(missingSystem));
    }
  }
}

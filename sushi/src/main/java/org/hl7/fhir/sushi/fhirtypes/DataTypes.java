package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.Common.getProp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.hl7.fhir.sushi.errors.CodeAndSystemMismatchError;
import org.hl7.fhir.sushi.errors.InvalidPeriodError;
import org.hl7.fhir.sushi.errors.InvalidRangeValueError;
import org.hl7.fhir.sushi.errors.UnitMismatchError;
import org.hl7.fhir.common.js.Js;

// port of sushi/src/fhirtypes/dataTypes.ts (SUSHI 3.21.1). The FHIR data types (CodeableConcept, Coding, Quantity,
// Reference, ...) are TS type aliases over plain objects; in Java they are value-model maps (JsObject). Only the two
// validation functions have behaviour.
public final class DataTypes {
  private DataTypes() {}

  /**
   * Throws if either start or end is not a valid dateTime string, or if start comes after end.
   *
   * @throws InvalidPeriodError when the start comes after the end.
   */
  public static void validatePeriod(Map<String, ?> period) {
    Double start = null;
    Double end = null;
    Object s = period.get("start");
    Object e = period.get("end");
    if (Js.truthy(s)) {
      PrimitiveTypes.validateFHIRDateTime((String) s);
      start = jsDate((String) s);
    }
    if (Js.truthy(e)) {
      PrimitiveTypes.validateFHIRDateTime((String) e);
      end = jsDate((String) e);
    }
    // `start && end && start > end` on Date objects (an Invalid Date is still truthy; NaN compares false)
    if (start != null && end != null && start > end) {
      throw new InvalidPeriodError(period);
    }
  }

  /**
   * JS {@code new Date(s).getTime()} for strings matching the FHIR dateTime regex: date-only forms (YYYY, YYYY-MM,
   * YYYY-MM-DD) are UTC; date-times have an offset (the regex requires one). The process time zone only matters for
   * the date-only forms in V8's legacy parser, which these are not.
   */
  static double jsDate(String s) {
    try {
      if (s.length() == 4) return LocalDate.of(Integer.parseInt(s), 1, 1).atStartOfDay(ZoneId.of("UTC"))
          .toInstant().toEpochMilli();
      if (s.length() == 7) return LocalDate.of(Integer.parseInt(s.substring(0, 4)),
          Integer.parseInt(s.substring(5, 7)), 1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
      if (s.length() == 10) return LocalDate.parse(s).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
      String t = s;
      // second 60 (leap second) is accepted by the regex but invalid for Date: NaN
      if (t.length() >= 19 && t.substring(17, 19).equals("60")) return Double.NaN;
      return OffsetDateTime.parse(t).toInstant().toEpochMilli();
    } catch (RuntimeException ex) {
      return Double.NaN;
    }
  }

  /**
   * Enforces constraints on values, units, codes, and systems in a range.
   *
   * @throws InvalidRangeValueError when the low value is greater than the high value.
   * @throws UnitMismatchError when low and high do not have matching unit attributes.
   * @throws CodeAndSystemMismatchError when low and high do not have matching code and system attributes.
   */
  @SuppressWarnings("unchecked")
  public static void validateRange(Map<String, ?> range) {
    Object low = range.get("low");
    Object high = range.get("high");
    if (Js.truthy(low) && Js.truthy(high)) {
      Object lv = getProp(low, "value");
      Object hv = getProp(high, "value");
      if (lv instanceof Number l && hv instanceof Number h && l.doubleValue() > h.doubleValue()) {
        throw new InvalidRangeValueError(l, h);
      }
      if (!Common.strictEquals(getProp(low, "unit"), getProp(high, "unit"))
          && !(getProp(low, "unit") == null && getProp(high, "unit") == null)) {
        throw new UnitMismatchError((Map<String, ?>) low, (Map<String, ?>) high);
      }
      if ((!Common.strictEquals(getProp(low, "code"), getProp(high, "code"))
          && !(getProp(low, "code") == null && getProp(high, "code") == null))
          || (!Common.strictEquals(getProp(low, "system"), getProp(high, "system"))
              && !(getProp(low, "system") == null && getProp(high, "system") == null))) {
        throw new CodeAndSystemMismatchError((Map<String, ?>) low, (Map<String, ?>) high);
      }
    }
  }

  @SuppressWarnings("unused")
  private static long unused(Instant i, LocalDateTime l) {
    return 0;
  }
}

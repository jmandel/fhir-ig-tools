package org.hl7.fhir.common.js;

import java.math.BigDecimal;

/**
 * ECMAScript Number semantics. {@link #toString(double)} is Number.prototype.toString() (ECMA-262 Number::toString,
 * radix 10): shortest round-trip digits (JDK 19+ Double.toString produces the shortest uniquely-distinguishing
 * decimal, same as V8; on JDK 17 and 18 it doesn't always, JDK-4511638, so the digits are worked out here), laid out
 * with JS's plain/exponential thresholds (1e21, 1e-7).
 */
public final class JsNumber {
  private JsNumber() {}

  /** JDK 19+: Double.toString gives the shortest digits that round-trip (JDK-4511638); before, not always */
  private static final boolean SHORTEST_TO_STRING = Runtime.version().feature() >= 19;

  public static String toString(double v) {
    if (Double.isNaN(v)) return "NaN";
    if (v == 0) return "0"; // also -0
    if (v < 0) return "-" + toString(-v);
    if (Double.isInfinite(v)) return "Infinity";
    BigDecimal bd;
    if (SHORTEST_TO_STRING) {
      bd = new BigDecimal(Double.toString(v)).stripTrailingZeros();
      if (bd.precision() == 2) bd = oneDigitIfRoundTrips(v, bd);
    } else {
      bd = shortestDigits(v);
    }
    String digits = bd.unscaledValue().toString();
    int k = digits.length();
    int n = k - bd.scale(); // v = 0.digits * 10^n
    StringBuilder sb = new StringBuilder();
    if (k <= n && n <= 21) {
      sb.append(digits);
      for (int i = 0; i < n - k; i++) sb.append('0');
    } else if (0 < n && n <= 21) {
      sb.append(digits, 0, n).append('.').append(digits, n, k);
    } else if (-6 < n && n <= 0) {
      sb.append("0.");
      for (int i = 0; i < -n; i++) sb.append('0');
      sb.append(digits);
    } else {
      int e = n - 1;
      sb.append(digits.charAt(0));
      if (k > 1) sb.append('.').append(digits, 1, k);
      sb.append('e').append(e < 0 ? '-' : '+').append(Math.abs(e));
    }
    return sb.toString();
  }

  /**
   * Java's Double.toString never returns fewer than 2 significant digits (e.g. 4.9E-324 for Double.MIN_VALUE) where
   * ECMAScript requires the shortest (5e-324). Pick the closest 1-digit decimal that still round-trips, if any.
   */
  private static BigDecimal oneDigitIfRoundTrips(double v, BigDecimal two) {
    BigDecimal exact = new BigDecimal(v);
    BigDecimal best = null;
    BigDecimal lo = two.round(new java.math.MathContext(1, java.math.RoundingMode.FLOOR));
    BigDecimal ulp = lo.ulp();
    for (BigDecimal c : new BigDecimal[] {lo, lo.add(ulp)}) {
      if (Double.parseDouble(c.toString()) != v) continue;
      if (best == null || c.subtract(exact).abs().compareTo(best.subtract(exact).abs()) < 0) best = c;
    }
    return best == null ? two : best.stripTrailingZeros();
  }

  /**
   * ECMA-262 Number::toString's digits, without relying on Double.toString: the fewest significant digits k for which
   * some k-digit decimal parses back to v; of those, the closest to v (a tie: the even one). For a positive finite v
   */
  static BigDecimal shortestDigits(double v) {
    BigDecimal exact = new BigDecimal(v);
    for (int p = 1; p <= 17; p++) {
      BigDecimal r = exact.round(new java.math.MathContext(p, java.math.RoundingMode.HALF_EVEN));
      BigDecimal ulp = r.ulp();
      BigDecimal best = null;
      for (BigDecimal c : new BigDecimal[] {r, r.subtract(ulp), r.add(ulp)}) {
        if (c.signum() <= 0 || c.precision() > p || Double.parseDouble(c.toString()) != v) continue;
        if (best == null) {
          best = c;
        } else {
          int cmp = c.subtract(exact).abs().compareTo(best.subtract(exact).abs());
          if (cmp < 0 || (cmp == 0 && !c.unscaledValue().testBit(0))) best = c;
        }
      }
      if (best != null) return best.stripTrailingZeros();
    }
    return new BigDecimal(Double.toString(v)).stripTrailingZeros(); // not reached: 17 digits always round-trip
  }

  /** JS ToNumber of a Java numeric (Integer/Long/Double/...), for code that receives mixed numerics. */
  public static double of(Number n) {
    return n.doubleValue();
  }

  /** True when v is a JS "integer" (Number.isInteger). */
  public static boolean isInteger(double v) {
    return !Double.isInfinite(v) && Math.floor(v) == v;
  }
}

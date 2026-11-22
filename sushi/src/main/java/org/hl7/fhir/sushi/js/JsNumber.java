package org.hl7.fhir.sushi.js;

import java.math.BigDecimal;

/**
 * ECMAScript Number semantics. {@link #toString(double)} is Number.prototype.toString() (ECMA-262 Number::toString,
 * radix 10): shortest round-trip digits (JDK 19+ Double.toString produces the shortest uniquely-distinguishing
 * decimal, same as V8), laid out with JS's plain/exponential thresholds (1e21, 1e-7).
 */
public final class JsNumber {
  private JsNumber() {}

  public static String toString(double v) {
    if (Double.isNaN(v)) return "NaN";
    if (v == 0) return "0"; // also -0
    if (v < 0) return "-" + toString(-v);
    if (Double.isInfinite(v)) return "Infinity";
    BigDecimal bd = new BigDecimal(Double.toString(v)).stripTrailingZeros();
    if (bd.precision() == 2) bd = oneDigitIfRoundTrips(v, bd);
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

  /** JS ToNumber of a Java numeric (Integer/Long/Double/...), for code that receives mixed numerics. */
  public static double of(Number n) {
    return n.doubleValue();
  }

  /** True when v is a JS "integer" (Number.isInteger). */
  public static boolean isInteger(double v) {
    return !Double.isInfinite(v) && Math.floor(v) == v;
  }
}

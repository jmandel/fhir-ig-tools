package org.hl7.fhir.sushi.importer.parser;

import java.util.ArrayList;
import java.util.List;

// port of antlr4 JS runtime 4.13.2 misc/IntervalSet.js complement/removeRange/removeOne (as used by SUSHI 3.21.1)
/**
 * The JS runtime's IntervalSet algorithms, kept only for {@code complement}: its removeRange mishandles a removed
 * range that starts at the lower bound of an interval (it keeps the empty part below instead of the part above),
 * so in SUSHI's parser the LL(1) lookahead through {@code ~(...)} (paramRuleSetContent) is empty. That changes the
 * runtime's expected-token sets and single-token insertion, which the hand-written parser must reproduce.
 * Intervals are [start, stop) like the JS implementation.
 */
final class JsIntervalSet {
  private List<int[]> intervals;

  /** The set as the JS ATN deserializer builds a set label: one addRange per maximal run of types. */
  static JsIntervalSet ofTypes(TokenSet set, int maxTokenType) {
    JsIntervalSet s = new JsIntervalSet();
    int t = -2;
    while (t <= maxTokenType) {
      if (set.contains(t)) {
        int a = t;
        while (t + 1 <= maxTokenType && set.contains(t + 1)) t++;
        s.addInterval(a, t + 1);
      }
      t++;
    }
    return s;
  }

  void addInterval(int start, int stop) {
    if (intervals == null) {
      intervals = new ArrayList<>();
      intervals.add(new int[] {start, stop});
      return;
    }
    for (int pos = 0; pos < intervals.size(); pos++) {
      int[] existing = intervals.get(pos);
      if (stop < existing[0]) {
        intervals.add(pos, new int[] {start, stop});
        return;
      } else if (stop == existing[0]) {
        intervals.set(pos, new int[] {start, existing[1]});
        return;
      } else if (start <= existing[1]) {
        intervals.set(pos, new int[] {Math.min(existing[0], start), Math.max(existing[1], stop)});
        reduce(pos);
        return;
      }
    }
    intervals.add(new int[] {start, stop});
  }

  private void reduce(int pos) {
    if (pos < intervals.size() - 1) {
      int[] current = intervals.get(pos);
      int[] next = intervals.get(pos + 1);
      if (current[1] >= next[1]) {
        intervals.remove(pos + 1);
        reduce(pos);
      } else if (current[1] >= next[0]) {
        intervals.set(pos, new int[] {current[0], next[1]});
        intervals.remove(pos + 1);
      }
    }
  }

  /** JS complement(start, stop): [start, stop] minus this set, with the JS removeRange behaviour. */
  JsIntervalSet complement(int start, int stop) {
    JsIntervalSet result = new JsIntervalSet();
    result.addInterval(start, stop + 1);
    if (intervals != null) for (int[] r : intervals) result.removeRange(r[0], r[1]);
    return result;
  }

  private void removeRange(int rStart, int rStop) {
    if (rStart == rStop - 1) {
      removeOne(rStart);
    } else if (intervals != null) {
      int pos = 0;
      for (int n = 0; n < intervals.size(); n++) {
        int[] existing = intervals.get(pos);
        if (rStop <= existing[0]) {
          return;
        } else if (rStart > existing[0] && rStop < existing[1]) {
          intervals.set(pos, new int[] {existing[0], rStart});
          intervals.add(pos, new int[] {rStop, existing[1]});
          return;
        } else if (rStart <= existing[0] && rStop >= existing[1]) {
          intervals.remove(pos);
          pos = pos - 1;
        } else if (rStart < existing[1]) {
          intervals.set(pos, new int[] {existing[0], rStart});
        } else if (rStop < existing[1]) {
          intervals.set(pos, new int[] {rStop, existing[1]});
        }
        pos += 1;
      }
    }
  }

  private void removeOne(int value) {
    if (intervals == null) return;
    for (int i = 0; i < intervals.size(); i++) {
      int[] existing = intervals.get(i);
      if (value < existing[0]) {
        return;
      } else if (value == existing[0] && value == existing[1] - 1) {
        intervals.remove(i);
        return;
      } else if (value == existing[0]) {
        intervals.set(i, new int[] {existing[0] + 1, existing[1]});
        return;
      } else if (value == existing[1] - 1) {
        intervals.set(i, new int[] {existing[0], existing[1] - 1});
        return;
      } else if (value < existing[1] - 1) {
        int[] replace = {existing[0], value};
        existing[0] = value + 1;
        intervals.add(i, replace);
        return;
      }
    }
  }

  /** The members (contains() semantics: any interval containing the value). */
  TokenSet members() {
    TokenSet s = new TokenSet();
    if (intervals != null) {
      for (int[] iv : intervals) for (int t = iv[0]; t < iv[1]; t++) s.add(t);
    }
    return s;
  }
}

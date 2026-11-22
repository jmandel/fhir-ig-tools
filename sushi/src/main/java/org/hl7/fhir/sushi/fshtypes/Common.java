package org.hl7.fhir.sushi.fshtypes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.js.JsNumber;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;

// port of sushi/src/fshtypes/common.ts (SUSHI 3.21.1), plus small JS-semantics helpers used by fshtypes
public final class Common {
  private Common() {}

  /** os.EOL: SUSHI runs on Linux in the corpus; all toFSH output uses "\n". */
  public static final String EOL = "\n";

  /**
   * JS regex {@code \s} as a Java character class. Java's {@code \s} is ASCII-only; JS's includes Unicode spaces.
   */
  public static final String JS_WS =
      "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]";

  /** JS truthiness for String fields: null/undefined and "" are falsy. */
  public static boolean truthy(String s) {
    return s != null && !s.isEmpty();
  }

  /** JS truthiness for Boolean fields. */
  public static boolean truthy(Boolean b) {
    return b != null && b;
  }

  /** JS {@code a === b} for numbers (NaN !== NaN, 0 === -0, undefined === undefined). */
  public static boolean jsStrictEquals(Double a, Double b) {
    if (a == null || b == null) return a == b;
    return a.doubleValue() == b.doubleValue();
  }

  /** JS Number.prototype.toString(); delegates to {@link JsNumber#toString(double)}. */
  public static String jsNumberToString(double d) {
    return JsNumber.toString(d);
  }

  /**
   * JS template interpolation of an optional scalar field: a missing (null) field prints "undefined" as in TS.
   * (Js.toStr maps null to "null" because in the JSON value model null is JS null.)
   */
  public static String jsString(Object v) {
    if (v == null) return "undefined";
    if (v instanceof Double d) return jsNumberToString(d);
    if (v instanceof BigInteger b) return b.toString();
    return v.toString();
  }

  public static String typeString(List<OnlyRuleType> types) {
    List<OnlyRuleType> references = new ArrayList<>();
    List<OnlyRuleType> canonicals = new ArrayList<>();
    List<OnlyRuleType> codeableReferences = new ArrayList<>();
    List<OnlyRuleType> normals = new ArrayList<>();
    for (OnlyRuleType t : types) {
      if (truthy(t.isReference)) references.add(t);
      else if (truthy(t.isCanonical)) canonicals.add(t);
      else if (truthy(t.isCodeableReference)) codeableReferences.add(t);
      else normals.add(t);
    }
    String normalString = join(normals);
    String referenceString = references.isEmpty() ? "" : "Reference(" + join(references) + ")";
    String canonicalString = canonicals.isEmpty() ? "" : "Canonical(" + join(canonicals) + ")";
    String codeableReferenceString =
        codeableReferences.isEmpty() ? "" : "CodeableReference(" + join(codeableReferences) + ")";
    List<String> parts = new ArrayList<>();
    for (String s : List.of(normalString, referenceString, canonicalString, codeableReferenceString)) {
      if (!s.isEmpty()) parts.add(s);
    }
    return String.join(" or ", parts);
  }

  private static String join(List<OnlyRuleType> ts) {
    List<String> s = new ArrayList<>();
    for (OnlyRuleType t : ts) s.add(t.type);
    return String.join(" or ", s);
  }

  /** Adds expected backslash-escapes to a string to make it a FSH string. */
  public static String fshifyString(String input) {
    return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  /** The rules array of any FSH definition that has rules (TS: fshDefinition.rules). */
  public static List<? extends Rule> rulesOf(FshEntity def) {
    if (def instanceof FshStructure s) return s.rules;
    if (def instanceof Instance i) return i.rules;
    if (def instanceof FshValueSet v) return v.rules;
    if (def instanceof FshCodeSystem c) return c.rules;
    if (def instanceof Invariant inv) return inv.rules;
    if (def instanceof RuleSet r) return r.rules;
    if (def instanceof Mapping m) return m.rules;
    throw new IllegalArgumentException("no rules on " + def.getClass().getSimpleName());
  }

  /**
   * Cache of rule lookups (see the long comment in common.ts). TS keys a WeakMap by the rules array's identity;
   * Java has no weak identity map, so the cache lives on the definition and remembers which list instance it was
   * built for: replacing the definition's rules list (as applyInsertRules does) starts an empty cache, exactly as in
   * TS. Output-neutral either way.
   */
  public static final class RuleLookupCache {
    List<? extends Rule> rules;
    int length;
    Rule lastRule;
    final Map<String, Rule> rulesByLookup = new HashMap<>();
  }

  /** Discards the cached rule lookups for a definition (TS clearRuleLookupCache(rules)). */
  public static void clearRuleLookupCache(FshEntity def) {
    def.ruleLookupCache = null;
  }

  /** Returns the matching AssignmentRule or CaretValueRule, or null. */
  public static Rule findAssignmentByPath(FshEntity fshDefinition, String assignmentRulePath, String caretRulePath,
      String caretRuleCaretPath) {
    List<? extends Rule> rules = rulesOf(fshDefinition);
    Rule lastRule = rules.isEmpty() ? null : rules.get(rules.size() - 1);
    RuleLookupCache cache = fshDefinition.ruleLookupCache;
    if (cache == null || cache.rules != rules || cache.length != rules.size() || cache.lastRule != lastRule) {
      cache = new RuleLookupCache();
      cache.rules = rules;
      cache.length = rules.size();
      cache.lastRule = lastRule;
      fshDefinition.ruleLookupCache = cache;
    }
    String key;
    Predicate<Rule> matches;
    if (fshDefinition instanceof Instance || fshDefinition instanceof Invariant) {
      key = "assignment|" + assignmentRulePath;
      matches = rule -> rule instanceof AssignmentRule a && assignmentRulePath.equals(a.path);
    } else {
      key = "caret|" + caretRulePath + "|" + caretRuleCaretPath;
      matches = rule -> rule instanceof CaretValueRule c && caretRulePath.equals(c.path)
          && caretRuleCaretPath.equals(c.caretPath);
    }
    Rule cachedRule = cache.rulesByLookup.get(key);
    if (cache.rulesByLookup.containsKey(key) && (cachedRule == null || matches.test(cachedRule))) {
      return cachedRule;
    }
    Rule foundRule = null;
    for (int i = rules.size() - 1; i >= 0; i--) {
      if (matches.test(rules.get(i))) {
        foundRule = rules.get(i);
        break;
      }
    }
    cache.rulesByLookup.put(key, foundRule);
    return foundRule;
  }

  /** TS return type { value, isInstance } | undefined. */
  public record ValueFromRules(Object value, boolean isInstance) {}

  public static ValueFromRules getValueFromRules(FshEntity fshDefinition, String assignmentRulePath,
      String caretRulePath, String caretRuleCaretPath) {
    Rule foundRule = findAssignmentByPath(fshDefinition, assignmentRulePath, caretRulePath, caretRuleCaretPath);
    if (foundRule instanceof AssignmentRule a) return new ValueFromRules(a.value, a.isInstance);
    if (foundRule instanceof CaretValueRule c) return new ValueFromRules(c.value, c.isInstance);
    return null;
  }

  /** Returns the value, or null (undefined) when no rule or the rule assigns an instance. */
  public static Object getNonInstanceValueFromRules(FshEntity fshDefinition, String assignmentRulePath,
      String caretRulePath, String caretRuleCaretPath) {
    ValueFromRules foundValue =
        getValueFromRules(fshDefinition, assignmentRulePath, caretRulePath, caretRuleCaretPath);
    if (foundValue != null && !foundValue.isInstance()) {
      return foundValue.value();
    }
    return null;
  }

  /** Shared body of the entities' toFSH(): metadata, then rules each on its own line. */
  static String toFSH(String metadataFSH, List<? extends Rule> rules) {
    List<String> lines = new ArrayList<>();
    for (Rule r : rules) lines.add(r.toFSH());
    String rulesFSH = String.join(EOL, lines);
    return metadataFSH + (rulesFSH.isEmpty() ? "" : EOL + rulesFSH);
  }

  /** Description/expression lines: multiline strings use triple quotes, others are fshified. */
  static String quoted(String s) {
    return s.indexOf('\n') > -1 ? "\"\"\"" + s + "\"\"\"" : "\"" + fshifyString(s) + "\"";
  }
}

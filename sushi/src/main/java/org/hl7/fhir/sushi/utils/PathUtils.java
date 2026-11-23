package org.hl7.fhir.sushi.utils;

import static org.hl7.fhir.sushi.utils.FshLogger.logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fhirtypes.PathPart;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.common.js.Js;

// port of sushi/src/utils/PathUtils.ts (SUSHI 3.21.1)
public final class PathUtils {
  private PathUtils() {}

  private static final Pattern BASE = Pattern.compile("^([^\\[]+(\\[x\\])?)");
  private static final Pattern BRACKET = Pattern.compile("\\[([^\\[\\]]|\\[(?:[^\\[\\]]*)\\])*\\]");
  private static final Pattern INDEX = Pattern.compile("^[0-9]+\\z");

  /** Thrown inside the soft-index conversion and caught (logged) by resolveSoftIndexing, like the TS Error. */
  static final class SoftIndexError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    SoftIndexError(String message) {
      super(message);
    }
  }

  /** Parses a FSH Path into a more easily usable form. */
  public static List<PathPart> parseFSHPath(String fshPath) {
    return parseFSHPath(fshPath, null);
  }

  public static List<PathPart> parseFSHPath(String fshPath, SourceInfo sourceInfo) {
    List<PathPart> pathParts = new ArrayList<>();
    List<String> seenSlices = new ArrayList<>();
    List<String> splitPath = ".".equals(fshPath) ? List.of(fshPath) : Common.splitOnPathPeriods(fshPath);
    for (String pathPart : splitPath) {
      Matcher bm = BASE.matcher(pathPart);
      PathPart parsedPart = new PathPart(bm.find() ? bm.group() : "");
      if (pathPart.length() > parsedPart.base.length()) {
        // Get the content from the outermost bracket pairs.
        parsedPart.brackets = new ArrayList<>();
        Matcher m = BRACKET.matcher(pathPart.substring(parsedPart.base.length()));
        while (m.find()) {
          String g = m.group();
          parsedPart.brackets.add(g.substring(1, g.length() - 1));
        }
        for (String b : parsedPart.brackets) {
          if (!INDEX.matcher(b).find() && !(b.equals("+") || b.equals("="))) seenSlices.add(b);
        }
        if (!seenSlices.isEmpty()) {
          parsedPart.slices = new ArrayList<>(seenSlices);
        }
        int parsedPartLength = parsedPart.base.length();
        for (String b : parsedPart.brackets) parsedPartLength += b.length() + 2;
        if (pathPart.length() != parsedPartLength) {
          String message = "Error processing path due to unmatched brackets: " + fshPath + ". ";
          if (sourceInfo != null) {
            logger.error(message, sourceInfo);
          } else {
            logger.error(message);
          }
        }
      }
      pathParts.add(parsedPart);
    }
    return pathParts;
  }

  /** Assembles a PathPart array back to its original form. */
  public static String assembleFSHPath(List<PathPart> pathParts) {
    StringBuilder path = new StringBuilder();
    for (int i = 0; i < pathParts.size(); i++) {
      PathPart pathPart = pathParts.get(i);
      path.append(pathPart.base);
      if (pathPart.brackets != null) {
        for (String bracket : pathPart.brackets) path.append('[').append(bracket).append(']');
      }
      if (i < pathParts.size() - 1) {
        path.append('.');
      }
    }
    return path.toString();
  }

  private static String mapName(PathPart element, int takeSlices) {
    List<String> slices = element.slices == null ? List.of() : element.slices;
    return (element.prefix == null ? "" : element.prefix) + "." + element.base + "|"
        + String.join("|", slices.subList(0, Math.min(takeSlices, slices.size())));
  }

  private static String firstNumeric(List<String> brackets) {
    if (brackets == null) return null;
    for (String b : brackets) if (INDEX.matcher(b).find()) return b;
    return null;
  }

  /**
   * Allows named slices to be accessed without a name - they can be accessed via the numeric index or soft index
   * they will eventually appear at.
   */
  private static void convertSoftIndices(PathPart element, Map<String, Integer> pathMap) {
    String mapName = mapName(element, Integer.MAX_VALUE);
    if (!pathMap.containsKey(mapName)) {
      String existingNumericBracket = firstNumeric(element.brackets);
      if (existingNumericBracket != null) {
        pathMap.put(mapName, parseIntJs(existingNumericBracket));
      } else {
        pathMap.put(mapName, 0);
        if (element.brackets != null && element.brackets.contains("+")) {
          element.brackets.set(element.brackets.indexOf("+"), "0");
        } else if (element.brackets != null && element.brackets.contains("=")) {
          // If a sequence begins with a '=', we log an error but assume a value of 0
          element.brackets.set(element.brackets.indexOf("="), "0");
          throw new SoftIndexError(
              "The first index in a Soft Indexing sequence must be \"+\", an actual index of \"0\" has been assumed");
        }
      }
    } else if (element.brackets != null) {
      for (int index = 0; index < element.brackets.size(); index++) {
        String bracket = element.brackets.get(index);
        if (bracket.equals("+")) {
          int newIndex = pathMap.get(mapName) + 1;
          element.brackets.set(index, Integer.toString(newIndex));
          pathMap.put(mapName, newIndex);
        } else if (bracket.equals("=")) {
          int currentIndex = pathMap.get(mapName);
          element.brackets.set(index, Integer.toString(currentIndex));
        } else if (INDEX.matcher(bracket).find()) {
          // If a numeric index is found, we update our pathMap so subsequent soft indexes are converted in that context
          pathMap.put(mapName, parseIntJs(bracket));
        }
      }
    }
  }

  /**
   * Requires named slices to be accessed with a slice name. Soft indices can be used in conjunction with named slices
   * and are resolved within that named slice's context.
   */
  private static void convertSoftIndicesStrict(PathPart element, Map<String, Integer> pathMap,
      Map<String, Integer> maxPathMap) {
    String mapName = mapName(element, Integer.MAX_VALUE);
    Integer addToBaseElement = null; // track the amount of indices we need to add to the base element of a slice
    if (!pathMap.containsKey(mapName)) {
      String existingNumericBracket = firstNumeric(element.brackets);
      if (existingNumericBracket != null) {
        int indexUsed = parseIntJs(existingNumericBracket);
        pathMap.put(mapName, indexUsed);
        maxPathMap.put(mapName, indexUsed);
        addToBaseElement = parseIntJs(existingNumericBracket) + 1;
      } else {
        pathMap.put(mapName, 0);
        maxPathMap.put(mapName, 0);
        addToBaseElement = 1;
        if (element.brackets != null && element.brackets.contains("+")) {
          element.brackets.set(element.brackets.indexOf("+"), "0");
        } else if (element.brackets != null && element.brackets.contains("=")) {
          element.brackets.set(element.brackets.indexOf("="), "0");
          throw new SoftIndexError(
              "The first index in a Soft Indexing sequence must be \"+\", an actual index of \"0\" has been assumed");
        }
      }
    } else if (element.brackets != null) {
      for (int index = 0; index < element.brackets.size(); index++) {
        String bracket = element.brackets.get(index);
        if (bracket.equals("+")) {
          int newIndex = pathMap.get(mapName) + 1;
          element.brackets.set(index, Integer.toString(newIndex));
          pathMap.put(mapName, newIndex);
          if (newIndex > maxPathMap.get(mapName)) {
            addToBaseElement = newIndex - maxPathMap.get(mapName);
            maxPathMap.put(mapName, newIndex);
          }
        } else if (bracket.equals("=")) {
          int currentIndex = pathMap.get(mapName);
          element.brackets.set(index, Integer.toString(currentIndex));
        } else if (INDEX.matcher(bracket).find()) {
          int newIndex = parseIntJs(bracket);
          pathMap.put(mapName, newIndex);
          if (newIndex > maxPathMap.get(mapName)) {
            addToBaseElement = newIndex - maxPathMap.get(mapName);
            maxPathMap.put(mapName, newIndex);
          }
        }
      }
    }
    // if the element has slices, increment the less-sliced elements
    if (element.slices != null && !element.slices.isEmpty() && addToBaseElement != null) {
      for (int takeSlices = element.slices.size() - 1; takeSlices >= 0; takeSlices--) {
        String lessSlicedMapName = mapName(element, takeSlices);
        if (!pathMap.containsKey(lessSlicedMapName)) {
          // if we are adding a new map entry for the less-sliced element,
          // subtract 1 from the amount to add, since the values we track start at 0.
          pathMap.put(lessSlicedMapName, addToBaseElement - 1);
          maxPathMap.put(lessSlicedMapName, addToBaseElement - 1);
        } else {
          int oldMax = maxPathMap.get(lessSlicedMapName);
          int newIndex = pathMap.get(lessSlicedMapName) + addToBaseElement;
          pathMap.put(lessSlicedMapName, newIndex);
          if (newIndex > oldMax) {
            maxPathMap.put(lessSlicedMapName, newIndex);
          }
        }
      }
    }
  }

  /** parseInt of an all-digit string (indexes beyond int range are not meaningful in FSH). */
  private static int parseIntJs(String digits) {
    return (int) Math.min(Integer.MAX_VALUE, Double.parseDouble(digits));
  }

  /** Replaces soft indexes in rule paths with corresponding numbers. */
  public static void resolveSoftIndexing(List<? extends Rule> rules) {
    resolveSoftIndexing(rules, false);
  }

  public static void resolveSoftIndexing(List<? extends Rule> rules, boolean strict) {
    Map<String, Integer> pathMap = new LinkedHashMap<>(); // tracks the current index at a path
    Map<String, Integer> maxPathMap = new LinkedHashMap<>(); // tracks the maximum index seen at a path
    Map<String, Map<String, Integer>> caretPathMap = new LinkedHashMap<>();
    Map<String, Map<String, Integer>> maxCaretPathMap = new LinkedHashMap<>();

    // Parsing and separating rules by base name and bracket indexes
    List<List<PathPart>> parsedPaths = new ArrayList<>();
    List<List<PathPart>> parsedCaretPaths = new ArrayList<>();
    for (Rule rule : rules) {
      parsedPaths.add(parseFSHPath(rule.path, rule.sourceInfo));
      // If we have a CaretValueRule, we'll need a second round of parsing for the caret path
      parsedCaretPaths.add(rule instanceof CaretValueRule c ? parseFSHPath(c.caretPath, rule.sourceInfo) : null);
    }

    // Replacing Soft indexes with numbers
    for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
      Rule originalRule = rules.get(ruleIndex);
      List<PathPart> path = parsedPaths.get(ruleIndex);
      for (int elementIndex = 0; elementIndex < path.size(); elementIndex++) {
        PathPart element = path.get(elementIndex);
        // Add a prefix to the current element containing previously parsed rule elements
        element.prefix = assembleFSHPath(path.subList(0, elementIndex));
        try {
          if (strict) {
            convertSoftIndicesStrict(element, pathMap, maxPathMap);
          } else {
            convertSoftIndices(element, pathMap);
          }
        } catch (SoftIndexError e) {
          logger.error(e.getMessage(), originalRule.sourceInfo);
          logger.debug(Common.jsStack(e));
        }
      }
      originalRule.path = assembleFSHPath(path); // Assembling the separated rule path back into a normal string

      List<PathPart> caretPath = parsedCaretPaths.get(ruleIndex);
      if (caretPath != null) {
        for (int elementIndex = 0; elementIndex < caretPath.size(); elementIndex++) {
          PathPart element = caretPath.get(elementIndex);
          // Caret path indexes should only be resolved in the context of a specific path
          Map<String, Integer> elementCaretPathMap =
              caretPathMap.computeIfAbsent(originalRule.path, k -> new LinkedHashMap<>());
          Map<String, Integer> elementMaxCaretPathMap =
              maxCaretPathMap.computeIfAbsent(originalRule.path, k -> new LinkedHashMap<>());
          element.prefix = assembleFSHPath(caretPath.subList(0, elementIndex));
          try {
            if (strict) {
              convertSoftIndicesStrict(element, elementCaretPathMap, elementMaxCaretPathMap);
            } else {
              convertSoftIndices(element, elementCaretPathMap);
            }
          } catch (SoftIndexError e) {
            logger.error(e.getMessage(), originalRule.sourceInfo);
            logger.debug(Common.jsStack(e));
          }
        }
      }

      // If a rule is a CaretValueRule, we assemble its caretPath as well
      if (originalRule instanceof CaretValueRule c) {
        c.caretPath = assembleFSHPath(caretPath);
      }
    }
  }

  /** Result of {@link #collectValuesAtElementIdOrPath}. */
  public record CollectedValues(List<Object> values, List<String> ignoredSliceRequirements) {}

  private static final Pattern ID_PART = Pattern.compile("^([^:]+)(:([^/]+)(/.*)?)?\\z");
  private static final Pattern UPPER_START = Pattern.compile("^[A-Z]");

  /**
   * Given an id or path, collect all of the values at that path in a flattened array. This works similar to FHIRPath
   * path navigation, but has a few tweaks for our specific use case. {@code object} is an InstanceDefinition or any
   * value-model object.
   */
  public static CollectedValues collectValuesAtElementIdOrPath(String idOrPath, Object object) {
    List<String> ignoredSliceRequirements = new ArrayList<>();
    List<Object> values = new ArrayList<>();
    values.add(object);
    String[] parts = idOrPath.split("\\.", -1);
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      // Handle the initial resourceType part of the path, if applicable
      if (i == 0 && UPPER_START.matcher(part).find()) {
        Object resourceType = Common.getProp(object, "resourceType");
        if (Js.truthy(resourceType) && !part.equals(resourceType)) {
          return new CollectedValues(new ArrayList<>(), new ArrayList<>());
        } else {
          continue;
        }
      }
      // Find the base and optional slicename, ignoring reslices
      Matcher m = ID_PART.matcher(part);
      if (!m.find()) {
        throw new IllegalStateException(
            "TypeError: object null is not iterable (cannot read property Symbol(Symbol.iterator))");
      }
      String base = m.group(1);
      String slice = m.group(3);
      String reslice = m.group(4);
      // If it's a non-type slice or a reslice of a type slice, remember it
      boolean isTypeSlice = slice != null && base.endsWith("[x]");
      if (reslice != null || (slice != null && !isTypeSlice)) {
        ignoredSliceRequirements.add(String.join(".", java.util.Arrays.asList(parts).subList(0, i + 1)));
      }
      // If it's a type slice, use the slicename, else use the base
      String prop = isTypeSlice ? slice : base;
      // Now collect results of this part of the path
      List<Object> newResults = new ArrayList<>();
      for (Object obj : values) {
        if (obj == null) {
          continue;
        } else if (Common.hasProp(obj, prop)) {
          newResults.add(Common.getProp(obj, prop));
        } else if (prop.endsWith("[x]")) {
          String choiceBase = prop.substring(0, prop.length() - 3);
          String choiceProp = null;
          for (String k : Common.keysOf(obj)) {
            if (k.startsWith(choiceBase)) {
              choiceProp = k;
              break;
            }
          }
          newResults.add(choiceProp == null ? null : Common.getProp(obj, choiceProp));
        }
      }
      // lodash flatten: one level
      List<Object> flat = new ArrayList<>();
      for (Object r : newResults) {
        if (r instanceof List<?> l) flat.addAll(l);
        else flat.add(r);
      }
      values = flat;
    }
    return new CollectedValues(values, ignoredSliceRequirements);
  }
}

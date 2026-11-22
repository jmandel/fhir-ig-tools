package org.hl7.fhir.sushi.fshtypes.rules;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fshtypes.Common;
import org.hl7.fhir.sushi.fshtypes.FshCanonical;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshQuantity;
import org.hl7.fhir.sushi.fshtypes.FshRatio;
import org.hl7.fhir.sushi.fshtypes.FshReference;

// port of sushi/src/fshtypes/rules/CaretValueRule.ts (SUSHI 3.21.1)
public class CaretValueRule extends Rule implements SdRule, LrRule {
  private static final Pattern NEEDS_QUOTES = Pattern.compile("^\"|" + Common.JS_WS);

  public String caretPath;
  /** Same value types as {@link AssignmentRule#value}. */
  public Object value;
  public String rawValue;
  public boolean isInstance;
  /** True for code caret rules in a CodeSystem/ValueSet ({@code * #code ^property = value}); see pathArray. */
  public boolean isCodeCaretRule = false;
  /** For code caret rules: the "system#code" hierarchy the rule applies to. */
  public List<String> pathArray = new ArrayList<>();

  public CaretValueRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "CaretValueRule";
  }

  @Override public String toFSH() {
    String v = "undefined";
    if (value instanceof FshCanonical || value instanceof FshCode || value instanceof FshQuantity
        || value instanceof FshRatio || value instanceof FshReference) {
      v = value.toString();
    } else if (value instanceof Boolean || value instanceof Double || value instanceof BigInteger) {
      v = rawValue != null ? rawValue : Common.jsString(value);
    } else if (value instanceof String s) {
      v = isInstance ? s : "\"" + Common.fshifyString(s) + "\"";
    } else if (value instanceof InstanceDefinition inst) {
      v = Common.jsString(inst._instanceMeta.name);
    }
    String printablePath;
    if (isCodeCaretRule) {
      if (!pathArray.isEmpty()) {
        List<String> parts = new ArrayList<>();
        for (String code : pathArray) {
          List<String> splitCode = Arrays.asList(code.split("#", -1));
          String systemPart = splitCode.get(0);
          String codePart = String.join("#", splitCode.subList(1, splitCode.size()));
          if (NEEDS_QUOTES.matcher(codePart).find()) {
            parts.add(systemPart + "#\"" + Common.fshifyString(codePart) + "\"");
          } else {
            parts.add(systemPart + "#" + codePart);
          }
        }
        printablePath = String.join(" ", parts) + " ";
      } else {
        printablePath = "";
      }
    } else {
      printablePath = !path.isEmpty() ? path + " " : "";
    }
    return "* " + printablePath + "^" + caretPath + " = " + v;
  }
}

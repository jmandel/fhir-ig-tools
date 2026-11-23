package org.hl7.fhir.sushi.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.Common;

// port of title-case 3.0.3 (node_modules/title-case/dist/index.js), used for IG page titles
public final class TitleCase {
  private TitleCase() {}

  private static final Pattern SMALL_WORDS = Pattern.compile(
      "\\b(?:an?d?|a[st]|because|but|by|en|for|i[fn]|neither|nor|o[fnr]|only|over|per|so|some|tha[tn]|the|to|up|upon|vs?\\.?|versus|via|when|with|without|yet)\\b",
      Pattern.CASE_INSENSITIVE);
  // JS /[^\s:–—-]+|./g ; JS `.` excludes \n \r     (all inside JS \s anyway)
  private static final Pattern TOKENS = Pattern.compile("[^" + Common.JS_WS.substring(1, Common.JS_WS.length() - 1)
      + ":–—-]+|[^\\n\\r\\u2028\\u2029]");
  private static final Pattern WHITESPACE = Pattern.compile(Common.JS_WS);
  private static final Pattern IS_MANUAL_CASE = Pattern.compile("[^\\n\\r\\u2028\\u2029](?=[A-Z]|\\.[^\\n\\r\\u2028\\u2029])");
  private static final Pattern ALPHANUMERIC = Pattern.compile("[A-Za-z0-9\\u00C0-\\u00FF]");

  public static String titleCase(String input) {
    StringBuilder result = new StringBuilder();
    Matcher m = TOKENS.matcher(input);
    while (m.find()) {
      String token = m.group();
      int index = m.start();
      int after = index + token.length();
      boolean urlLike = after < input.length() && input.charAt(after) == ':'
          && !(after + 1 < input.length() && WHITESPACE.matcher(String.valueOf(input.charAt(after + 1))).matches());
      if (!IS_MANUAL_CASE.matcher(token).find()
          && (!SMALL_WORDS.matcher(token).find() || index == 0 || after == input.length())
          && !urlLike) {
        Matcher a = ALPHANUMERIC.matcher(token);
        if (a.find()) {
          token = token.substring(0, a.start()) + a.group().toUpperCase(Locale.ROOT) + token.substring(a.end());
        }
      }
      result.append(token);
    }
    return result.toString();
  }
}

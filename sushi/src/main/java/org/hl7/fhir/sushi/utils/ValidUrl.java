package org.hl7.fhir.sushi.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// port of valid-url 1.0.9 is_iri/isUri (node_modules/valid-url/index.js); SUSHI only uses isUri
public final class ValidUrl {
  private ValidUrl() {}

  private static final Pattern ILLEGAL = Pattern.compile("[^a-z0-9:/?#\\[\\]@!$&'()*+,;=.\\-_~%]", Pattern.CASE_INSENSITIVE);
  private static final Pattern BAD_HEX1 = Pattern.compile("%[^0-9a-f]", Pattern.CASE_INSENSITIVE);
  // JS /%[0-9a-f](:?[^0-9a-f]|$)/i -- note the original's "(:?" typo (an optional colon), and JS $ = end of input
  private static final Pattern BAD_HEX2 = Pattern.compile("%[0-9a-f](:?[^0-9a-f]|\\z)", Pattern.CASE_INSENSITIVE);
  private static final Pattern SPLIT =
      Pattern.compile("(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#([\\s\\S]*))?");
  private static final Pattern SCHEME = Pattern.compile("^[a-z][a-z0-9+\\-.]*\\z");

  /** isUri(value): the re-assembled URI, or null (TS undefined) when invalid. */
  public static String isUri(String value) {
    if (value == null || value.isEmpty()) return null;
    if (ILLEGAL.matcher(value).find()) return null;
    if (BAD_HEX1.matcher(value).find()) return null;
    if (BAD_HEX2.matcher(value).find()) return null;
    Matcher m = SPLIT.matcher(value);
    if (!m.lookingAt()) return null;
    String scheme = m.group(1), authority = m.group(2), path = m.group(3), query = m.group(4), fragment = m.group(5);
    if (scheme == null || scheme.isEmpty()) return null;
    if (authority != null && !authority.isEmpty()) {
      if (!(path.isEmpty() || path.startsWith("/"))) return null;
    } else if (path.startsWith("//")) {
      return null;
    }
    if (!SCHEME.matcher(scheme.toLowerCase(Locale.ROOT)).find()) return null;
    StringBuilder out = new StringBuilder(scheme).append(':');
    if (authority != null && !authority.isEmpty()) out.append("//").append(authority);
    out.append(path);
    if (query != null && !query.isEmpty()) out.append('?').append(query);
    if (fragment != null && !fragment.isEmpty()) out.append('#').append(fragment);
    return out.toString();
  }
}

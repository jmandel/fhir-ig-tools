package org.hl7.fhir.jekyll.rouge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Port of rouge-4.7.0/lib/rouge/text_analyzer.rb (a String with shebang/doctype helpers). */
public final class TextAnalyzer {
  public final String text;
  private boolean shebangDone, doctypeDone;
  private String shebang, doctype;

  private static final Pattern SHEBANG = RRegex.compile("\\A\\s*#!(.*)$", "");
  private static final Pattern DOCTYPE = RRegex.compile("\\A\\s*\n"
      + "        (?:<\\?.*?\\?>\\s*)? # possible <?xml...?> tag\n"
      + "        <!DOCTYPE\\s+(.+?)>\n"
      + "      ", "xm");

  public TextAnalyzer(String text) {
    this.text = text;
  }

  /** Ruby <code>text =~ re</code> (unanchored search) */
  public boolean matches(Pattern re) {
    return re.matcher(text).find();
  }

  public boolean startsWith(String... prefixes) {
    for (String p : prefixes) {
      if (text.startsWith(p)) {
        return true;
      }
    }
    return false;
  }

  public String shebang() {
    if (!shebangDone) {
      shebangDone = true;
      Matcher m = SHEBANG.matcher(text);
      shebang = m.find() ? m.group(1) : null;
    }
    return shebang;
  }

  /** shebang?(match) with a String: <code>/\b#{match}(\s|$)/ === shebang</code> */
  public boolean shebangP(String match) {
    return shebangRe(match);
  }

  /** shebang?(match) with a Regexp: interpolated via Regexp#to_s */
  public boolean shebangP(String reSrc, String flags) {
    return shebangRe(RRegex.toS(reSrc, flags));
  }

  private boolean shebangRe(String inner) {
    String sb = shebang();
    if (sb == null) {
      return false;
    }
    return RRegex.compile("\\b" + inner + "(\\s|$)", "").matcher(sb).find();
  }

  public String doctype() {
    if (!doctypeDone) {
      doctypeDone = true;
      Matcher m = DOCTYPE.matcher(text);
      doctype = m.find() ? m.group(1) : null;
    }
    return doctype;
  }

  /** doctype?() : <code>// === doctype</code> */
  public boolean doctypeP() {
    return doctype() != null;
  }

  /** doctype?(re) : <code>re === doctype</code> */
  public boolean doctypeP(Pattern re) {
    String d = doctype();
    return d != null && re.matcher(d).find();
  }
}

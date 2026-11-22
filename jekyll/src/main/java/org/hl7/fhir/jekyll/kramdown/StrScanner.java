package org.hl7.fhir.jekyll.kramdown;

import java.util.IdentityHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of Ruby's ::StringScanner (strscan, default non-fixed-anchor mode) plus
 * kramdown-2.5.2/lib/kramdown/utils/string_scanner.rb (line number tracking).
 *
 * <p>Semantics mirrored from strscan: every match is anchored at the current position, and the
 * regex sees the string as if it started at the position ({@code ^} and {@code \A} match there,
 * look-behind cannot see before it). This is exactly a Java {@link Matcher} region with anchoring
 * and opaque bounds. Positions are char indices (Ruby uses byte offsets, but kramdown only uses
 * positions relative to the same scanner, so char offsets are equivalent). After a failed match
 * the match data is cleared, as in Ruby.
 */
public final class StrScanner {
  private final String string;
  private final int len;
  private int pos;
  private final int startLineNumber;
  private int previousPos;
  private int previousLineNumber;

  // last match data
  private Matcher last;
  private int lastPrevPos; // position the last match started scanning from

  private final IdentityHashMap<Pattern, Matcher> matchers = new IdentityHashMap<>();

  public StrScanner(String string) {
    this(string, 1);
  }

  public StrScanner(String string, Integer startLineNumber) {
    this.string = string;
    this.len = string.length();
    this.startLineNumber = startLineNumber == null ? 1 : startLineNumber;
    this.previousPos = 0;
    this.previousLineNumber = this.startLineNumber;
  }

  public String string() {
    return string;
  }

  public int pos() {
    return pos;
  }

  /** kramdown's StringScanner#pos= (resets line-number cache when moving backwards). */
  public void setPos(int p) {
    if (this.pos > p) {
      previousLineNumber = startLineNumber;
      previousPos = 0;
    }
    if (p < 0 || p > len) {
      throw new IndexOutOfBoundsException("index out of range");
    }
    this.pos = p;
  }

  public void addPos(int delta) {
    setPos(pos + delta);
  }

  public int startLineNumber() {
    return startLineNumber;
  }

  public int[] savePos() {
    return new int[] {pos, previousPos, previousLineNumber};
  }

  public void revertPos(int[] data) {
    setPos(data[0]);
    previousPos = data[1];
    previousLineNumber = data[2];
  }

  public int currentLineNumber() {
    // Ruby: strscan = ::StringScanner.new(string); strscan.pos = @previous_pos; old_pos = pos + 1
    //       @previous_line_number += 1 while strscan.skip_until(/\n/) && strscan.pos <= old_pos
    int p = previousPos;
    int oldPos = pos + 1;
    while (true) {
      int nl = nextNewline(p);
      if (nl < 0) {
        break;
      }
      p = nl + 1;
      if (p <= oldPos) {
        previousLineNumber++;
      } else {
        break;
      }
    }
    previousPos = eos() ? pos : pos + 1;
    return previousLineNumber;
  }

  // Cache for nextNewline: the first '\n' at or after nlFrom is nlAt (-1 = none). Valid for any
  // p >= nlFrom with p <= nlAt (or nlAt == -1), which avoids rescanning a long newline-free tail
  // on every call (Ruby rescans; output is identical).
  private int nlFrom = Integer.MAX_VALUE;
  private int nlAt = -1;

  private int nextNewline(int p) {
    if (p >= nlFrom && (nlAt < 0 || p <= nlAt)) {
      return nlAt;
    }
    nlFrom = p;
    nlAt = string.indexOf('\n', p);
    return nlAt;
  }

  /** Anchored match of p at index `at` with the same region semantics as check/matches, without touching scanner state. */
  public boolean matchesAt(Pattern p, int at) {
    Matcher m = p.matcher(string);
    m.useAnchoringBounds(true);
    m.useTransparentBounds(false);
    m.region(at, len);
    return m.lookingAt();
  }

  public int length() {
    return len;
  }

  /** End index of pre_match (the start of the last match, or of the last getch), or -1 if none. */
  public int preMatchEnd() {
    if (last == null) {
      return lastGetchStart >= 0 ? lastGetchStart : -1;
    }
    return last.start();
  }

  public char charAt(int i) {
    return string.charAt(i);
  }

  public boolean eos() {
    return pos >= len;
  }

  private Matcher matcher(Pattern p) {
    Matcher m = matchers.get(p);
    if (m == null) {
      m = p.matcher(string);
      m.useAnchoringBounds(true);
      m.useTransparentBounds(false);
      matchers.put(p, m);
    }
    m.region(pos, len);
    return m;
  }

  private boolean doMatch(Pattern p, boolean anchored) {
    Matcher m = matcher(p);
    boolean ok = anchored ? m.lookingAt() : m.find();
    lastPrevPos = pos;
    lastGetchStart = -1;
    last = ok ? m : null;
    return ok;
  }

  /** Ruby scan: anchored match; advances; returns matched string or null. */
  public String scan(Pattern p) {
    if (!doMatch(p, true)) {
      return null;
    }
    pos = last.end();
    return last.group();
  }

  /** Ruby check: anchored match without advancing. */
  public String check(Pattern p) {
    if (!doMatch(p, true)) {
      return null;
    }
    return last.group();
  }

  /** Ruby match?: anchored match without advancing, returns the length or -1. */
  public int matchP(Pattern p) {
    if (!doMatch(p, true)) {
      return -1;
    }
    return last.end() - last.start();
  }

  public boolean matches(Pattern p) {
    return matchP(p) >= 0;
  }

  /** Ruby skip: anchored match; advances; returns length or -1. */
  public int skip(Pattern p) {
    if (!doMatch(p, true)) {
      return -1;
    }
    int l = last.end() - pos;
    pos = last.end();
    return l;
  }

  /** Ruby scan_until: search forward; returns string from pos to match end and advances. */
  public String scanUntil(Pattern p) {
    if (!doMatch(p, false)) {
      return null;
    }
    String res = string.substring(pos, last.end());
    pos = last.end();
    return res;
  }

  /** Ruby check_until. */
  public String checkUntil(Pattern p) {
    if (!doMatch(p, false)) {
      return null;
    }
    return string.substring(pos, last.end());
  }

  /** Ruby getch: one character (code point). */
  public String getch() {
    if (eos()) {
      last = null;
      return null;
    }
    int cp = string.codePointAt(pos);
    int n = Character.charCount(cp);
    String r = string.substring(pos, pos + n);
    lastPrevPos = pos;
    // Ruby getch sets match data to the char; emulate with a pseudo matcher
    last = null;
    lastGetchStart = pos;
    lastGetchEnd = pos + n;
    pos += n;
    return r;
  }

  private int lastGetchStart = -1;
  private int lastGetchEnd = -1;

  public String rest() {
    return string.substring(pos);
  }

  public void terminate() {
    pos = len;
    last = null;
  }

  /** Ruby @src[n]; null when the group did not participate or there is no match. */
  public String group(int n) {
    if (last == null) {
      if (n == 0 && lastGetchStart >= 0) {
        return string.substring(lastGetchStart, lastGetchEnd);
      }
      return null;
    }
    return last.group(n);
  }

  public String group(String name) {
    if (last == null) {
      return null;
    }
    return last.group(name);
  }

  public String matched() {
    if (last == null) {
      if (lastGetchStart >= 0) {
        return string.substring(lastGetchStart, lastGetchEnd);
      }
      return null;
    }
    return last.group();
  }

  public int matchedSize() {
    if (last == null) {
      if (lastGetchStart >= 0) {
        return lastGetchEnd - lastGetchStart;
      }
      return 0;
    }
    return last.end() - last.start();
  }

  /** Ruby pre_match: string from the beginning up to the start of the last match. */
  public String preMatch() {
    if (last == null) {
      if (lastGetchStart >= 0) {
        return string.substring(0, lastGetchStart);
      }
      return null;
    }
    return string.substring(0, last.start());
  }

  /** Start of the last match (char index) */
  public int matchStart() {
    return last.start();
  }

  /** Extracts string[from, to) (kramdown's extract_string(range, strscan)). */
  public String extract(int from, int to) {
    if (from < 0) { // Ruby negative range start counts from the end
      from += len;
      if (from < 0 || from > to) {
        return from < 0 ? null : "";
      }
    }
    if (from > to) {
      return "";
    }
    return string.substring(from, to);
  }
}

package org.hl7.fhir.jekyll.liquid;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.Locale;

/** Ruby's {@code Time#strftime} (strftime.c): flags {@code -_0^#}, width, colons for %z. */
final class Strftime {
  private Strftime() {}

  private static final String[] DAYS = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
  private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

  static String format(RubyTime t, String fmt) {
    StringBuilder out = new StringBuilder();
    LocalDateTime w = t.wall();
    int n = fmt.length();
    for (int i = 0; i < n; i++) {
      char c = fmt.charAt(i);
      if (c != '%') {
        out.append(c);
        continue;
      }
      int start = i;
      boolean noPad = false, upcase = false, chcase = false;
      char pad = 0;
      int width = -1;
      int colons = 0;
      i++;
      // flags
      while (i < n && "-_0^#".indexOf(fmt.charAt(i)) >= 0) {
        char f = fmt.charAt(i++);
        switch (f) {
          case '-': noPad = true; break;
          case '_': pad = ' '; break;
          case '0': pad = '0'; break;
          case '^': upcase = true; break;
          case '#': chcase = true; break;
          default: break;
        }
      }
      while (i < n && Character.isDigit(fmt.charAt(i))) {
        width = (width < 0 ? 0 : width * 10) + (fmt.charAt(i++) - '0');
      }
      while (i < n && fmt.charAt(i) == ':') {
        colons++;
        i++;
      }
      if (i < n && (fmt.charAt(i) == 'E' || fmt.charAt(i) == 'O') && i + 1 < n) {
        char mod = fmt.charAt(i);
        char nx = fmt.charAt(i + 1);
        if (mod == 'E' && "cCxXyY".indexOf(nx) >= 0 || mod == 'O' && "deHImMSuUVwWy".indexOf(nx) >= 0) {
          i++;
        }
      }
      if (i >= n) {
        throw RubyError.argumentError("invalid format: " + fmt);
      }
      char conv = fmt.charAt(i);
      String s;
      boolean text = false;
      if (colons > 0 && conv != 'z') {
        out.append(fmt, start, i + 1);
        continue;
      }
      switch (conv) {
        case 'Y': s = num(w.getYear(), 4, '0', pad, noPad, width); break;
        case 'C': s = num(Math.floorDiv(w.getYear(), 100), 2, '0', pad, noPad, width); break;
        case 'y': s = num(Math.floorMod(w.getYear(), 100), 2, '0', pad, noPad, width); break;
        case 'm': s = num(w.getMonthValue(), 2, '0', pad, noPad, width); break;
        case 'd': s = num(w.getDayOfMonth(), 2, '0', pad, noPad, width); break;
        case 'e': s = num(w.getDayOfMonth(), 2, ' ', pad, noPad, width); break;
        case 'j': s = num(w.getDayOfYear(), 3, '0', pad, noPad, width); break;
        case 'H': s = num(w.getHour(), 2, '0', pad, noPad, width); break;
        case 'k': s = num(w.getHour(), 2, ' ', pad, noPad, width); break;
        case 'I': s = num(hour12(w), 2, '0', pad, noPad, width); break;
        case 'l': s = num(hour12(w), 2, ' ', pad, noPad, width); break;
        case 'M': s = num(w.getMinute(), 2, '0', pad, noPad, width); break;
        case 'S': s = num(w.getSecond(), 2, '0', pad, noPad, width); break;
        case 'L':
        case 'N': {
          int digits = width > 0 ? width : (conv == 'L' ? 3 : 9);
          String ns = String.format(java.util.Locale.ROOT, "%09d", t.nano());
          StringBuilder b = new StringBuilder(ns.length() >= digits ? ns.substring(0, digits) : ns);
          while (b.length() < digits) {
            b.append('0');
          }
          out.append(b);
          continue;
        }
        case 'z': {
          int off = t.offset().getTotalSeconds();
          char sign = off < 0 ? '-' : '+';
          int a = Math.abs(off);
          int hh = a / 3600, mm = a / 60 % 60, ss = a % 60;
          String body;
          if (colons == 0) {
            body = String.format(java.util.Locale.ROOT, "%02d%02d", hh, mm);
          } else if (colons == 1) {
            body = String.format(java.util.Locale.ROOT, "%02d:%02d", hh, mm);
          } else if (colons == 2) {
            body = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hh, mm, ss);
          } else {
            out.append(fmt, start, i + 1);
            continue;
          }
          s = sign + body;
          if (width > s.length() && !noPad) {
            char pc = pad == ' ' ? ' ' : '0';
            StringBuilder b = new StringBuilder();
            if (pc == ' ') {
              for (int k = s.length(); k < width; k++) {
                b.append(' ');
              }
              b.append(s);
            } else {
              b.append(sign);
              for (int k = s.length(); k < width; k++) {
                b.append('0');
              }
              b.append(body);
            }
            s = b.toString();
          }
          out.append(s);
          continue;
        }
        case 'Z': s = t.zone() == null ? "" : t.zone(); text = true; if (chcase) { s = s.toLowerCase(Locale.ROOT); chcase = false; } break;
        case 'A': s = DAYS[w.getDayOfWeek().getValue() % 7]; text = true; break;
        case 'a': s = DAYS[w.getDayOfWeek().getValue() % 7].substring(0, 3); text = true; break;
        case 'B': s = MONTHS[w.getMonthValue() - 1]; text = true; break;
        case 'b':
        case 'h': s = MONTHS[w.getMonthValue() - 1].substring(0, 3); text = true; break;
        case 'p': s = w.getHour() < 12 ? "AM" : "PM"; text = true; if (chcase) { s = s.toLowerCase(Locale.ROOT); chcase = false; } break;
        case 'P': s = w.getHour() < 12 ? "am" : "pm"; text = true; break;
        case 'u': s = num(w.getDayOfWeek().getValue(), 1, '0', pad, noPad, width); break;
        case 'w': s = num(w.getDayOfWeek().getValue() % 7, 1, '0', pad, noPad, width); break;
        case 'U': {
          int wday = w.getDayOfWeek().getValue() % 7;
          s = num((w.getDayOfYear() - 1 + 7 - wday) / 7, 2, '0', pad, noPad, width);
          break;
        }
        case 'W': {
          int wday = (w.getDayOfWeek().getValue() + 6) % 7;
          s = num((w.getDayOfYear() - 1 + 7 - wday) / 7, 2, '0', pad, noPad, width);
          break;
        }
        case 'G': s = num(w.get(IsoFields.WEEK_BASED_YEAR), 4, '0', pad, noPad, width); break;
        case 'g': s = num(Math.floorMod(w.get(IsoFields.WEEK_BASED_YEAR), 100), 2, '0', pad, noPad, width); break;
        case 'V': s = num(w.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), 2, '0', pad, noPad, width); break;
        case 's': s = num(t.epochSecond(), 1, '0', pad, noPad, width); break;
        case 'n': s = "\n"; text = true; break;
        case 't': s = "\t"; text = true; break;
        case '%': s = "%"; text = true; break;
        case 'c': s = format(t, "%a %b %e %H:%M:%S %Y"); text = true; break;
        case 'x':
        case 'D': s = format(t, "%m/%d/%y"); text = true; break;
        case 'X':
        case 'T': s = format(t, "%H:%M:%S"); text = true; break;
        case 'F': s = format(t, "%Y-%m-%d"); text = true; break;
        case 'R': s = format(t, "%H:%M"); text = true; break;
        case 'r': s = format(t, "%I:%M:%S %p"); text = true; break;
        case 'v': s = format(t, "%e-%^b-%4Y"); text = true; break;
        default:
          // unknown conversion: copied literally
          out.append(fmt, start, i + 1);
          continue;
      }
      if (upcase || chcase) {
        s = s.toUpperCase(Locale.ROOT);
      }
      if (text && width > s.length() && !noPad) {
        char pc = pad == 0 ? ' ' : pad;
        for (int k = s.length(); k < width; k++) {
          out.append(pc);
        }
      }
      out.append(s);
    }
    return out.toString();
  }

  private static int hour12(LocalDateTime w) {
    int h = w.getHour() % 12;
    return h == 0 ? 12 : h;
  }

  private static String num(long v, int natural, char defPad, char pad, boolean noPad, int width) {
    String digits = Long.toString(Math.abs(v));
    String sign = v < 0 ? "-" : "";
    if (noPad) {
      return sign + digits;
    }
    int w = width >= 0 ? width : natural;
    char pc = pad == 0 ? defPad : pad;
    StringBuilder b = new StringBuilder();
    int len = sign.length() + digits.length();
    if (pc == '0') {
      b.append(sign);
      for (int k = len; k < w; k++) {
        b.append('0');
      }
      b.append(digits);
    } else {
      for (int k = len; k < w; k++) {
        b.append(' ');
      }
      b.append(sign).append(digits);
    }
    return b.toString();
  }
}

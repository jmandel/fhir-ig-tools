package org.hl7.fhir.sushi.utils;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;

/**
 * SUSHI's logger (port of sushi/src/utils/FSHLogger.ts, which is winston + a format pipeline). Message text, counting
 * and ignore semantics are part of the output contract (SUSHI's console output and exit status = error count).
 *
 * <p>Pipeline per message, in TS order (verified against winston 3.19 in SUSHI 3.21.1):
 * <ol>
 *   <li>ignore: a warn/error whose raw message equals an ignore-file line, or matches a {@code /regex/} line
 *       (JS RegExp.test, i.e. find anywhere), is dropped and counted in numIgnoredWarn/numIgnoredError;</li>
 *   <li>count by level, ALWAYS, even when the level is not printed (debug messages count while level=info; the
 *       "secret logger" relies on this);</li>
 *   <li>track (only while {@code errorsAndWarnings.shouldTrack}) errors/warnings with raw message + location + file;</li>
 *   <li>append location lines: "\n  File: f", "\n  Line: a[ - b]", "\n  Applied in File: f",
 *       "\n  Applied on Line: a[ - b]";</li>
 *   <li>print {@code "<level> <message>"} where the level is padded to 5 ("info ", "warn ", "error", "debug"), to
 *       stdout, if the level is enabled (npm levels: error 0, warn 1, info 2, http 3, verbose 4, debug 5, silly 6; "emerg" silences all), with chalk
 *       colors only when stdout is a TTY / FORCE_COLOR (differential runs compare uncolored output).</li>
 * </ol>
 *
 * <p>Java shape: one process-wide instance ({@link #logger}), like the TS module singleton, because SUSHI's tests and
 * code rely on global counts; the output sink is pluggable (tests install a capturing sink = TS loggerSpy).
 */
public final class FshLogger {
  public enum Level {
    error(0), warn(1), info(2), debug(5);

    final int severity;

    Level(int s) {
      severity = s;
    }

    /** "info " / "warn " / "error" / "debug" as printed. */
    String label() {
      return this == info || this == warn ? name() + " " : name();
    }
  }

  /** One emitted message as the printer saw it (after location suffixes), plus its raw parts. */
  public record LogEntry(Level level, String rawMessage, String message, SourceInfo source) {}

  public static final class Stats {
    public int numInfo, numWarn, numError, numDebug, numIgnoredWarn, numIgnoredError;

    public void reset() {
      numInfo = numWarn = numError = numDebug = numIgnoredWarn = numIgnoredError = 0;
    }

    public Stats copy() {
      Stats s = new Stats();
      s.numInfo = numInfo; s.numWarn = numWarn; s.numError = numError; s.numDebug = numDebug;
      s.numIgnoredWarn = numIgnoredWarn; s.numIgnoredError = numIgnoredError;
      return s;
    }
  }

  public record Tracked(String message, TextLocation location, String input) {}

  public static final class ErrorsAndWarnings {
    public List<Tracked> errors = new ArrayList<>();
    public List<Tracked> warnings = new ArrayList<>();
    public boolean shouldTrack;

    public void reset() {
      errors = new ArrayList<>();
      warnings = new ArrayList<>();
      shouldTrack = false;
    }

    ErrorsAndWarnings copy() {
      ErrorsAndWarnings c = new ErrorsAndWarnings();
      c.errors = new ArrayList<>(errors);
      c.warnings = new ArrayList<>(warnings);
      c.shouldTrack = shouldTrack;
      return c;
    }
  }

  /** Saved state from {@link #switchToSecretLogger()} (TS LoggerData). */
  public record LoggerData(String level, ErrorsAndWarnings errorsAndWarnings, Stats stats) {}

  /** The process-wide logger (TS `logger` export). */
  public static final FshLogger logger = new FshLogger();

  public final Stats stats = new Stats();
  public final ErrorsAndWarnings errorsAndWarnings = new ErrorsAndWarnings();
  private String level = "info";
  private List<Object> ignoredWarnings = List.of(); // String or Pattern
  private List<Object> ignoredErrors = List.of();
  private Consumer<LogEntry> sink = consoleSink(new PrintStream(System.out, true, StandardCharsets.UTF_8), false);
  private final List<Consumer<LogEntry>> taps = new java.util.concurrent.CopyOnWriteArrayList<>();

  private FshLogger() {}

  // ------------------------------------------------------------------------------------------------ API

  public void error(String message) { log(Level.error, message, null); }
  public void error(String message, SourceInfo source) { log(Level.error, message, source); }
  public void warn(String message) { log(Level.warn, message, null); }
  public void warn(String message, SourceInfo source) { log(Level.warn, message, source); }
  public void info(String message) { log(Level.info, message, null); }
  public void info(String message, SourceInfo source) { log(Level.info, message, source); }
  public void debug(String message) { log(Level.debug, message, null); }
  public void debug(String message, SourceInfo source) { log(Level.debug, message, source); }

  /** TS logMessage(level, message). */
  public void logMessage(String level, String message) {
    log(Level.valueOf(level), message, null);
  }

  public synchronized void log(Level lvl, String message, SourceInfo src) {
    // 1. ignore
    List<Object> ignored = lvl == Level.warn ? ignoredWarnings : lvl == Level.error ? ignoredErrors : List.of();
    for (Object m : ignored) {
      boolean hit = m instanceof Pattern p ? p.matcher(message).find() : m.equals(message);
      if (hit) {
        if (lvl == Level.warn) stats.numIgnoredWarn++;
        else stats.numIgnoredError++;
        return;
      }
    }
    // 2. count
    switch (lvl) {
      case info -> stats.numInfo++;
      case warn -> stats.numWarn++;
      case error -> stats.numError++;
      case debug -> stats.numDebug++;
    }
    // 3. track
    if (errorsAndWarnings.shouldTrack && (lvl == Level.error || lvl == Level.warn)) {
      Tracked t = new Tracked(message, src == null ? null : src.location, src == null ? null : src.file);
      (lvl == Level.error ? errorsAndWarnings.errors : errorsAndWarnings.warnings).add(t);
    }
    // 4. location suffixes
    StringBuilder full = new StringBuilder(message);
    if (src != null) {
      if (src.file != null && !src.file.isEmpty()) full.append("\n  File: ").append(src.file);
      appendLines(full, "\n  Line: ", src.location);
      if (src.appliedFile != null && !src.appliedFile.isEmpty()) full.append("\n  Applied in File: ").append(src.appliedFile);
      appendLines(full, "\n  Applied on Line: ", src.appliedLocation);
    }
    // 5. print
    LogEntry entry = new LogEntry(lvl, message, full.toString(), src);
    // taps see every non-ignored message regardless of level, like winston's transport.write (which TS loggerSpy spies on)
    for (Consumer<LogEntry> t : taps) t.accept(entry);
    if (enabled(lvl)) sink.accept(entry);
  }

  private static void appendLines(StringBuilder sb, String prefix, TextLocation loc) {
    if (loc == null) return;
    sb.append(prefix).append(loc.startLine);
    if (loc.endLine != loc.startLine) sb.append(" - ").append(loc.endLine);
  }

  /**
   * winston's default (npm) levels: error 0, warn 1, info 2, http 3, verbose 4, debug 5, silly 6. A message prints
   * when its level is at or below the logger level; "emerg"/"silent"/unknown print nothing (SUSHI's secret logger
   * uses "emerg").
   */
  private boolean enabled(Level lvl) {
    int max = switch (level) {
      case "error" -> 0;
      case "warn" -> 1;
      case "info" -> 2;
      case "http" -> 3;
      case "verbose" -> 4;
      case "debug" -> 5;
      case "silly" -> 6;
      default -> -1;
    };
    return lvl.severity <= max;
  }

  // ------------------------------------------------------------------------------------------------ config

  public String level() { return level; }

  public void setLevel(String level) { this.level = level; }

  /** TS setIgnoredWarnings(fileContents). */
  public void setIgnoredWarnings(String config) { ignoredWarnings = parseIgnoredLogsConfiguration(config); }

  /** TS setIgnoredErrors(fileContents). */
  public void setIgnoredErrors(String config) { ignoredErrors = parseIgnoredLogsConfiguration(config); }

  static List<Object> parseIgnoredLogsConfiguration(String config) {
    List<Object> out = new ArrayList<>();
    for (String line : config.split("\r?\n", -1)) {
      String m = org.hl7.fhir.sushi.js.Js.trim(line);
      if (m.isEmpty() || m.startsWith("#")) continue;
      if (m.startsWith("/") && m.endsWith("/") && m.length() >= 2) {
        // JS RegExp source; most ignore patterns are plain. JsRegex (WP-1) owns full JS->Java regex translation.
        out.add(Pattern.compile(m.substring(1, m.length() - 1)));
      } else {
        out.add(m);
      }
    }
    return out;
  }

  /** Replace the output sink; returns the previous one. Tests: capture entries (TS loggerSpy). */
  public synchronized Consumer<LogEntry> setSink(Consumer<LogEntry> s) {
    Consumer<LogEntry> old = sink;
    sink = s;
    return old;
  }

  /**
   * Observe every message that passes the ignore filter, at ANY level (debug included, also while the secret logger
   * silences output), with location suffixes appended: exactly what TS tests see through loggerSpy.
   */
  public void addTap(Consumer<LogEntry> tap) {
    taps.add(tap);
  }

  public void removeTap(Consumer<LogEntry> tap) {
    taps.remove(tap);
  }

  private volatile boolean colorEnabled;

  /**
   * Whether chalk would colour output (TS: chalk's level > 0, i.e. stdout is a TTY or FORCE_COLOR). One process-wide
   * flag for every chalk use: the level labels here, the summary box (App), and the boxed messages in
   * StructureDefinitionExporter/InstanceExporter. App sets it once at startup together with the console sink;
   * default false (as in tests and differential runs, where stdout is not a TTY).
   */
  public boolean colorEnabled() {
    return colorEnabled;
  }

  public void setColorEnabled(boolean enabled) {
    colorEnabled = enabled;
  }

  /** winston Console transport + SUSHI's printer: {@code "<level> <message>"} on stdout. */
  public static Consumer<LogEntry> consoleSink(PrintStream out, boolean color) {
    return e -> out.println((color ? colorLabel(e.level()) : e.level().label()) + " " + e.message());
  }

  /** chalk (level 3 / truecolor) styling used by SUSHI: whiteBright on green/orange(179,98,0)/red/blue. */
  static String colorLabel(Level l) {
    String bg = switch (l) {
      case info -> "\u001b[42m";
      case warn -> "\u001b[48;2;179;98;0m";
      case error -> "\u001b[41m";
      case debug -> "\u001b[44m";
    };
    return "\u001b[97m" + bg + l.label() + "\u001b[49m\u001b[39m";
  }

  /** Clears counts, tracking and ignore lists (test isolation; TS tests reset stats in beforeEach). */
  public synchronized void resetForTest() {
    stats.reset();
    errorsAndWarnings.reset();
    ignoredWarnings = List.of();
    ignoredErrors = List.of();
    level = "info";
  }

  // ------------------------------------------------------------------------------------------------ secret logger

  /** TS switchToSecretLogger: silence output, save + reset stats and tracking, start tracking. */
  public synchronized LoggerData switchToSecretLogger() {
    String oldLevel = level;
    level = "emerg";
    ErrorsAndWarnings oldEw = errorsAndWarnings.copy();
    errorsAndWarnings.reset();
    errorsAndWarnings.shouldTrack = true;
    Stats oldStats = stats.copy();
    stats.reset();
    return new LoggerData(oldLevel, oldEw, oldStats);
  }

  /** TS restoreMainLogger: restore saved state, return what was tracked while secret. */
  public synchronized ErrorsAndWarnings restoreMainLogger(LoggerData d) {
    level = d.level();
    ErrorsAndWarnings secret = errorsAndWarnings.copy();
    errorsAndWarnings.errors = d.errorsAndWarnings().errors;
    errorsAndWarnings.warnings = d.errorsAndWarnings().warnings;
    errorsAndWarnings.shouldTrack = d.errorsAndWarnings().shouldTrack;
    Stats s = d.stats();
    stats.numInfo = s.numInfo; stats.numWarn = s.numWarn; stats.numError = s.numError; stats.numDebug = s.numDebug;
    stats.numIgnoredWarn = s.numIgnoredWarn; stats.numIgnoredError = s.numIgnoredError;
    return secret;
  }
}

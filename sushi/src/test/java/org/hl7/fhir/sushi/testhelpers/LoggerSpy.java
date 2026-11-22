package org.hl7.fhir.sushi.testhelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.FshLogger.LogEntry;

// port of sushi/test/testhelpers/loggerSpy.ts (SUSHI 3.21.1)
/**
 * Captures every log entry the way the TS spy on winston's transport.write does: all levels (debug too, and while the
 * secret logger is active), after the ignore filter, with "\n  File: ..." / "\n  Line: ..." suffixes in the message.
 * Console output is muted while tests run (TS MUTE_LOGS = true).
 *
 * <p>Usage, mirroring TS: {@code import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;} then
 * {@code loggerSpy.reset()} in {@code @BeforeEach} and {@code loggerSpy.getLastMessage("error")}. Levels filter like
 * TS ({@code RegExp(level).test(entry.level)}). Indexes may be negative (from the end). Missing entries give null.
 * Call {@link FshLogger#resetForTest()} yourself where TS resets stats.
 */
public final class LoggerSpy {
  public static final LoggerSpy loggerSpy = new LoggerSpy();

  private final List<LogEntry> logs = new ArrayList<>();

  private LoggerSpy() {
    FshLogger.logger.setSink(e -> {}); // MUTE_LOGS
    FshLogger.logger.addTap(this::record);
  }

  private synchronized void record(LogEntry e) {
    logs.add(e);
  }

  /** A captured entry: TS LogEntry has level + message (message includes location suffixes). */
  public record Log(String level, String message) {}

  public synchronized List<Log> getAllLogs() {
    return getAllLogs(null);
  }

  public synchronized List<Log> getAllLogs(String level) {
    List<Log> out = new ArrayList<>();
    Pattern p = level == null ? null : Pattern.compile(level);
    for (LogEntry e : logs) {
      if (p == null || p.matcher(e.level().name()).find()) out.add(new Log(e.level().name(), e.message()));
    }
    return out;
  }

  public Log getLogAtIndex(int index) {
    return getLogAtIndex(index, null);
  }

  public Log getLogAtIndex(int index, String level) {
    List<Log> l = getAllLogs(level);
    int i = index < 0 ? l.size() + index : index;
    return i >= 0 && i < l.size() ? l.get(i) : null;
  }

  public Log getFirstLog() { return getLogAtIndex(0, null); }
  public Log getFirstLog(String level) { return getLogAtIndex(0, level); }
  public Log getLastLog() { return getLogAtIndex(-1, null); }
  public Log getLastLog(String level) { return getLogAtIndex(-1, level); }

  public List<String> getAllMessages() { return getAllMessages(null); }

  public List<String> getAllMessages(String level) {
    return getAllLogs(level).stream().map(Log::message).toList();
  }

  public String getMessageAtIndex(int index) { return getMessageAtIndex(index, null); }

  public String getMessageAtIndex(int index, String level) {
    Log l = getLogAtIndex(index, level);
    return l == null ? null : l.message();
  }

  public String getFirstMessage() { return getMessageAtIndex(0, null); }
  public String getFirstMessage(String level) { return getMessageAtIndex(0, level); }
  public String getLastMessage() { return getMessageAtIndex(-1, null); }
  public String getLastMessage(String level) { return getMessageAtIndex(-1, level); }

  public synchronized void reset() {
    logs.clear();
  }
}

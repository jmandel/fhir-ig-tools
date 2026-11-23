package org.hl7.fhir.sushi.utils;

import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.*;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirtypes.*;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.common.js.*;
import org.hl7.fhir.sushi.testhelpers.*;
import org.hl7.fhir.sushi.utils.*;
import org.junit.jupiter.api.*;

// port of sushi/test/utils/FSHLogger.test.ts (SUSHI 3.21.1)
@DisplayName("FSHLogger")
class FshLoggerTest {
  // MUTE_LOGS controls whether or not logs get printed during testing.
  static final boolean MUTE_LOGS = true;
  static final FshLogger logger = FshLogger.logger;
  static final FshLogger.Stats stats = logger.stats;
  static final FshLogger.ErrorsAndWarnings errorsAndWarnings = logger.errorsAndWarnings;
  java.util.function.Consumer<FshLogger.LogEntry> originalWriteFn;

  /** test/utils/leftAlign.ts */
  static String leftAlign(String input) {
    String[] lines = input.split("\\r?\\n", -1);
    int offset = -1;
    for (String line : lines) {
      if (line.matches("(?s).*[a-zA-Z].*")) {
        offset = line.length() - line.stripLeading().length();
        break;
      }
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) sb.append('\n');
      String l = lines[i];
      sb.append(offset >= 0 && l.length() >= offset ? l.substring(offset) : l);
    }
    return sb.toString();
  }

  static void setIgnoredWarnings(String s) { logger.setIgnoredWarnings(s); }

  static void setIgnoredErrors(String s) { logger.setIgnoredErrors(s); }

  /** winston's info[MESSAGE] as the transport's write sees it: "<level padded to 5> <message>" */
  static String printed(FshLogger.LogEntry e) {
    String l = e.level().name();
    return (l.length() == 4 ? l + " " : l) + " " + e.message();
  }

  /** errorsAndWarnings entries as the TS objects ({message, location?, input?}) */
  static List<Object> tracked(List<FshLogger.Tracked> l) {
    List<Object> out = new ArrayList<>();
    for (FshLogger.Tracked t : l) {
      JsObject o = new JsObject();
      if (t.location() != null) {
        o.put("location", new JsObject().with("startLine", (double) t.location().startLine)
            .with("startColumn", (double) t.location().startColumn).with("endLine", (double) t.location().endLine)
            .with("endColumn", (double) t.location().endColumn));
      }
      o.put("message", t.message());
      if (t.input() != null) o.put("input", t.input());
      out.add(o);
    }
    return out;
  }

  @BeforeEach
  void beforeEach() {
    stats.reset();
    errorsAndWarnings.reset();
    if (MUTE_LOGS) {
      originalWriteFn = logger.setSink(e -> {});
    }
  }

  @AfterEach
  void afterEach() {
    if (MUTE_LOGS) {
      logger.setSink(originalWriteFn);
    }
  }

  @AfterAll
  static void afterAll() {
    // TS keeps the ignore lists module-global; don't leak them into other test classes
    setIgnoredWarnings("");
    setIgnoredErrors("");
  }

  @Test
  @DisplayName("FSHLogger should store number of logger info messages")
  void shouldStoreNumberOfLoggerInfoMessages() {
    logger.info("info1");
    assertEquals(1, stats.numInfo);
    logger.info("info2");
    assertEquals(2, stats.numInfo);
  }

  @Test
  @DisplayName("FSHLogger should store number of logger warning messages")
  void shouldStoreNumberOfLoggerWarningMessages() {
    logger.warn("warn1");
    assertEquals(1, stats.numWarn);
    logger.warn("warn2");
    assertEquals(2, stats.numWarn);
  }

  @Test
  @DisplayName("FSHLogger should store number of logger error messages")
  void shouldStoreNumberOfLoggerErrorMessages() {
    logger.error("error1");
    assertEquals(1, stats.numError);
    logger.error("error2");
    assertEquals(2, stats.numError);
  }

  @Test
  @DisplayName("FSHLogger should store number of logger messages of all types simultaneously")
  void shouldStoreNumberOfLoggerMessagesOfAllTypesSimultaneously() {
    logger.error("error1");
    assertEquals(0, stats.numInfo);
    assertEquals(0, stats.numWarn);
    assertEquals(1, stats.numError);
    assertEquals(0, stats.numIgnoredWarn);
    assertEquals(0, stats.numIgnoredError);
    logger.info("info1");
    assertEquals(1, stats.numInfo);
    assertEquals(0, stats.numWarn);
    assertEquals(1, stats.numError);
    assertEquals(0, stats.numIgnoredWarn);
    assertEquals(0, stats.numIgnoredError);
    logger.warn("warn1");
    assertEquals(1, stats.numInfo);
    assertEquals(1, stats.numWarn);
    assertEquals(1, stats.numError);
    assertEquals(0, stats.numIgnoredWarn);
    assertEquals(0, stats.numIgnoredError);
    logger.info("info2");
    assertEquals(2, stats.numInfo);
    assertEquals(1, stats.numWarn);
    assertEquals(1, stats.numError);
    assertEquals(0, stats.numIgnoredWarn);
    assertEquals(0, stats.numIgnoredError);
  }

  @Test
  @DisplayName("FSHLogger should correctly reset the stats")
  void shouldCorrectlyResetTheStats() {
    logger.info("info1");
    logger.warn("warn1");
    logger.error("error1");
    assertEquals(1, stats.numInfo);
    assertEquals(1, stats.numWarn);
    assertEquals(1, stats.numError);
    assertEquals(0, stats.numIgnoredWarn);
    assertEquals(0, stats.numIgnoredError);
    stats.reset();
    assertEquals(0, stats.numInfo);
    assertEquals(0, stats.numWarn);
    assertEquals(0, stats.numError);
    assertEquals(0, stats.numIgnoredWarn);
    assertEquals(0, stats.numIgnoredError);
  }

  @Test
  @DisplayName("FSHLogger should not track errors and warnings when shouldTrack is false")
  void shouldNotTrackErrorsAndWarningsWhenShouldTrackIsFalse() {
    logger.warn("warn1");
    logger.error("error1");
    assertEquals(false, errorsAndWarnings.shouldTrack);
    assertEquals(0, len(tracked(errorsAndWarnings.errors)));
    assertEquals(0, len(tracked(errorsAndWarnings.warnings)));
  }

  @Test
  @DisplayName("FSHLogger should track errors and warnings when shouldTrack is true")
  void shouldTrackErrorsAndWarningsWhenShouldTrackIsTrue() {
    errorsAndWarnings.shouldTrack = true;
    // TS passes a plain object with a misspelled "startColumm"; Java SourceInfo/TextLocation are typed
    logger.warn("warn1", new SourceInfo("Input_0", new TextLocation(1, 2, 3, 4)));
    logger.warn("warn2");
    logger.error("error1");
    logger.error("error2");
    assertEquals(true, errorsAndWarnings.shouldTrack);
    assertEquals(2, len(tracked(errorsAndWarnings.warnings)));
    assertContainsEqual(JsAssert.json("""
        {
          "location": {
            "startLine": 1,
            "startColumn": 2,
            "endLine": 3,
            "endColumn": 4
          },
          "message": "warn1",
          "input": "Input_0"
        }
        """), tracked(errorsAndWarnings.warnings));
    assertContainsEqual(JsAssert.json("""
        {
          "message": "warn2"
        }
        """), tracked(errorsAndWarnings.warnings));
    assertEquals(2, len(tracked(errorsAndWarnings.errors)));
    assertContainsEqual(JsAssert.json("""
        {
          "message": "error1"
        }
        """), tracked(errorsAndWarnings.errors));
    assertContainsEqual(JsAssert.json("""
        {
          "message": "error2"
        }
        """), tracked(errorsAndWarnings.errors));
  }

  @Test
  @DisplayName("FSHLogger should reset errors and warnings")
  void shouldResetErrorsAndWarnings() {
    errorsAndWarnings.shouldTrack = true;
    logger.warn("warn1");
    logger.error("error1");
    assertEquals(true, errorsAndWarnings.shouldTrack);
    assertEquals(1, len(tracked(errorsAndWarnings.warnings)));
    assertContainsEqual(JsAssert.json("""
        {
          "message": "warn1"
        }
        """), tracked(errorsAndWarnings.warnings));
    assertEquals(1, len(tracked(errorsAndWarnings.errors)));
    assertContainsEqual(JsAssert.json("""
        {
          "message": "error1"
        }
        """), tracked(errorsAndWarnings.errors));
    errorsAndWarnings.reset();
    assertEquals(0, len(tracked(errorsAndWarnings.errors)));
    assertEquals(0, len(tracked(errorsAndWarnings.warnings)));
  }

  @DisplayName("#ignoreWarnings")
  @Nested
  class IgnoreWarnings {
    List<String> messages = new ArrayList<>(Arrays.asList());
    java.util.function.Consumer<FshLogger.LogEntry> logMock = e -> messages.add(printed(e));

    @BeforeEach
    void isolate() {
      // TS runs the describes in file order and never clears the other list; JUnit's order differs
      setIgnoredErrors("");
    }


    @AfterEach
    void afterEach() {
      messages.clear();
      logger.removeTap(logMock);
    }

    @Test
    @DisplayName("FSHLogger #ignoreWarnings should ignore warnings which are listed directly")
    void shouldIgnoreWarningsWhichAreListedDirectly() {
      logger.addTap(logMock);
      setIgnoredWarnings(leftAlign("\n        warn1\n        "));
      logger.warn("warn1");
      logger.error("error1");
      assertEquals(1, messages.size());
      assertMatches("error.*error1", "", messages.get(0));
      assertEquals(1, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreWarnings should ignore multiple warnings which are listed directly")
    void shouldIgnoreMultipleWarningsWhichAreListedDirectly() {
      logger.addTap(logMock);
      setIgnoredWarnings(leftAlign("\n        warn1\n        warn2\n        "));
      logger.warn("warn1");
      logger.warn("warn2");
      logger.error("error1");
      assertEquals(1, messages.size());
      assertMatches("error.*error1", "", messages.get(0));
      assertEquals(2, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreWarnings should ignore lines beginning with # when listing warnings")
    void shouldIgnoreLinesBeginningWithWhenListingWarnings() {
      logger.addTap(logMock);
      setIgnoredWarnings(leftAlign("\n        warn1\n        # warn2\n        "));
      logger.warn("warn1");
      logger.warn("warn2");
      logger.error("error1");
      assertEquals(2, messages.size());
      assertMatches("warn.*warn2", "", messages.get(0));
      assertMatches("error.*error1", "", messages.get(1));
      assertEquals(1, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreWarnings should ignore leading and trailing whitespace for warnings which are listed directly")
    void shouldIgnoreLeadingAndTrailingWhitespaceForWarningsWhichAreListedDirectly() {
      logger.addTap(logMock);
      setIgnoredWarnings(" warn1  ");
      logger.warn("warn1");
      logger.error("error1");
      assertEquals(1, messages.size());
      assertMatches("error.*error1", "", messages.get(0));
      assertEquals(1, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreWarnings should not ignore warnings which are unlisted")
    void shouldNotIgnoreWarningsWhichAreUnlisted() {
      logger.addTap(logMock);
      setIgnoredWarnings(leftAlign("\n        warn1\n        "));
      logger.warn("warn1");
      logger.warn("warn2");
      logger.error("error1");
      assertEquals(2, messages.size());
      assertMatches("warn.*warn2", "", messages.get(0));
      assertMatches("error.*error1", "", messages.get(1));
      assertEquals(1, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreWarnings should not ignore messages which are not warnings, even when listed")
    void shouldNotIgnoreMessagesWhichAreNotWarningsEvenWhenListed() {
      logger.addTap(logMock);
      setIgnoredWarnings(leftAlign("\n        foo\n        "));
      logger.error("foo");
      logger.info("foo");
      logger.debug("foo");
      assertEquals(3, messages.size());
      assertMatches("error.*foo", "", messages.get(0));
      assertMatches("info.*foo", "", messages.get(1));
      assertMatches("debug.*foo", "", messages.get(2));
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreWarnings should ignore warnings which are matched via regex")
    void shouldIgnoreWarningsWhichAreMatchedViaRegex() {
      logger.addTap(logMock);
      setIgnoredWarnings(leftAlign("\n        /ignore.*/\n        "));
      logger.warn("ignore this message");
      logger.warn("ignore this message even though\n      it goes over\n      multiple lines");
      assertEquals(0, messages.size());
      assertEquals(2, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreWarnings should ignore warnings which are matched via regex with leading or trailing whitespace")
    void shouldIgnoreWarningsWhichAreMatchedViaRegexWithLeadingOrTrailingWhitespace() {
      logger.addTap(logMock);
      setIgnoredWarnings(" /ignore.*/ ");
      logger.warn("ignore this message");
      logger.warn("ignore this message even though\n      it goes over\n      multiple lines");
      assertEquals(0, messages.size());
      assertEquals(2, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreWarnings should ignore warnings which are matched via regex and listed directly")
    void shouldIgnoreWarningsWhichAreMatchedViaRegexAndListedDirectly() {
      logger.addTap(logMock);
      setIgnoredWarnings(leftAlign("\n        /ignore.*/\n        this too\n        "));
      logger.warn("ignore this message");
      logger.warn("this too");
      assertEquals(0, messages.size());
      assertEquals(2, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreWarnings should not ignore warnings which do not match the regex")
    void shouldNotIgnoreWarningsWhichDoNotMatchTheRegex() {
      logger.addTap(logMock);
      setIgnoredWarnings(leftAlign("\n        /ignore.*/\n        "));
      logger.warn("please log this");
      assertEquals(1, messages.size());
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

  }
  @DisplayName("#ignoreErrors")
  @Nested
  class IgnoreErrors {
    List<String> messages = new ArrayList<>(Arrays.asList());
    java.util.function.Consumer<FshLogger.LogEntry> logMock = e -> messages.add(printed(e));

    @BeforeEach
    void isolate() {
      // TS runs the describes in file order and never clears the other list; JUnit's order differs
      setIgnoredWarnings("");
    }


    @AfterEach
    void afterEach() {
      messages.clear();
      logger.removeTap(logMock);
    }

    @Test
    @DisplayName("FSHLogger #ignoreErrors should ignore errors which are listed directly")
    void shouldIgnoreErrorsWhichAreListedDirectly() {
      logger.addTap(logMock);
      setIgnoredErrors(leftAlign("\n        error1\n        "));
      logger.warn("warn1");
      logger.error("error1");
      assertEquals(1, messages.size());
      assertMatches("warn.*warn1", "", messages.get(0));
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(1, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreErrors should ignore multiple errors which are listed directly")
    void shouldIgnoreMultipleErrorsWhichAreListedDirectly() {
      logger.addTap(logMock);
      setIgnoredErrors(leftAlign("\n        error1\n        error2\n        "));
      logger.error("error1");
      logger.error("error2");
      logger.warn("warn1");
      assertEquals(1, messages.size());
      assertMatches("warn.*warn1", "", messages.get(0));
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(2, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreErrors should ignore lines beginning with # when listing errors")
    void shouldIgnoreLinesBeginningWithWhenListingErrors() {
      logger.addTap(logMock);
      setIgnoredErrors(leftAlign("\n        error1\n        # error2\n        "));
      logger.error("error1");
      logger.error("error2");
      logger.warn("warn1");
      assertEquals(2, messages.size());
      assertMatches("error.*error2", "", messages.get(0));
      assertMatches("warn.*warn1", "", messages.get(1));
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(1, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreErrors should ignore leading and trailing whitespace for warnings which are listed directly")
    void shouldIgnoreLeadingAndTrailingWhitespaceForWarningsWhichAreListedDirectly() {
      logger.addTap(logMock);
      setIgnoredErrors(" error1  ");
      logger.warn("warn1");
      logger.error("error1");
      assertEquals(1, messages.size());
      assertMatches("warn.*warn1", "", messages.get(0));
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(1, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreErrors should not ignore errors which are unlisted")
    void shouldNotIgnoreErrorsWhichAreUnlisted() {
      logger.addTap(logMock);
      setIgnoredErrors(leftAlign("\n        error1\n        "));
      logger.error("error1");
      logger.error("error2");
      logger.warn("warn1");
      assertEquals(2, messages.size());
      assertMatches("error.*error2", "", messages.get(0));
      assertMatches("warn.*warn1", "", messages.get(1));
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(1, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreErrors should not ignore messages which are not errors, even when listed")
    void shouldNotIgnoreMessagesWhichAreNotErrorsEvenWhenListed() {
      logger.addTap(logMock);
      setIgnoredErrors(leftAlign("\n        foo\n        "));
      logger.warn("foo");
      logger.info("foo");
      logger.debug("foo");
      assertEquals(3, messages.size());
      assertMatches("warn.*foo", "", messages.get(0));
      assertMatches("info.*foo", "", messages.get(1));
      assertMatches("debug.*foo", "", messages.get(2));
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreErrors should ignore errors which are matched via regex")
    void shouldIgnoreErrorsWhichAreMatchedViaRegex() {
      logger.addTap(logMock);
      setIgnoredErrors(leftAlign("\n        /ignore.*/\n        "));
      logger.error("ignore this message");
      logger.error("ignore this message even though\n      it goes over\n      multiple lines");
      assertEquals(0, messages.size());
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(2, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreErrors should ignore errors which are matched via regex with leading or trailing whitespace")
    void shouldIgnoreErrorsWhichAreMatchedViaRegexWithLeadingOrTrailingWhitespace() {
      logger.addTap(logMock);
      setIgnoredErrors(" /ignore.*/ ");
      logger.error("ignore this message");
      logger.error("ignore this message even though\n      it goes over\n      multiple lines");
      assertEquals(0, messages.size());
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(2, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreErrors should ignore errors which are matched via regex and listed directly")
    void shouldIgnoreErrorsWhichAreMatchedViaRegexAndListedDirectly() {
      logger.addTap(logMock);
      setIgnoredErrors(leftAlign("\n        /ignore.*/\n        this too\n        "));
      logger.error("ignore this message");
      logger.error("this too");
      assertEquals(0, messages.size());
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(2, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreErrors should not ignore errors which do not match the regex")
    void shouldNotIgnoreErrorsWhichDoNotMatchTheRegex() {
      logger.addTap(logMock);
      setIgnoredErrors(leftAlign("\n        /ignore.*/\n        "));
      logger.error("please log this");
      assertEquals(1, messages.size());
      assertEquals(0, stats.numIgnoredWarn);
      assertEquals(0, stats.numIgnoredError);
    }

    @Test
    @DisplayName("FSHLogger #ignoreErrors should correctly handle ignored warnign and errors at the same time")
    void shouldCorrectlyHandleIgnoredWarnignAndErrorsAtTheSameTime() {
      logger.addTap(logMock);
      setIgnoredErrors(leftAlign("\n        /ignore.*error/\n        error1\n        "));
      setIgnoredWarnings(leftAlign("\n        /ignore.*warning/\n        warning1\n        "));
      logger.info("just an info");
      logger.warn("this warning is not ignored");
      logger.error("error1");
      logger.debug("hello world");
      logger.error("error2 should not be ignored");
      logger.warn("ignore this warning");
      logger.warn("ignore this warning too");
      logger.error("this error should not be ignored");
      logger.info("enough");
      assertEquals(6, messages.size());
      assertMatches("info.*just an info", "", messages.get(0));
      assertMatches("warn.*this warning is not ignored", "", messages.get(1));
      assertMatches("debug.*hello world", "", messages.get(2));
      assertMatches("error.*error2 should not be ignored", "", messages.get(3));
      assertMatches("error.*this error should not be ignored", "", messages.get(4));
      assertMatches("info.*enough", "", messages.get(5));
      assertEquals(1, stats.numDebug);
      assertEquals(2, stats.numInfo);
      assertEquals(1, stats.numWarn);
      assertEquals(2, stats.numError);
      assertEquals(2, stats.numIgnoredWarn);
      assertEquals(1, stats.numIgnoredError);
    }

  }
}

package org.hl7.fhir.sushi.importer.parser;

import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.utils.FshLogger;

// port of sushi/src/import/FSHErrorListener.ts (SUSHI 3.21.1)
/** Logs syntax errors, rewriting ANTLR's messages for common FSH mistakes (missing spaces, deprecated syntax). */
public class FSHErrorListener implements ErrorListener {
  private final String file;

  public FSHErrorListener(String file) {
    this.file = file;
  }

  public String getFile() { return file; }

  @Override
  public void syntaxError(Recognizer recognizer, Token offendingSymbol, int line, int column, String msg) {
    ErrorMessage m = buildErrorMessage(recognizer, offendingSymbol, line, column, msg);
    FshLogger.logger.error(m.message(), new SourceInfo(file, m.location()));
  }

  public record ErrorMessage(String message, TextLocation location) {}

  // JS RegExp.test == Matcher.find; the patterns below are the TS regexes verbatim (no flags; '.' excludes line
  // terminators in both; JS \S/\s are Unicode-aware, hence JS_S/JS_NS)
  private static final String JS_WS = "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]";
  private static final String JS_NWS = "[^\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]";
  private static final String DOT = "[^\\n\\r\\u2028\\u2029]";

  private static Pattern re(String jsRegex) {
    String r = jsRegex.replace("\\S", JS_NWS).replace("\\s", JS_WS).replace(".", DOT);
    // JS '$' without the m flag is end of input only; Java's also matches before a final line terminator
    if (r.endsWith("$")) r = r.substring(0, r.length() - 1) + "\\z";
    return Pattern.compile(r);
  }

  private static final Pattern MISSING_EQ = re("^missing '='");
  private static final Pattern STARTS_EQ = re("^=");
  private static final Pattern STARTS_CARET = re("^\\^");
  private static final Pattern NOT_CARET_ENDS_EQ = re("^[^\\^].*=$");
  private static final Pattern MISMATCHED_EXPECTING_EQ = re("^mismatched input .+ expecting '='$");
  private static final Pattern NOT_CARET_HAS_EQ = re("^[^\\^].*=");
  private static final Pattern NO_VIABLE_EQ = re("^no viable alternative at input '.*((\\S=)|(=\\S))");
  private static final Pattern EXTRANEOUS = re("^extraneous input");
  private static final Pattern HAS_EQ = re("=");
  private static final Pattern CARET_HAS_EQ = re("^\\^.+=");
  private static final Pattern MISMATCHED_EXPECTING_ARROW = re("^mismatched input .+ expecting '->'$");
  private static final Pattern HAS_ARROW = re("->");
  private static final Pattern STARTS_ARROW = re("^->");
  private static final Pattern MISSING_ARROW = re("^missing '->'");
  private static final Pattern ENDS_ARROW = re("->$");
  private static final Pattern EXTRANEOUS_STAR = re("^extraneous input '\\*\\S");
  private static final Pattern EXTRANEOUS_MIXINS = re("^extraneous input 'Mixins:'");
  private static final Pattern EXTRANEOUS_UNITS = re("^extraneous input 'units'");
  private static final Pattern PIPE = re("^\\|$");
  private static final Pattern REFERENCE_OPEN = re("^Reference\\([^)]*$");
  private static final Pattern STARTS_PAREN = re("^\\(");
  private static final Pattern REFERENCE = re("^Reference$");
  private static final Pattern NO_VIABLE = re("^no viable alternative at input '");
  private static final Pattern ENDS_COMMA = re(",$");

  /** The flags that may be applied to a rule, as defined by the flag rule in the FSH grammar */
  private static final List<String> FLAGS = List.of("MS", "SU", "?!", "TU", "N", "D");

  /** JS {@code /re/.test(s)}: s undefined is coerced to the string "undefined". */
  private static boolean test(Pattern p, String s) {
    return p.matcher(s == null ? "undefined" : s).find();
  }

  private static String text(Token t) {
    return t == null ? null : t.getText();
  }

  public ErrorMessage buildErrorMessage(Recognizer recognizer, Token offendingSymbol, int line, int column, String msg) {
    String message = msg;
    TextLocation location = new TextLocation(line, column + 1, line,
        column + (offendingSymbol != null ? offendingSymbol.getText().length() : 1));

    // Now attempt to detect known patterns and improve the error messages
    Token oneTokenBack = getPreviousNonWsToken(recognizer, offendingSymbol);
    Token twoTokensBack = getPreviousNonWsToken(recognizer, oneTokenBack);
    String off = text(offendingSymbol);
    String one = text(oneTokenBack);
    String two = text(twoTokensBack);

    // ########################################################################
    // # Missing space around =                                               #
    // ########################################################################

    // Alias: MyAlias =http://myserver.com/
    // > missing '=' at '=http://myserver.com/'
    if (test(MISSING_EQ, msg) && test(STARTS_EQ, off) && !test(STARTS_CARET, one)) {
      message = "Alias declarations must include at least one space both before and after the '=' sign";
    }

    // Alias: MyAlias= http://myserver.com/
    // > missing '=' at 'http://myserver.com/'
    else if (test(MISSING_EQ, msg) && test(NOT_CARET_ENDS_EQ, one)) {
      message = "Alias declarations must include at least one space both before and after the '=' sign";
      // Need to adjust the location to match the previous token (where '=' is)
      location = getTokenLocation(oneTokenBack);
    }

    // Alias: MyAlias=http://myserver.com/
    // > mismatched input '<EOF>' expecting '='
    else if (test(MISMATCHED_EXPECTING_EQ, msg) && test(NOT_CARET_HAS_EQ, one)) {
      message = "Alias declarations must include at least one space both before and after the '=' sign";
      // Need to adjust the location to match the previous token (where '=' is)
      location = getTokenLocation(oneTokenBack);
    }

    // * active= true
    // > extraneous input 'true'
    // * active =true
    // > extraneous input '=active'
    // * active=true
    // > no viable alternative at input '* active=true'
    // * valueString="My String"
    // > extraneous input 'String"'
    else if (test(NO_VIABLE_EQ, msg)
        || (test(EXTRANEOUS, msg) && test(HAS_EQ, one))
        || (test(EXTRANEOUS, msg) && test(STARTS_EQ, off))) {
      message = "Assignment rules must include at least one space both before and after the '=' sign";
    }

    // * component ^short ="Component1"
    // > 'missing '=' at '="Component1"''
    else if (test(MISSING_EQ, msg) && test(STARTS_EQ, off) && test(STARTS_CARET, one)) {
      message = "Assignment rules must include at least one space both before and after the '=' sign";
    }

    // * component ^short ="A component"
    // > extraneous input 'component\"' expecting {<EOF>, KW_ALIAS, KW_PROFILE, KW_EXTENSION, KW_INSTANCE, KW_INVARIANT, KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING}
    else if (test(EXTRANEOUS, msg) && test(STARTS_CARET, two) && test(STARTS_EQ, one)) {
      message = "Assignment rules must include at least one space both before and after the '=' sign";
      // Need to adjust the location to match the previous token (where '=' is)
      location = getTokenLocation(oneTokenBack);
    }

    // * component ^short= "Component1"
    // > 'missing '=' at '"Component1"''
    // * component ^short= "A component"
    // > missing '=' at '"A component"'
    // * component ^short="A component"
    // > missing '=' at 'component"'
    else if (test(MISSING_EQ, msg) && test(CARET_HAS_EQ, one)) {
      message = "Assignment rules must include at least one space both before and after the '=' sign";
      // Need to adjust the location to match the previous token (where '=' is)
      location = getTokenLocation(oneTokenBack);
    }

    // * component ^short="Component1"
    // > 'mismatched input '<EOF>' expecting '=''
    else if (test(MISMATCHED_EXPECTING_EQ, msg) && test(CARET_HAS_EQ, one)) {
      message = "Assignment rules must include at least one space both before and after the '=' sign";
      // Need to adjust the location to match the previous token (where '=' is)
      location = getTokenLocation(oneTokenBack);
    }

    // ########################################################################
    // # Missing spaces around ->                                             #
    // ########################################################################
    // * identifier ->"Patient.identifier"
    // > mismatched input '->\"Patient.identifier\"' expecting '->'
    // * identifier->"Patient.identifier"
    // > mismatched input '<EOF>' expecting '->'
    else if (test(MISMATCHED_EXPECTING_ARROW, msg)
        || (test(EXTRANEOUS, msg) && test(HAS_ARROW, one))
        || (test(EXTRANEOUS, msg) && test(STARTS_ARROW, off))) {
      message = "Mapping rules must include at least one space both before and after the '->' operator";
      // Need to adjust the location to match the previous token (where '=' is)
      location = getTokenLocation(oneTokenBack);
    }

    // * identifier-> "Patient.identifier"
    // > missing '->' at '"Patient.identifier"'
    else if (test(MISSING_ARROW, msg) && test(ENDS_ARROW, one)) {
      message = "Mapping rules must include at least one space both before and after the '->' operator";
      // Need to adjust the location to match the previous token (where '=' is)
      location = getTokenLocation(oneTokenBack);
    }

    // ########################################################################
    // # Missing space after *                                                #
    // ########################################################################
    // *active = true
    // > extraneous input '*component' expecting {<EOF>, KW_ALIAS, KW_PROFILE, KW_EXTENSION,
    // > KW_INSTANCE, KW_INVARIANT, KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING}
    else if (test(EXTRANEOUS_STAR, msg)) {
      message = "Rules must start with a '*' symbol followed by at least one space, and may only be preceded by whitespace";
    }

    // ########################################################################
    // # Deprecated syntax                                           #
    // ########################################################################
    // Mixins: MyRuleSet
    // > extraneous input 'Mixins:' expecting {<EOF>, KW_ALIAS, KW_PROFILE, KW_EXTENSION,
    // > KW_INSTANCE, KW_INVARIANT, KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING,
    // > KW_LOGICAL, KW_RESOURCE}
    else if (test(EXTRANEOUS_MIXINS, msg)) {
      message = "The 'Mixins' keyword is no longer supported. Use the 'insert' keyword to insert a "
          + "RuleSet at any location in the list of rules.";
    }

    // * valueQuantity units = http://foo.org#bar
    // * valueQuantity units from MyVS (preferred)
    // > extraneous input 'units' expecting {<EOF>, KW_ALIAS, KW_PROFILE, KW_EXTENSION,
    // > KW_INSTANCE, KW_INVARIANT, KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING,
    // > KW_LOGICAL, KW_RESOURCE}
    else if (test(EXTRANEOUS_UNITS, msg)) {
      message = "The 'units' keyword is no longer supported. You can safely remove it from your FSH "
          + "since quantity assignments and bindings function the same without it.";
    }

    // * value[x] only Reference(Patient | Practitioner | Person)
    // > extraneous input '|' expecting {<EOF>, KW_ALIAS, KW_PROFILE, KW_EXTENSION, KW_INSTANCE,
    // > KW_INVARIANT, KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING, KW_LOGICAL, KW_RESOURCE}
    // * * value[x] only Reference ( Patient | Practitioner | Person )
    // > extraneous input '(' expecting {<EOF>, KW_ALIAS, KW_PROFILE, KW_EXTENSION, KW_INSTANCE, KW_INVARIANT,
    // > KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING, KW_LOGICAL, KW_RESOURCE}
    // * value[x] only Reference( Patient | Practitioner | Person )
    // > extraneous input 'Patient' expecting {<EOF>, KW_ALIAS, KW_PROFILE, KW_EXTENSION,
    // > KW_INSTANCE, KW_INVARIANT, KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING, KW_LOGICAL, KW_RESOURCE}
    else if (test(EXTRANEOUS, msg)
        && (test(PIPE, off) || test(REFERENCE_OPEN, one) || (test(STARTS_PAREN, off) && test(REFERENCE, one)))) {
      message = "Using '|' to list references is no longer supported. Use 'or' to list multiple references.";
    }

    // * onset[x], abatement[x] MS
    // > extraneous input 'abatement[x]' expecting {<EOF>, KW_ALIAS, KW_PROFILE, KW_EXTENSION,
    // > KW_INSTANCE, KW_INVARIANT, KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING, KW_LOGICAL, KW_RESOURCE}
    // * codes from valueset FirstZooVS, SecondZooVS
    // > extraneous input 'SecondZooVS' expecting {<EOF>, KW_ALIAS, KW_PROFILE, KW_EXTENSION,
    // > KW_INSTANCE, KW_INVARIANT, KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING, KW_LOGICAL, KW_RESOURCE}
    // * #hippo, #crocodile , #emu from system ZOO
    // > no viable alternative at input '\n* #hippo, #crocodile ,'
    // * #hippo, #crocodile, #emu from system ZOO
    // > no viable alternative at input '\n* #hippo, #crocodile, #emu from'
    else if ((test(EXTRANEOUS, msg) || test(NO_VIABLE, msg)) && (test(ENDS_COMMA, one) || test(ENDS_COMMA, two))) {
      message = "Using ',' to list items is no longer supported. Use 'and' to list multiple items.";
    }

    // ########################################################################
    // # Flags on rules that do not support them                              #
    // ########################################################################
    // * subject only Reference(Patient) MS
    // > extraneous input 'MS' expecting {<EOF>, KW_ALIAS, KW_PROFILE, KW_EXTENSION, KW_INSTANCE,
    // > KW_INVARIANT, KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING, KW_LOGICAL, KW_RESOURCE}
    // * value[x] only string MS
    // > extraneous input 'MS' expecting {<EOF>, KW_ALIAS, KW_PROFILE, KW_EXTENSION, KW_INSTANCE,
    // > KW_INVARIANT, KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING, KW_LOGICAL, KW_RESOURCE}
    else if (test(EXTRANEOUS, msg) && off != null && FLAGS.contains(off)
        && hasPrecedingTokenOnLine(recognizer, offendingSymbol, "only")) {
      message = "Flags are not allowed on 'only' rules. Apply the flags in a separate rule (e.g., '* value[x] MS').";
    }

    return new ErrorMessage(message, location);
  }

  /**
   * Determines if a token with the given text appears earlier on the same line as the given token
   * @param recognizer - the Recognizer instance provided by ANTLR
   * @param token - the token to search backwards from
   * @param text - the token text to search for
   * @return true if a token with the given text precedes the token on the same line
   */
  static boolean hasPrecedingTokenOnLine(Recognizer recognizer, Token token, String text) {
    if (token == null) {
      return false;
    }
    List<Token> tokens = recognizer.getTokens();
    for (int i = token.getTokenIndex() - 1; i >= 0; i--) {
      Token previousToken = tokens.get(i);
      if (previousToken.getLine() != token.getLine()) {
        return false;
      }
      if (text.equals(previousToken.getText())) {
        return true;
      }
    }
    return false;
  }

  private static final Pattern NON_WS = re("\\S");

  /**
   * Gets the previous non-whitespace token, which may be needed to help interpret the error
   * @param recognizer - the Recognizer instance provided by ANTLR
   * @param token - the token ahead of the token we want returned
   * @return the previous non-WS token or null if there is no previous non-WS token
   */
  static Token getPreviousNonWsToken(Recognizer recognizer, Token token) {
    if (token == null) {
      return null;
    }
    List<Token> tokens = recognizer.getTokens();
    for (int i = token.getTokenIndex() - 1; i >= 0; i--) {
      Token previousToken = tokens.get(i);
      if (NON_WS.matcher(previousToken.getText()).find()) {
        return previousToken;
      }
    }
    return null;
  }

  static TextLocation getTokenLocation(Token token) {
    return new TextLocation(token.getLine(), token.getCharPositionInLine() + 1, token.getLine(),
        token.getCharPositionInLine() + token.getText().length());
  }
}

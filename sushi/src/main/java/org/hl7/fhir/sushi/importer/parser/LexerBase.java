package org.hl7.fhir.sushi.importer.parser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

// port of the antlr4 JS runtime 4.13.2 Lexer.nextToken/error recovery semantics (as used by SUSHI 3.21.1)
/**
 * Mode-based longest-match lexer. Each mode is an ordered list of rules; at every position the longest match wins,
 * ties going to the earlier rule. When no rule matches, a "token recognition error" is reported for the text from the
 * token start through the first char no rule could consume, all of which is dropped (ANTLR's LexerNoViableAlt
 * recovery); then lexing resumes after it.
 */
abstract class LexerBase implements TokenSource, Recognizer {
  /** How a rule's match continues: emitted on the default or hidden channel, skipped; mode changes. */
  enum Action { NONE, HIDDEN, SKIP }

  /** A matcher: the longest accepted length at pos (-1 if none) and how many chars it consumed while alive. */
  interface Matcher {
    LexPattern.Match match(String input, int pos);
  }

  /** A lexer rule. pushMode = -1 for none; pops = number of popMode actions. */
  record Rule(int type, Matcher matcher, Action action, int pushMode, int pops) {
    static Rule of(int type, LexPattern.Rx rx) { return new Rule(type, new LexPattern(rx)::match, Action.NONE, -1, 0); }
    Rule push(int mode) { return new Rule(type, matcher, action, mode, pops); }
    Rule pop(int n) { return new Rule(type, matcher, action, pushMode, n); }
    Rule hidden() { return new Rule(type, matcher, Action.HIDDEN, pushMode, pops); }
    Rule skip() { return new Rule(type, matcher, Action.SKIP, pushMode, pops); }
  }

  protected final InputText input;
  private final String data;
  private final ErrorListener listener;
  private int pos;
  private int line = 1;
  private int column;
  private int mode;
  private final Deque<Integer> modeStack = new ArrayDeque<>();
  private boolean hitEOF;

  LexerBase(InputText input, ErrorListener listener) {
    this.input = input;
    this.data = input.getData();
    this.listener = listener;
  }

  abstract Rule[] rules(int mode);

  @Override public List<Token> getTokens() { return List.of(); }

  private void consume(int n) {
    for (int i = 0; i < n; i++) {
      if (data.charAt(pos) == '\n') {
        line++;
        column = 0;
      } else {
        column++;
      }
      pos++;
    }
  }

  @Override public Token nextToken() {
    while (true) {
      if (hitEOF) return new Token(input, Token.EOF, null, Token.DEFAULT_CHANNEL, pos, pos - 1, line, column);
      int start = pos, startLine = line, startColumn = column;
      Rule best = null;
      int bestLen = -1, alive = 0;
      for (Rule r : rules(mode)) {
        LexPattern.Match m = r.matcher().match(data, pos);
        if (m.alive() > alive) alive = m.alive();
        if (m.accepted() > bestLen) {
          bestLen = m.accepted();
          best = r;
        }
      }
      Action action = Action.NONE;
      int type;
      if (best != null && bestLen > 0) {
        consume(bestLen);
        type = best.type();
        action = best.action();
        for (int i = 0; i < best.pops(); i++) {
          if (modeStack.isEmpty()) throw new IllegalStateException("Empty Stack");
          mode = modeStack.pop();
        }
        if (best.pushMode() >= 0) {
          modeStack.push(mode);
          mode = best.pushMode();
        }
      } else if (pos >= data.length()) {
        type = Token.EOF;
      } else {
        // no viable alternative: report start..failure point (inclusive), drop it, resume after it
        int fail = start + alive;
        consume(alive);
        listener.syntaxError(this, null, startLine, startColumn,
            "token recognition error at: '" + input.getText(start, fail) + "'");
        if (pos < data.length()) consume(1);
        action = Action.SKIP;
        type = 0;
      }
      if (pos >= data.length()) hitEOF = true;
      if (action == Action.SKIP) continue;
      return new Token(input, type, null, action == Action.HIDDEN ? Token.HIDDEN_CHANNEL : Token.DEFAULT_CHANNEL,
          start, pos - 1, startLine, startColumn);
    }
  }
}

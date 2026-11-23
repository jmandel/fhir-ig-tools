package org.hl7.fhir.sushi.importer.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// port of antlr4 JS runtime 4.13.2 BufferedTokenStream + CommonTokenStream (as used by SUSHI 3.21.1)
/**
 * Buffers the lexer's tokens (all channels) and presents the default-channel ones to the parser. Tokens are fetched
 * lazily, exactly when the JS runtime fetches them, because lexer errors are reported at fetch time and so their
 * position among the parser's error messages depends on it.
 */
final class CommonTokenStream {
  private final TokenSource tokenSource;
  private final List<Token> tokens = new ArrayList<>();
  private final List<Token> view = Collections.unmodifiableList(tokens);
  private int index = -1;
  private boolean fetchedEOF;
  private final int channel = Token.DEFAULT_CHANNEL;

  CommonTokenStream(TokenSource tokenSource) {
    this.tokenSource = tokenSource;
  }

  /** The tokens fetched so far (all channels); TS {@code _input.tokens}. */
  List<Token> getTokens() { return view; }

  int index() { return index; }

  void seek(int i) {
    lazyInit();
    index = adjustSeekIndex(i);
  }

  Token get(int i) {
    lazyInit();
    return tokens.get(i);
  }

  void consume() {
    boolean skipEofCheck;
    if (index >= 0) {
      skipEofCheck = fetchedEOF ? index < tokens.size() - 1 : index < tokens.size();
    } else {
      skipEofCheck = false;
    }
    if (!skipEofCheck && LA(1) == Token.EOF) throw new IllegalStateException("cannot consume EOF");
    if (sync(index + 1)) index = adjustSeekIndex(index + 1);
  }

  private boolean sync(int i) {
    int n = i - tokens.size() + 1;
    if (n > 0) return fetch(n) >= n;
    return true;
  }

  private int fetch(int n) {
    if (fetchedEOF) return 0;
    for (int i = 0; i < n; i++) {
      Token t = tokenSource.nextToken();
      t.setTokenIndex(tokens.size());
      tokens.add(t);
      if (t.getType() == Token.EOF) {
        fetchedEOF = true;
        return i + 1;
      }
    }
    return n;
  }

  int LA(int i) {
    return LT(i).getType();
  }

  Token LB(int k) {
    if (k == 0 || index - k < 0) return null;
    int i = index;
    int n = 1;
    while (n <= k) {
      i = previousTokenOnChannel(i - 1);
      n++;
    }
    return i < 0 ? null : tokens.get(i);
  }

  Token LT(int k) {
    lazyInit();
    if (k == 0) return null;
    if (k < 0) return LB(-k);
    int i = index;
    int n = 1;
    while (n < k) {
      if (sync(i + 1)) i = nextTokenOnChannel(i + 1);
      n++;
    }
    return tokens.get(i);
  }

  private int adjustSeekIndex(int i) {
    return nextTokenOnChannel(i);
  }

  private void lazyInit() {
    if (index == -1) {
      sync(0);
      index = adjustSeekIndex(0);
    }
  }

  private int nextTokenOnChannel(int i) {
    sync(i);
    if (i >= tokens.size()) return -1;
    Token token = tokens.get(i);
    while (token.getChannel() != channel) {
      if (token.getType() == Token.EOF) return -1;
      i++;
      sync(i);
      token = tokens.get(i);
    }
    return i;
  }

  private int previousTokenOnChannel(int i) {
    while (i >= 0 && tokens.get(i).getChannel() != channel) i--;
    return i;
  }

  /** JS getText(interval): lexes the rest of the input first (fill), then concatenates token texts up to EOF. */
  String getText(int start, int stop) {
    lazyInit();
    fill();
    if (start < 0 || stop < 0) return "";
    if (stop >= tokens.size()) stop = tokens.size() - 1;
    StringBuilder s = new StringBuilder();
    for (int i = start; i < stop + 1; i++) {
      Token t = tokens.get(i);
      if (t.getType() == Token.EOF) break;
      s.append(t.getText());
    }
    return s.toString();
  }

  void fill() {
    lazyInit();
    while (fetch(1000) == 1000) {
      // keep fetching
    }
  }
}

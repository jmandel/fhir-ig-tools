// Vendored from fhir-ig-jekyll@ecd3c065 shim/src/main/java/org/hl7/fhir/jekyll/yaml/Scanner.java (libyaml 0.2.5 event layer),
// unchanged except the package line and the opt-in tabsInBlankLines switch (marked "SUSHI:"), until the monorepo
// move puts it in common (PORTING.md section 7).
package org.hl7.fhir.sushi.yaml;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of libyaml-0.2.5/src/scanner.c: turns the character stream into tokens. Same structure as
 * the C code: a token queue with insertion for simple keys (KEY and BLOCK-MAPPING-START are
 * inserted retroactively when ':' is found), the potential-simple-key stack (one per flow level),
 * the indentation stack, and the flow level. Function names follow the C ones
 * ({@code yaml_parser_fetch_more_tokens} becomes {@link #fetchMoreTokens()}, etc.).
 */
abstract class Scanner extends Reader {
  /** yaml_simple_key_t */
  static final class SimpleKey {
    boolean possible;
    boolean required;
    long tokenNumber;
    Mark mark;

    SimpleKey(boolean possible, boolean required, long tokenNumber, Mark mark) {
      this.possible = possible;
      this.required = required;
      this.tokenNumber = tokenNumber;
      this.mark = mark;
    }
  }

  private static final int MAX_NUMBER_LENGTH = 9;

  boolean streamStartProduced;
  boolean streamEndProduced;
  int flowLevel;
  /** the token queue: tokens[tokensHead..] are queued */
  final List<Token> tokens = new ArrayList<>();
  int tokensHead;
  long tokensParsed;
  boolean tokenAvailable;
  private final List<Long> indents = new ArrayList<>();
  long indent;
  boolean simpleKeyAllowed;
  /**
   * SUSHI: accept tabs on otherwise blank lines (YAML 1.2 / yaml@1.10 do; libyaml reports "found character that
   * cannot start any token"). Off by default, so the Jekyll shim's libyaml behaviour is unchanged.
   */
  boolean tabsInBlankLines;
  private final List<SimpleKey> simpleKeys = new ArrayList<>();

  Scanner(byte[] input, int encoding) {
    super(input, encoding);
  }

  // ---- scanner.c macros ----

  /** CACHE(parser, length) */
  private void cache(int length) {
    if (unread < length) {
      updateBuffer(length);
    }
  }

  /** SKIP(parser) */
  private void skip() {
    markIndex++;
    markColumn++;
    unread--;
    pointer += widthAt(0);
  }

  /** SKIP_LINE(parser) */
  private void skipLine() {
    if (isCrlf()) {
      markIndex += 2;
      markColumn = 0;
      markLine++;
      unread -= 2;
      pointer += 2;
    } else if (isBreak()) {
      markIndex++;
      markColumn = 0;
      markLine++;
      unread--;
      pointer += widthAt(0);
    }
  }

  /** READ(parser, string): COPY one character into the string. */
  private void read(ByteString s) {
    int w = widthAt(0);
    for (int k = 0; k < w; k++) {
      s.add(buffer[pointer++]);
    }
    markIndex++;
    markColumn++;
    unread--;
  }

  /** READ_LINE(parser, string): CR LF, CR, LF and NEL become LF; LS and PS are kept. */
  private void readLine(ByteString s) {
    if (checkAt('\r', 0) && checkAt('\n', 1)) {
      s.add('\n');
      pointer += 2;
      markIndex += 2;
      markColumn = 0;
      markLine++;
      unread -= 2;
    } else if (checkAt('\r', 0) || checkAt('\n', 0)) {
      s.add('\n');
      pointer++;
      markIndex++;
      markColumn = 0;
      markLine++;
      unread--;
    } else if (at(0) == 0xC2 && at(1) == 0x85) {
      s.add('\n');
      pointer += 2;
      markIndex++;
      markColumn = 0;
      markLine++;
      unread--;
    } else if (at(0) == 0xE2 && at(1) == 0x80 && (at(2) == 0xA8 || at(2) == 0xA9)) {
      s.add(buffer[pointer++]);
      s.add(buffer[pointer++]);
      s.add(buffer[pointer++]);
      markIndex++;
      markColumn = 0;
      markLine++;
      unread--;
    }
  }

  // ---- queue helpers (QUEUE_* macros) ----

  private void enqueue(Token t) {
    tokens.add(t);
  }

  private void queueInsert(long index, Token t) {
    tokens.add(tokensHead + (int) index, t);
  }

  private int queueSize() {
    return tokens.size() - tokensHead;
  }

  /** tokens.head (the queue must be non-empty) */
  final Token head() {
    return tokens.get(tokensHead);
  }

  /** advance tokens.head (DEQUEUE / the parser's SKIP_TOKEN) */
  final void dequeue() {
    tokensHead++;
    if (tokensHead > 4096 && tokensHead * 2 > tokens.size()) {
      tokens.subList(0, tokensHead).clear();
      tokensHead = 0;
    }
  }

  // ---- errors ----

  /** yaml_parser_set_scanner_error */
  private LibyamlError scannerError(String context, Mark contextMark, String problem) {
    return new LibyamlError(LibyamlError.SCANNER_ERROR, problem, 0, 0, mark(), context, contextMark);
  }

  /** yaml_parser_scan: the next token, or null after STREAM-END. */
  final Token scan() {
    if (streamEndProduced) {
      return null;
    }
    if (!tokenAvailable) {
      fetchMoreTokens();
    }
    Token token = head();
    dequeue();
    tokenAvailable = false;
    tokensParsed++;
    if (token.type == Token.Type.STREAM_END) {
      streamEndProduced = true;
    }
    return token;
  }

  /** yaml_parser_fetch_more_tokens */
  final void fetchMoreTokens() {
    boolean needMoreTokens;
    while (true) {
      needMoreTokens = false;
      if (queueSize() == 0) {
        needMoreTokens = true;
      } else {
        staleSimpleKeys();
        for (SimpleKey simpleKey : simpleKeys) {
          if (simpleKey.possible && simpleKey.tokenNumber == tokensParsed) {
            needMoreTokens = true;
            break;
          }
        }
      }
      if (!needMoreTokens) {
        break;
      }
      fetchNextToken();
    }
    tokenAvailable = true;
  }

  /** yaml_parser_fetch_next_token */
  private void fetchNextToken() {
    cache(1);
    if (!streamStartProduced) {
      fetchStreamStart();
      return;
    }
    scanToNextToken();
    staleSimpleKeys();
    unrollIndent(markColumn);
    cache(4);
    if (isZ()) {
      fetchStreamEnd();
      return;
    }
    if (markColumn == 0 && check('%')) {
      fetchDirective();
      return;
    }
    if (markColumn == 0 && checkAt('-', 0) && checkAt('-', 1) && checkAt('-', 2) && isBlankzAt(3)) {
      fetchDocumentIndicator(Token.Type.DOCUMENT_START);
      return;
    }
    if (markColumn == 0 && checkAt('.', 0) && checkAt('.', 1) && checkAt('.', 2) && isBlankzAt(3)) {
      fetchDocumentIndicator(Token.Type.DOCUMENT_END);
      return;
    }
    if (check('[')) {
      fetchFlowCollectionStart(Token.Type.FLOW_SEQUENCE_START);
      return;
    }
    if (check('{')) {
      fetchFlowCollectionStart(Token.Type.FLOW_MAPPING_START);
      return;
    }
    if (check(']')) {
      fetchFlowCollectionEnd(Token.Type.FLOW_SEQUENCE_END);
      return;
    }
    if (check('}')) {
      fetchFlowCollectionEnd(Token.Type.FLOW_MAPPING_END);
      return;
    }
    if (check(',')) {
      fetchFlowEntry();
      return;
    }
    if (check('-') && isBlankzAt(1)) {
      fetchBlockEntry();
      return;
    }
    if (check('?') && (flowLevel != 0 || isBlankzAt(1))) {
      fetchKey();
      return;
    }
    if (check(':') && (flowLevel != 0 || isBlankzAt(1))) {
      fetchValue();
      return;
    }
    if (check('*')) {
      fetchAnchor(Token.Type.ALIAS);
      return;
    }
    if (check('&')) {
      fetchAnchor(Token.Type.ANCHOR);
      return;
    }
    if (check('!')) {
      fetchTag();
      return;
    }
    if (check('|') && flowLevel == 0) {
      fetchBlockScalar(true);
      return;
    }
    if (check('>') && flowLevel == 0) {
      fetchBlockScalar(false);
      return;
    }
    if (check('\'')) {
      fetchFlowScalar(true);
      return;
    }
    if (check('"')) {
      fetchFlowScalar(false);
      return;
    }
    if (!(isBlankz() || check('-') || check('?') || check(':') || check(',') || check('[') || check(']') || check('{')
        || check('}') || check('#') || check('&') || check('*') || check('!') || check('|') || check('>') || check('\'')
        || check('"') || check('%') || check('@') || check('`'))
        || (check('-') && !isBlankAt(1))
        || (flowLevel == 0 && (check('?') || check(':')) && !isBlankzAt(1))) {
      fetchPlainScalar();
      return;
    }
    throw scannerError("while scanning for the next token", mark(), "found character that cannot start any token");
  }

  /** yaml_parser_stale_simple_keys */
  private void staleSimpleKeys() {
    for (SimpleKey simpleKey : simpleKeys) {
      if (simpleKey.possible && (simpleKey.mark.line < markLine || simpleKey.mark.index + 1024 < markIndex)) {
        if (simpleKey.required) {
          throw scannerError("while scanning a simple key", simpleKey.mark, "could not find expected ':'");
        }
        simpleKey.possible = false;
      }
    }
  }

  /** yaml_parser_save_simple_key */
  private void saveSimpleKey() {
    boolean required = flowLevel == 0 && indent == markColumn;
    if (simpleKeyAllowed) {
      SimpleKey simpleKey = new SimpleKey(true, required, tokensParsed + queueSize(), mark());
      removeSimpleKey();
      simpleKeys.set(simpleKeys.size() - 1, simpleKey);
    }
  }

  /** yaml_parser_remove_simple_key */
  private void removeSimpleKey() {
    SimpleKey simpleKey = simpleKeys.get(simpleKeys.size() - 1);
    if (simpleKey.possible) {
      if (simpleKey.required) {
        throw scannerError("while scanning a simple key", simpleKey.mark, "could not find expected ':'");
      }
    }
    simpleKey.possible = false;
  }

  /** yaml_parser_increase_flow_level */
  private void increaseFlowLevel() {
    simpleKeys.add(new SimpleKey(false, false, 0, Mark.ZERO));
    flowLevel++;
  }

  /** yaml_parser_decrease_flow_level */
  private void decreaseFlowLevel() {
    if (flowLevel != 0) {
      flowLevel--;
      simpleKeys.remove(simpleKeys.size() - 1);
    }
  }

  /** yaml_parser_roll_indent */
  private void rollIndent(long column, long number, Token.Type type, Mark mark) {
    if (flowLevel != 0) {
      return;
    }
    if (indent < column) {
      indents.add(indent);
      indent = column;
      Token token = new Token(type, mark, mark);
      if (number == -1) {
        enqueue(token);
      } else {
        queueInsert(number - tokensParsed, token);
      }
    }
  }

  /** yaml_parser_unroll_indent */
  private void unrollIndent(long column) {
    if (flowLevel != 0) {
      return;
    }
    while (indent > column) {
      Mark m = mark();
      enqueue(new Token(Token.Type.BLOCK_END, m, m));
      indent = indents.remove(indents.size() - 1);
    }
  }

  /** yaml_parser_fetch_stream_start */
  private void fetchStreamStart() {
    indent = -1;
    simpleKeys.add(new SimpleKey(false, false, 0, Mark.ZERO));
    simpleKeyAllowed = true;
    streamStartProduced = true;
    Mark m = mark();
    Token token = new Token(Token.Type.STREAM_START, m, m);
    token.encoding = encoding;
    enqueue(token);
  }

  /** yaml_parser_fetch_stream_end */
  private void fetchStreamEnd() {
    if (markColumn != 0) {
      markColumn = 0;
      markLine++;
    }
    unrollIndent(-1);
    removeSimpleKey();
    simpleKeyAllowed = false;
    Mark m = mark();
    enqueue(new Token(Token.Type.STREAM_END, m, m));
  }

  /** yaml_parser_fetch_directive */
  private void fetchDirective() {
    unrollIndent(-1);
    removeSimpleKey();
    simpleKeyAllowed = false;
    enqueue(scanDirective());
  }

  /** yaml_parser_fetch_document_indicator */
  private void fetchDocumentIndicator(Token.Type type) {
    unrollIndent(-1);
    removeSimpleKey();
    simpleKeyAllowed = false;
    Mark startMark = mark();
    skip();
    skip();
    skip();
    Mark endMark = mark();
    enqueue(new Token(type, startMark, endMark));
  }

  /** yaml_parser_fetch_flow_collection_start */
  private void fetchFlowCollectionStart(Token.Type type) {
    saveSimpleKey();
    increaseFlowLevel();
    simpleKeyAllowed = true;
    Mark startMark = mark();
    skip();
    Mark endMark = mark();
    enqueue(new Token(type, startMark, endMark));
  }

  /** yaml_parser_fetch_flow_collection_end */
  private void fetchFlowCollectionEnd(Token.Type type) {
    removeSimpleKey();
    decreaseFlowLevel();
    simpleKeyAllowed = false;
    Mark startMark = mark();
    skip();
    Mark endMark = mark();
    enqueue(new Token(type, startMark, endMark));
  }

  /** yaml_parser_fetch_flow_entry */
  private void fetchFlowEntry() {
    removeSimpleKey();
    simpleKeyAllowed = true;
    Mark startMark = mark();
    skip();
    Mark endMark = mark();
    enqueue(new Token(Token.Type.FLOW_ENTRY, startMark, endMark));
  }

  /** yaml_parser_fetch_block_entry */
  private void fetchBlockEntry() {
    if (flowLevel == 0) {
      if (!simpleKeyAllowed) {
        throw scannerError(null, mark(), "block sequence entries are not allowed in this context");
      }
      rollIndent(markColumn, -1, Token.Type.BLOCK_SEQUENCE_START, mark());
    }
    // (in the flow context, '-' is an error the Parser reports)
    removeSimpleKey();
    simpleKeyAllowed = true;
    Mark startMark = mark();
    skip();
    Mark endMark = mark();
    enqueue(new Token(Token.Type.BLOCK_ENTRY, startMark, endMark));
  }

  /** yaml_parser_fetch_key */
  private void fetchKey() {
    if (flowLevel == 0) {
      if (!simpleKeyAllowed) {
        throw scannerError(null, mark(), "mapping keys are not allowed in this context");
      }
      rollIndent(markColumn, -1, Token.Type.BLOCK_MAPPING_START, mark());
    }
    removeSimpleKey();
    simpleKeyAllowed = flowLevel == 0;
    Mark startMark = mark();
    skip();
    Mark endMark = mark();
    enqueue(new Token(Token.Type.KEY, startMark, endMark));
  }

  /** yaml_parser_fetch_value */
  private void fetchValue() {
    SimpleKey simpleKey = simpleKeys.get(simpleKeys.size() - 1);
    if (simpleKey.possible) {
      queueInsert(simpleKey.tokenNumber - tokensParsed, new Token(Token.Type.KEY, simpleKey.mark, simpleKey.mark));
      rollIndent(simpleKey.mark.column, simpleKey.tokenNumber, Token.Type.BLOCK_MAPPING_START, simpleKey.mark);
      simpleKey.possible = false;
      simpleKeyAllowed = false;
    } else {
      if (flowLevel == 0) {
        if (!simpleKeyAllowed) {
          throw scannerError(null, mark(), "mapping values are not allowed in this context");
        }
        rollIndent(markColumn, -1, Token.Type.BLOCK_MAPPING_START, mark());
      }
      simpleKeyAllowed = flowLevel == 0;
    }
    Mark startMark = mark();
    skip();
    Mark endMark = mark();
    enqueue(new Token(Token.Type.VALUE, startMark, endMark));
  }

  /** yaml_parser_fetch_anchor */
  private void fetchAnchor(Token.Type type) {
    saveSimpleKey();
    simpleKeyAllowed = false;
    enqueue(scanAnchor(type));
  }

  /** yaml_parser_fetch_tag */
  private void fetchTag() {
    saveSimpleKey();
    simpleKeyAllowed = false;
    enqueue(scanTag());
  }

  /** yaml_parser_fetch_block_scalar */
  private void fetchBlockScalar(boolean literal) {
    removeSimpleKey();
    simpleKeyAllowed = true;
    enqueue(scanBlockScalar(literal));
  }

  /** yaml_parser_fetch_flow_scalar */
  private void fetchFlowScalar(boolean single) {
    saveSimpleKey();
    simpleKeyAllowed = false;
    enqueue(scanFlowScalar(single));
  }

  /** yaml_parser_fetch_plain_scalar */
  private void fetchPlainScalar() {
    saveSimpleKey();
    simpleKeyAllowed = false;
    enqueue(scanPlainScalar());
  }

  /** yaml_parser_scan_to_next_token */
  /** SUSHI: only blanks up to the next line break or the end of the input (tabsInBlankLines). */
  private boolean restOfLineBlank() {
    int k = 0;
    while (true) {
      cache(k + 2);
      if (checkAt(' ', k) || checkAt('\t', k)) {
        k++;
      } else {
        return isBreakzAt(k);
      }
    }
  }

  private void scanToNextToken() {
    while (true) {
      cache(1);
      if (markColumn == 0 && isBom()) {
        skip();
      }
      cache(1);
      while (check(' ') || ((flowLevel != 0 || !simpleKeyAllowed) && check('\t'))) {
        skip();
        cache(1);
      }
      if (tabsInBlankLines && check('\t')) {
        // SUSHI: skip a run of blanks if only a line break, a comment or the end follows
        int k = 0;
        while (true) {
          cache(k + 2);
          if (checkAt(' ', k) || checkAt('\t', k)) {
            k++;
          } else {
            break;
          }
        }
        if (isBreakzAt(k) || checkAt('#', k)) {
          for (int i = 0; i < k; i++) {
            skip();
            cache(1);
          }
        }
      }
      if (check('#')) {
        while (!isBreakz()) {
          skip();
          cache(1);
        }
      }
      if (isBreak()) {
        cache(2);
        skipLine();
        if (flowLevel == 0) {
          simpleKeyAllowed = true;
        }
      } else {
        break;
      }
    }
  }

  /** yaml_parser_scan_directive */
  private Token scanDirective() {
    Mark startMark = mark();
    skip();
    byte[] name = scanDirectiveName(startMark);
    Token token;
    if (java.util.Arrays.equals(name, new byte[] {'Y', 'A', 'M', 'L'})) {
      int[] v = scanVersionDirectiveValue(startMark);
      Mark endMark = mark();
      token = new Token(Token.Type.VERSION_DIRECTIVE, startMark, endMark);
      token.major = v[0];
      token.minor = v[1];
    } else if (java.util.Arrays.equals(name, new byte[] {'T', 'A', 'G'})) {
      byte[][] hp = scanTagDirectiveValue(startMark);
      Mark endMark = mark();
      token = new Token(Token.Type.TAG_DIRECTIVE, startMark, endMark);
      token.handle = hp[0];
      token.suffix = hp[1];
    } else {
      throw scannerError("while scanning a directive", startMark, "found unknown directive name");
    }
    cache(1);
    while (isBlank()) {
      skip();
      cache(1);
    }
    if (check('#')) {
      while (!isBreakz()) {
        skip();
        cache(1);
      }
    }
    if (!isBreakz()) {
      throw scannerError("while scanning a directive", startMark, "did not find expected comment or line break");
    }
    if (isBreak()) {
      cache(2);
      skipLine();
    }
    return token;
  }

  /** yaml_parser_scan_directive_name */
  private byte[] scanDirectiveName(Mark startMark) {
    ByteString string = new ByteString();
    cache(1);
    while (isAlpha()) {
      read(string);
      cache(1);
    }
    if (string.isEmpty()) {
      throw scannerError("while scanning a directive", startMark, "could not find expected directive name");
    }
    if (!isBlankz()) {
      throw scannerError("while scanning a directive", startMark, "found unexpected non-alphabetical character");
    }
    return string.toCString();
  }

  /** yaml_parser_scan_version_directive_value */
  private int[] scanVersionDirectiveValue(Mark startMark) {
    cache(1);
    while (isBlank()) {
      skip();
      cache(1);
    }
    int major = scanVersionDirectiveNumber(startMark);
    if (!check('.')) {
      throw scannerError("while scanning a %YAML directive", startMark, "did not find expected digit or '.' character");
    }
    skip();
    int minor = scanVersionDirectiveNumber(startMark);
    return new int[] {major, minor};
  }

  /** yaml_parser_scan_version_directive_number */
  private int scanVersionDirectiveNumber(Mark startMark) {
    int value = 0;
    int length = 0;
    cache(1);
    while (isDigit()) {
      if (++length > MAX_NUMBER_LENGTH) {
        throw scannerError("while scanning a %YAML directive", startMark, "found extremely long version number");
      }
      value = value * 10 + asDigit();
      skip();
      cache(1);
    }
    if (length == 0) {
      throw scannerError("while scanning a %YAML directive", startMark, "did not find expected version number");
    }
    return value;
  }

  /** yaml_parser_scan_tag_directive_value: {handle, prefix} */
  private byte[][] scanTagDirectiveValue(Mark startMark) {
    cache(1);
    while (isBlank()) {
      skip();
      cache(1);
    }
    byte[] handle = scanTagHandle(true, startMark);
    cache(1);
    if (!isBlank()) {
      throw scannerError("while scanning a %TAG directive", startMark, "did not find expected whitespace");
    }
    while (isBlank()) {
      skip();
      cache(1);
    }
    byte[] prefix = scanTagUri(true, true, null, startMark);
    cache(1);
    if (!isBlankz()) {
      throw scannerError("while scanning a %TAG directive", startMark, "did not find expected whitespace or line break");
    }
    return new byte[][] {handle, prefix};
  }

  /** yaml_parser_scan_anchor */
  private Token scanAnchor(Token.Type type) {
    int length = 0;
    ByteString string = new ByteString();
    Mark startMark = mark();
    skip();
    cache(1);
    while (isAlpha()) {
      read(string);
      cache(1);
      length++;
    }
    Mark endMark = mark();
    if (length == 0 || !(isBlankz() || check('?') || check(':') || check(',') || check(']') || check('}') || check('%')
        || check('@') || check('`'))) {
      throw scannerError(type == Token.Type.ANCHOR ? "while scanning an anchor" : "while scanning an alias", startMark,
          "did not find expected alphabetic or numeric character");
    }
    Token token = new Token(type, startMark, endMark);
    token.value = string.toCString();
    return token;
  }

  /** yaml_parser_scan_tag */
  private Token scanTag() {
    byte[] handle;
    byte[] suffix;
    Mark startMark = mark();
    cache(2);
    if (checkAt('<', 1)) {
      handle = new byte[0];
      skip();
      skip();
      suffix = scanTagUri(true, false, null, startMark);
      if (!check('>')) {
        throw scannerError("while scanning a tag", startMark, "did not find the expected '>'");
      }
      skip();
    } else {
      handle = scanTagHandle(false, startMark);
      if (handle.length > 0 && handle[0] == '!' && handle.length > 1 && handle[handle.length - 1] == '!') {
        suffix = scanTagUri(false, false, null, startMark);
      } else {
        suffix = scanTagUri(false, false, handle, startMark);
        handle = new byte[] {'!'};
        // A special case: the '!' tag. Set the handle to '' and the suffix to '!'.
        if (suffix.length == 0) {
          byte[] tmp = handle;
          handle = suffix;
          suffix = tmp;
        }
      }
    }
    cache(1);
    if (!isBlankz()) {
      if (flowLevel == 0 || !check(',')) {
        throw scannerError("while scanning a tag", startMark, "did not find expected whitespace or line break");
      }
    }
    Mark endMark = mark();
    Token token = new Token(Token.Type.TAG, startMark, endMark);
    token.handle = handle;
    token.suffix = suffix;
    return token;
  }

  /** yaml_parser_scan_tag_handle */
  private byte[] scanTagHandle(boolean directive, Mark startMark) {
    ByteString string = new ByteString();
    cache(1);
    if (!check('!')) {
      throw scannerError(directive ? "while scanning a tag directive" : "while scanning a tag", startMark, "did not find expected '!'");
    }
    read(string);
    cache(1);
    while (isAlpha()) {
      read(string);
      cache(1);
    }
    if (check('!')) {
      read(string);
    } else {
      // Either the '!' tag or not really a tag handle: an error in a %TAG directive, else part of the URI.
      if (directive && !(string.first() == '!' && string.len == 1)) {
        throw scannerError("while parsing a tag directive", startMark, "did not find expected '!'");
      }
    }
    return string.toCString();
  }

  /** yaml_parser_scan_tag_uri */
  private byte[] scanTagUri(boolean uriChar, boolean directive, byte[] head, Mark startMark) {
    int length = head != null ? head.length : 0;
    ByteString string = new ByteString();
    // Copy the head if needed (without the leading '!').
    for (int k = 1; k < length; k++) {
      string.add(head[k]);
    }
    cache(1);
    while (isAlpha() || check(';') || check('/') || check('?') || check(':') || check('@') || check('&') || check('=')
        || check('+') || check('$') || check('.') || check('%') || check('!') || check('~') || check('*') || check('\'')
        || check('(') || check(')') || (uriChar && (check(',') || check('[') || check(']')))) {
      if (check('%')) {
        scanUriEscapes(directive, startMark, string);
      } else {
        read(string);
      }
      length++;
      cache(1);
    }
    if (length == 0) {
      throw scannerError(directive ? "while parsing a %TAG directive" : "while parsing a tag", startMark, "did not find expected tag URI");
    }
    return string.toCString();
  }

  /** yaml_parser_scan_uri_escapes */
  private void scanUriEscapes(boolean directive, Mark startMark, ByteString string) {
    int width = 0;
    do {
      cache(3);
      if (!(check('%') && isHexAt(1) && isHexAt(2))) {
        throw scannerError(directive ? "while parsing a %TAG directive" : "while parsing a tag", startMark, "did not find URI escaped octet");
      }
      int octet = (asHexAt(1) << 4) + asHexAt(2);
      if (width == 0) {
        width = (octet & 0x80) == 0x00 ? 1 : (octet & 0xE0) == 0xC0 ? 2 : (octet & 0xF0) == 0xE0 ? 3 : (octet & 0xF8) == 0xF0 ? 4 : 0;
        if (width == 0) {
          throw scannerError(directive ? "while parsing a %TAG directive" : "while parsing a tag", startMark, "found an incorrect leading UTF-8 octet");
        }
      } else {
        if ((octet & 0xC0) != 0x80) {
          throw scannerError(directive ? "while parsing a %TAG directive" : "while parsing a tag", startMark, "found an incorrect trailing UTF-8 octet");
        }
      }
      string.add(octet);
      skip();
      skip();
      skip();
    } while (--width != 0);
  }

  /** yaml_parser_scan_block_scalar */
  private Token scanBlockScalar(boolean literal) {
    ByteString string = new ByteString();
    ByteString leadingBreak = new ByteString();
    ByteString trailingBreaks = new ByteString();
    int chomping = 0;
    int increment = 0;
    int[] indentRef = {0};
    boolean leadingBlank = false;
    boolean trailingBlank;
    Mark startMark = mark();
    skip();
    cache(1);
    if (check('+') || check('-')) {
      chomping = check('+') ? +1 : -1;
      skip();
      cache(1);
      if (isDigit()) {
        if (check('0')) {
          throw scannerError("while scanning a block scalar", startMark, "found an indentation indicator equal to 0");
        }
        increment = asDigit();
        skip();
      }
    } else if (isDigit()) {
      if (check('0')) {
        throw scannerError("while scanning a block scalar", startMark, "found an indentation indicator equal to 0");
      }
      increment = asDigit();
      skip();
      cache(1);
      if (check('+') || check('-')) {
        chomping = check('+') ? +1 : -1;
        skip();
      }
    }
    cache(1);
    while (isBlank()) {
      skip();
      cache(1);
    }
    if (check('#')) {
      while (!isBreakz()) {
        skip();
        cache(1);
      }
    }
    if (!isBreakz()) {
      throw scannerError("while scanning a block scalar", startMark, "did not find expected comment or line break");
    }
    if (isBreak()) {
      cache(2);
      skipLine();
    }
    Mark[] endMark = {mark()};
    if (increment != 0) {
      indentRef[0] = indent >= 0 ? (int) (indent + increment) : increment;
    }
    scanBlockScalarBreaks(indentRef, trailingBreaks, startMark, endMark);
    cache(1);
    while ((int) markColumn == indentRef[0] && !isZ()) {
      // We are at the beginning of a non-empty line.
      trailingBlank = isBlank();
      if (!literal && leadingBreak.first() == '\n' && !leadingBlank && !trailingBlank) {
        if (trailingBreaks.first() == '\0') {
          string.add(' ');
        }
        leadingBreak.clear();
      } else {
        string.join(leadingBreak);
        leadingBreak.clear();
      }
      string.join(trailingBreaks);
      trailingBreaks.clear();
      leadingBlank = isBlank();
      while (!isBreakz()) {
        read(string);
        cache(1);
      }
      cache(2);
      readLine(leadingBreak);
      scanBlockScalarBreaks(indentRef, trailingBreaks, startMark, endMark);
    }
    if (chomping != -1) {
      string.join(leadingBreak);
    }
    if (chomping == 1) {
      string.join(trailingBreaks);
    }
    Token token = new Token(Token.Type.SCALAR, startMark, endMark[0]);
    token.value = string.toBytes();
    token.style = literal ? Token.LITERAL_SCALAR_STYLE : Token.FOLDED_SCALAR_STYLE;
    return token;
  }

  /** yaml_parser_scan_block_scalar_breaks */
  private void scanBlockScalarBreaks(int[] indentRef, ByteString breaks, Mark startMark, Mark[] endMark) {
    int maxIndent = 0;
    endMark[0] = mark();
    while (true) {
      cache(1);
      while ((indentRef[0] == 0 || (int) markColumn < indentRef[0]) && isSpace()) {
        skip();
        cache(1);
      }
      if ((int) markColumn > maxIndent) {
        maxIndent = (int) markColumn;
      }
      if ((indentRef[0] == 0 || (int) markColumn < indentRef[0]) && isTab()) {
        throw scannerError("while scanning a block scalar", startMark, "found a tab character where an indentation space is expected");
      }
      if (!isBreak()) {
        break;
      }
      cache(2);
      readLine(breaks);
      endMark[0] = mark();
    }
    if (indentRef[0] == 0) {
      indentRef[0] = maxIndent;
      if (indentRef[0] < indent + 1) {
        indentRef[0] = (int) (indent + 1);
      }
      if (indentRef[0] < 1) {
        indentRef[0] = 1;
      }
    }
  }

  /** yaml_parser_scan_flow_scalar */
  private Token scanFlowScalar(boolean single) {
    ByteString string = new ByteString();
    ByteString leadingBreak = new ByteString();
    ByteString trailingBreaks = new ByteString();
    ByteString whitespaces = new ByteString();
    boolean leadingBlanks;
    Mark startMark = mark();
    skip();
    while (true) {
      cache(4);
      if (markColumn == 0 && ((checkAt('-', 0) && checkAt('-', 1) && checkAt('-', 2)) || (checkAt('.', 0) && checkAt('.', 1) && checkAt('.', 2)))
          && isBlankzAt(3)) {
        throw scannerError("while scanning a quoted scalar", startMark, "found unexpected document indicator");
      }
      if (isZ()) {
        throw scannerError("while scanning a quoted scalar", startMark, "found unexpected end of stream");
      }
      cache(2);
      leadingBlanks = false;
      while (!isBlankz()) {
        if (single && checkAt('\'', 0) && checkAt('\'', 1)) {
          string.add('\'');
          skip();
          skip();
        } else if (check(single ? '\'' : '"')) {
          break;
        } else if (!single && check('\\') && isBreakAt(1)) {
          cache(3);
          skip();
          skipLine();
          leadingBlanks = true;
          break;
        } else if (!single && check('\\')) {
          int codeLength = 0;
          switch (at(1)) {
            case '0': string.add('\0'); break;
            case 'a': string.add(0x07); break;
            case 'b': string.add(0x08); break;
            case 't':
            case '\t': string.add(0x09); break;
            case 'n': string.add(0x0A); break;
            case 'v': string.add(0x0B); break;
            case 'f': string.add(0x0C); break;
            case 'r': string.add(0x0D); break;
            case 'e': string.add(0x1B); break;
            case ' ': string.add(0x20); break;
            case '"': string.add('"'); break;
            case '/': string.add('/'); break;
            case '\\': string.add('\\'); break;
            case 'N': string.add(0xC2); string.add(0x85); break;
            case '_': string.add(0xC2); string.add(0xA0); break;
            case 'L': string.add(0xE2); string.add(0x80); string.add(0xA8); break;
            case 'P': string.add(0xE2); string.add(0x80); string.add(0xA9); break;
            case 'x': codeLength = 2; break;
            case 'u': codeLength = 4; break;
            case 'U': codeLength = 8; break;
            default:
              throw scannerError("while parsing a quoted scalar", startMark, "found unknown escape character");
          }
          skip();
          skip();
          if (codeLength != 0) {
            long value = 0;
            cache(codeLength);
            for (int k = 0; k < codeLength; k++) {
              if (!isHexAt(k)) {
                throw scannerError("while parsing a quoted scalar", startMark, "did not find expected hexdecimal number");
              }
              value = ((value << 4) + asHexAt(k)) & 0xFFFFFFFFL;
            }
            if ((value >= 0xD800 && value <= 0xDFFF) || value > 0x10FFFF) {
              throw scannerError("while parsing a quoted scalar", startMark, "found invalid Unicode character escape code");
            }
            int v = (int) value;
            if (v <= 0x7F) {
              string.add(v);
            } else if (v <= 0x7FF) {
              string.add(0xC0 + (v >> 6));
              string.add(0x80 + (v & 0x3F));
            } else if (v <= 0xFFFF) {
              string.add(0xE0 + (v >> 12));
              string.add(0x80 + ((v >> 6) & 0x3F));
              string.add(0x80 + (v & 0x3F));
            } else {
              string.add(0xF0 + (v >> 18));
              string.add(0x80 + ((v >> 12) & 0x3F));
              string.add(0x80 + ((v >> 6) & 0x3F));
              string.add(0x80 + (v & 0x3F));
            }
            for (int k = 0; k < codeLength; k++) {
              skip();
            }
          }
        } else {
          read(string);
        }
        cache(2);
      }
      cache(1);
      if (check(single ? '\'' : '"')) {
        break;
      }
      cache(1);
      while (isBlank() || isBreak()) {
        if (isBlank()) {
          if (!leadingBlanks) {
            read(whitespaces);
          } else {
            skip();
          }
        } else {
          cache(2);
          if (!leadingBlanks) {
            whitespaces.clear();
            readLine(leadingBreak);
            leadingBlanks = true;
          } else {
            readLine(trailingBreaks);
          }
        }
        cache(1);
      }
      if (leadingBlanks) {
        if (leadingBreak.first() == '\n') {
          if (trailingBreaks.first() == '\0') {
            string.add(' ');
          } else {
            string.join(trailingBreaks);
            trailingBreaks.clear();
          }
          leadingBreak.clear();
        } else {
          string.join(leadingBreak);
          string.join(trailingBreaks);
          leadingBreak.clear();
          trailingBreaks.clear();
        }
      } else {
        string.join(whitespaces);
        whitespaces.clear();
      }
    }
    skip();
    Mark endMark = mark();
    Token token = new Token(Token.Type.SCALAR, startMark, endMark);
    token.value = string.toBytes();
    token.style = single ? Token.SINGLE_QUOTED_SCALAR_STYLE : Token.DOUBLE_QUOTED_SCALAR_STYLE;
    return token;
  }

  /** yaml_parser_scan_plain_scalar */
  private Token scanPlainScalar() {
    ByteString string = new ByteString();
    ByteString leadingBreak = new ByteString();
    ByteString trailingBreaks = new ByteString();
    ByteString whitespaces = new ByteString();
    boolean leadingBlanks = false;
    long indentLimit = indent + 1;
    Mark startMark = mark();
    // end_mark, kept as fields to avoid a Mark per character
    long endIndex = markIndex;
    long endLine = markLine;
    long endColumn = markColumn;
    while (true) {
      cache(4);
      if (markColumn == 0 && ((checkAt('-', 0) && checkAt('-', 1) && checkAt('-', 2)) || (checkAt('.', 0) && checkAt('.', 1) && checkAt('.', 2)))
          && isBlankzAt(3)) {
        break;
      }
      if (check('#')) {
        break;
      }
      while (!isBlankz()) {
        if (flowLevel != 0 && check(':')
            && (checkAt(',', 1) || checkAt('?', 1) || checkAt('[', 1) || checkAt(']', 1) || checkAt('{', 1) || checkAt('}', 1))) {
          throw scannerError("while scanning a plain scalar", startMark, "found unexpected ':'");
        }
        if ((check(':') && isBlankzAt(1)) || (flowLevel != 0 && (check(',') || check('[') || check(']') || check('{') || check('}')))) {
          break;
        }
        if (leadingBlanks || !whitespaces.isEmpty()) {
          if (leadingBlanks) {
            if (leadingBreak.first() == '\n') {
              if (trailingBreaks.first() == '\0') {
                string.add(' ');
              } else {
                string.join(trailingBreaks);
                trailingBreaks.clear();
              }
              leadingBreak.clear();
            } else {
              string.join(leadingBreak);
              string.join(trailingBreaks);
              leadingBreak.clear();
              trailingBreaks.clear();
            }
            leadingBlanks = false;
          } else {
            string.join(whitespaces);
            whitespaces.clear();
          }
        }
        read(string);
        endIndex = markIndex;
        endLine = markLine;
        endColumn = markColumn;
        cache(2);
      }
      if (!(isBlank() || isBreak())) {
        break;
      }
      cache(1);
      while (isBlank() || isBreak()) {
        if (isBlank()) {
          if (leadingBlanks && (int) markColumn < indentLimit && isTab() && !(tabsInBlankLines && restOfLineBlank())) {
            throw scannerError("while scanning a plain scalar", startMark, "found a tab character that violates indentation");
          }
          if (!leadingBlanks) {
            read(whitespaces);
          } else {
            skip();
          }
        } else {
          cache(2);
          if (!leadingBlanks) {
            whitespaces.clear();
            readLine(leadingBreak);
            leadingBlanks = true;
          } else {
            readLine(trailingBreaks);
          }
        }
        cache(1);
      }
      if (flowLevel == 0 && (int) markColumn < indentLimit) {
        break;
      }
    }
    Token token = new Token(Token.Type.SCALAR, startMark, new Mark(endIndex, endLine, endColumn));
    token.value = string.toBytes();
    token.style = Token.PLAIN_SCALAR_STYLE;
    if (leadingBlanks) {
      simpleKeyAllowed = true;
    }
    return token;
  }
}

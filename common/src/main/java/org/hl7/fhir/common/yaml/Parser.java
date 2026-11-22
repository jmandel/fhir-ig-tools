package org.hl7.fhir.common.yaml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Port of libyaml-0.2.5/src/parser.c: the LL(1) state machine that turns tokens into events
 * (STREAM/DOCUMENT/MAPPING/SEQUENCE start/end, SCALAR, ALIAS), including tag-handle resolution
 * against %TAG directives and the default {@code !} / {@code !!} handles, and the
 * {@code plain_implicit} / {@code quoted_implicit} flags Psych hands to the handler.
 *
 * <p>Usage: {@code new Parser(bytes, Parser.UTF8_ENCODING)} (or {@code ANY_ENCODING} to detect a BOM), then
 * call {@link #parse()} until it returns null. Errors are thrown as {@link LibyamlError} (libyaml's
 * error state; every libyaml error is terminal). One instance parses one input; not thread-safe.
 */
public final class Parser extends Scanner {
  /** yaml_parser_state_t */
  enum State {
    STREAM_START, IMPLICIT_DOCUMENT_START, DOCUMENT_START, DOCUMENT_CONTENT, DOCUMENT_END, BLOCK_NODE,
    BLOCK_NODE_OR_INDENTLESS_SEQUENCE, FLOW_NODE, BLOCK_SEQUENCE_FIRST_ENTRY, BLOCK_SEQUENCE_ENTRY,
    INDENTLESS_SEQUENCE_ENTRY, BLOCK_MAPPING_FIRST_KEY, BLOCK_MAPPING_KEY, BLOCK_MAPPING_VALUE,
    FLOW_SEQUENCE_FIRST_ENTRY, FLOW_SEQUENCE_ENTRY, FLOW_SEQUENCE_ENTRY_MAPPING_KEY,
    FLOW_SEQUENCE_ENTRY_MAPPING_VALUE, FLOW_SEQUENCE_ENTRY_MAPPING_END, FLOW_MAPPING_FIRST_KEY, FLOW_MAPPING_KEY,
    FLOW_MAPPING_VALUE, FLOW_MAPPING_EMPTY_VALUE, END
  }

  private static final byte[] BANG = {'!'};
  private static final byte[][][] DEFAULT_TAG_DIRECTIVES = {
    {BANG, BANG},
    {{'!', '!'}, "tag:yaml.org,2002:".getBytes(java.nio.charset.StandardCharsets.US_ASCII)},
  };

  private State state = State.STREAM_START;
  private final List<State> states = new ArrayList<>();
  private final List<Mark> marks = new ArrayList<>();
  /** parser->tag_directives: {handle, prefix} */
  private final List<byte[][]> tagDirectives = new ArrayList<>();

  /**
   * @param input the raw input bytes
   * @param encoding {@code UTF8_ENCODING}, {@code UTF16LE_ENCODING}, {@code UTF16BE_ENCODING}, or
   *     {@code ANY_ENCODING} (detect from a BOM; UTF-8 without one), as {@code yaml_parser_set_encoding}
   */
  public Parser(byte[] input, int encoding) {
    super(input, encoding);
  }

  /**
   * As {@link #Parser(byte[], int)}, with {@code yaml110} = true selecting the opt-in relaxations SUSHI's yaml@1.10
   * composer needs where eemeli/yaml 1.10 (YAML 1.2) accepts input libyaml 0.2.5 rejects: any character is allowed
   * and only CR/LF break lines; tabs may appear on otherwise blank lines and before the end of a line inside plain
   * scalars; {@code %} may start a plain scalar after column 0; a block mapping value without a key (": x") has an
   * empty key. With false this is exactly libyaml 0.2.5 (what the Jekyll shim uses).
   */
  public Parser(byte[] input, int encoding, boolean yaml110) {
    super(input, encoding);
    this.yaml110 = yaml110;
  }

  // ---- PEEK_TOKEN / SKIP_TOKEN ----

  private Token peekToken() {
    if (!tokenAvailable) {
      fetchMoreTokens();
    }
    return head();
  }

  private void skipToken() {
    tokenAvailable = false;
    tokensParsed++;
    streamEndProduced = head().type == Token.Type.STREAM_END;
    dequeue();
  }

  private <T> T pop(List<T> stack) {
    return stack.remove(stack.size() - 1);
  }

  /** yaml_parser_set_parser_error (context_mark is left as initialized: zero) */
  private static LibyamlError parserError(String problem, Mark problemMark) {
    return new LibyamlError(LibyamlError.PARSER_ERROR, problem, 0, 0, problemMark, null, Mark.ZERO);
  }

  /** yaml_parser_set_parser_error_context */
  private static LibyamlError parserErrorContext(String context, Mark contextMark, String problem, Mark problemMark) {
    return new LibyamlError(LibyamlError.PARSER_ERROR, problem, 0, 0, problemMark, context, contextMark);
  }

  /** yaml_parser_parse: the next event, or null after the end of the stream. */
  public Event parse() {
    if (streamEndProduced || state == State.END) {
      return null;
    }
    return stateMachine();
  }

  /** yaml_parser_state_machine */
  private Event stateMachine() {
    switch (state) {
      case STREAM_START: return parseStreamStart();
      case IMPLICIT_DOCUMENT_START: return parseDocumentStart(true);
      case DOCUMENT_START: return parseDocumentStart(false);
      case DOCUMENT_CONTENT: return parseDocumentContent();
      case DOCUMENT_END: return parseDocumentEnd();
      case BLOCK_NODE: return parseNode(true, false);
      case BLOCK_NODE_OR_INDENTLESS_SEQUENCE: return parseNode(true, true);
      case FLOW_NODE: return parseNode(false, false);
      case BLOCK_SEQUENCE_FIRST_ENTRY: return parseBlockSequenceEntry(true);
      case BLOCK_SEQUENCE_ENTRY: return parseBlockSequenceEntry(false);
      case INDENTLESS_SEQUENCE_ENTRY: return parseIndentlessSequenceEntry();
      case BLOCK_MAPPING_FIRST_KEY: return parseBlockMappingKey(true);
      case BLOCK_MAPPING_KEY: return parseBlockMappingKey(false);
      case BLOCK_MAPPING_VALUE: return parseBlockMappingValue();
      case FLOW_SEQUENCE_FIRST_ENTRY: return parseFlowSequenceEntry(true);
      case FLOW_SEQUENCE_ENTRY: return parseFlowSequenceEntry(false);
      case FLOW_SEQUENCE_ENTRY_MAPPING_KEY: return parseFlowSequenceEntryMappingKey();
      case FLOW_SEQUENCE_ENTRY_MAPPING_VALUE: return parseFlowSequenceEntryMappingValue();
      case FLOW_SEQUENCE_ENTRY_MAPPING_END: return parseFlowSequenceEntryMappingEnd();
      case FLOW_MAPPING_FIRST_KEY: return parseFlowMappingKey(true);
      case FLOW_MAPPING_KEY: return parseFlowMappingKey(false);
      case FLOW_MAPPING_VALUE: return parseFlowMappingValue(false);
      case FLOW_MAPPING_EMPTY_VALUE: return parseFlowMappingValue(true);
      default: throw new IllegalStateException("invalid state");
    }
  }

  /** yaml_parser_parse_stream_start */
  private Event parseStreamStart() {
    Token token = peekToken();
    if (token.type != Token.Type.STREAM_START) {
      throw parserError("did not find expected <stream-start>", token.startMark);
    }
    state = State.IMPLICIT_DOCUMENT_START;
    Event event = new Event(Event.Type.STREAM_START, token.startMark, token.startMark);
    event.encoding = token.encoding;
    skipToken();
    return event;
  }

  /** yaml_parser_parse_document_start */
  private Event parseDocumentStart(boolean implicit) {
    Token token = peekToken();
    // Parse extra document end indicators.
    if (!implicit) {
      while (token.type == Token.Type.DOCUMENT_END) {
        skipToken();
        token = peekToken();
      }
    }
    if (implicit && token.type != Token.Type.VERSION_DIRECTIVE && token.type != Token.Type.TAG_DIRECTIVE
        && token.type != Token.Type.DOCUMENT_START && token.type != Token.Type.STREAM_END) {
      // Parse an implicit document.
      processDirectives(null, null);
      states.add(State.DOCUMENT_END);
      state = State.BLOCK_NODE;
      Event event = new Event(Event.Type.DOCUMENT_START, token.startMark, token.startMark);
      event.implicit = true;
      return event;
    } else if (token.type != Token.Type.STREAM_END) {
      // Parse an explicit document.
      Mark startMark = token.startMark;
      int[][] versionDirective = {null};
      List<byte[][]> docTagDirectives = new ArrayList<>();
      processDirectives(versionDirective, docTagDirectives);
      token = peekToken();
      if (token.type != Token.Type.DOCUMENT_START) {
        throw parserError("did not find expected <document start>", token.startMark);
      }
      states.add(State.DOCUMENT_END);
      state = State.DOCUMENT_CONTENT;
      Mark endMark = token.endMark;
      Event event = new Event(Event.Type.DOCUMENT_START, startMark, endMark);
      event.versionDirective = versionDirective[0];
      event.tagDirectives = docTagDirectives;
      event.implicit = false;
      skipToken();
      return event;
    } else {
      // Parse the stream end.
      state = State.END;
      Event event = new Event(Event.Type.STREAM_END, token.startMark, token.endMark);
      skipToken();
      return event;
    }
  }

  /** yaml_parser_parse_document_content */
  private Event parseDocumentContent() {
    Token token = peekToken();
    if (token.type == Token.Type.VERSION_DIRECTIVE || token.type == Token.Type.TAG_DIRECTIVE || token.type == Token.Type.DOCUMENT_START
        || token.type == Token.Type.DOCUMENT_END || token.type == Token.Type.STREAM_END) {
      state = pop(states);
      return processEmptyScalar(token.startMark);
    }
    return parseNode(true, false);
  }

  /** yaml_parser_parse_document_end */
  private Event parseDocumentEnd() {
    Token token = peekToken();
    Mark startMark = token.startMark;
    Mark endMark = token.startMark;
    boolean implicit = true;
    if (token.type == Token.Type.DOCUMENT_END) {
      endMark = token.endMark;
      skipToken();
      implicit = false;
    }
    tagDirectives.clear();
    state = State.DOCUMENT_START;
    Event event = new Event(Event.Type.DOCUMENT_END, startMark, endMark);
    event.implicit = implicit;
    return event;
  }

  private static boolean cstrEquals(byte[] a, byte[] b) {
    return Arrays.equals(a, b);
  }

  /** yaml_parser_parse_node */
  private Event parseNode(boolean block, boolean indentlessSequence) {
    byte[] anchor = null;
    byte[] tagHandle = null;
    byte[] tagSuffix = null;
    byte[] tag = null;
    Mark startMark;
    Mark endMark;
    Mark tagMark = null;
    Token token = peekToken();
    if (token.type == Token.Type.ALIAS) {
      state = pop(states);
      Event event = new Event(Event.Type.ALIAS, token.startMark, token.endMark);
      event.anchor = token.value;
      skipToken();
      return event;
    }
    startMark = endMark = token.startMark;
    if (token.type == Token.Type.ANCHOR) {
      anchor = token.value;
      startMark = token.startMark;
      endMark = token.endMark;
      skipToken();
      token = peekToken();
      if (token.type == Token.Type.TAG) {
        tagHandle = token.handle;
        tagSuffix = token.suffix;
        tagMark = token.startMark;
        endMark = token.endMark;
        skipToken();
        token = peekToken();
      }
    } else if (token.type == Token.Type.TAG) {
      tagHandle = token.handle;
      tagSuffix = token.suffix;
      startMark = tagMark = token.startMark;
      endMark = token.endMark;
      skipToken();
      token = peekToken();
      if (token.type == Token.Type.ANCHOR) {
        anchor = token.value;
        endMark = token.endMark;
        skipToken();
        token = peekToken();
      }
    }
    if (tagHandle != null) {
      if (tagHandle.length == 0) {
        tag = tagSuffix;
      } else {
        for (byte[][] tagDirective : tagDirectives) {
          if (cstrEquals(tagDirective[0], tagHandle)) {
            byte[] prefix = tagDirective[1];
            tag = Arrays.copyOf(prefix, prefix.length + tagSuffix.length);
            System.arraycopy(tagSuffix, 0, tag, prefix.length, tagSuffix.length);
            break;
          }
        }
        if (tag == null) {
          throw parserErrorContext("while parsing a node", startMark, "found undefined tag handle", tagMark);
        }
      }
    }
    boolean implicit = tag == null || tag.length == 0;
    if (indentlessSequence && token.type == Token.Type.BLOCK_ENTRY) {
      endMark = token.endMark;
      state = State.INDENTLESS_SEQUENCE_ENTRY;
      return collectionStart(Event.Type.SEQUENCE_START, anchor, tag, implicit, Event.BLOCK_STYLE, startMark, endMark);
    }
    if (token.type == Token.Type.SCALAR) {
      boolean plainImplicit = false;
      boolean quotedImplicit = false;
      endMark = token.endMark;
      if ((token.style == Token.PLAIN_SCALAR_STYLE && tag == null) || (tag != null && cstrEquals(tag, BANG))) {
        plainImplicit = true;
      } else if (tag == null) {
        quotedImplicit = true;
      }
      state = pop(states);
      Event event = new Event(Event.Type.SCALAR, startMark, endMark);
      event.anchor = anchor;
      event.tag = tag;
      event.value = token.value;
      event.plainImplicit = plainImplicit;
      event.quotedImplicit = quotedImplicit;
      event.style = token.style;
      skipToken();
      return event;
    } else if (token.type == Token.Type.FLOW_SEQUENCE_START) {
      endMark = token.endMark;
      state = State.FLOW_SEQUENCE_FIRST_ENTRY;
      return collectionStart(Event.Type.SEQUENCE_START, anchor, tag, implicit, Event.FLOW_STYLE, startMark, endMark);
    } else if (token.type == Token.Type.FLOW_MAPPING_START) {
      endMark = token.endMark;
      state = State.FLOW_MAPPING_FIRST_KEY;
      return collectionStart(Event.Type.MAPPING_START, anchor, tag, implicit, Event.FLOW_STYLE, startMark, endMark);
    } else if (block && token.type == Token.Type.BLOCK_SEQUENCE_START) {
      endMark = token.endMark;
      state = State.BLOCK_SEQUENCE_FIRST_ENTRY;
      return collectionStart(Event.Type.SEQUENCE_START, anchor, tag, implicit, Event.BLOCK_STYLE, startMark, endMark);
    } else if (block && token.type == Token.Type.BLOCK_MAPPING_START) {
      endMark = token.endMark;
      state = State.BLOCK_MAPPING_FIRST_KEY;
      return collectionStart(Event.Type.MAPPING_START, anchor, tag, implicit, Event.BLOCK_STYLE, startMark, endMark);
    } else if (anchor != null || tag != null) {
      state = pop(states);
      Event event = new Event(Event.Type.SCALAR, startMark, endMark);
      event.anchor = anchor;
      event.tag = tag;
      event.value = new byte[0];
      event.plainImplicit = implicit;
      event.quotedImplicit = false;
      event.style = Token.PLAIN_SCALAR_STYLE;
      return event;
    } else {
      throw parserErrorContext(block ? "while parsing a block node" : "while parsing a flow node", startMark,
          "did not find expected node content", token.startMark);
    }
  }

  private static Event collectionStart(Event.Type type, byte[] anchor, byte[] tag, boolean implicit, int style, Mark startMark, Mark endMark) {
    Event event = new Event(type, startMark, endMark);
    event.anchor = anchor;
    event.tag = tag;
    event.implicit = implicit;
    event.style = style;
    return event;
  }

  /** yaml_parser_parse_block_sequence_entry */
  private Event parseBlockSequenceEntry(boolean first) {
    Token token;
    if (first) {
      token = peekToken();
      marks.add(token.startMark);
      skipToken();
    }
    token = peekToken();
    if (token.type == Token.Type.BLOCK_ENTRY) {
      Mark mark = token.endMark;
      skipToken();
      token = peekToken();
      if (token.type != Token.Type.BLOCK_ENTRY && token.type != Token.Type.BLOCK_END) {
        states.add(State.BLOCK_SEQUENCE_ENTRY);
        return parseNode(true, false);
      } else {
        state = State.BLOCK_SEQUENCE_ENTRY;
        return processEmptyScalar(mark);
      }
    } else if (token.type == Token.Type.BLOCK_END) {
      state = pop(states);
      pop(marks);
      Event event = new Event(Event.Type.SEQUENCE_END, token.startMark, token.endMark);
      skipToken();
      return event;
    } else {
      throw parserErrorContext("while parsing a block collection", pop(marks), "did not find expected '-' indicator", token.startMark);
    }
  }

  /** yaml_parser_parse_indentless_sequence_entry */
  private Event parseIndentlessSequenceEntry() {
    Token token = peekToken();
    if (token.type == Token.Type.BLOCK_ENTRY) {
      Mark mark = token.endMark;
      skipToken();
      token = peekToken();
      if (token.type != Token.Type.BLOCK_ENTRY && token.type != Token.Type.KEY && token.type != Token.Type.VALUE
          && token.type != Token.Type.BLOCK_END) {
        states.add(State.INDENTLESS_SEQUENCE_ENTRY);
        return parseNode(true, false);
      } else {
        state = State.INDENTLESS_SEQUENCE_ENTRY;
        return processEmptyScalar(mark);
      }
    } else {
      state = pop(states);
      return new Event(Event.Type.SEQUENCE_END, token.startMark, token.startMark);
    }
  }

  /** yaml_parser_parse_block_mapping_key */
  private Event parseBlockMappingKey(boolean first) {
    Token token;
    if (first) {
      token = peekToken();
      marks.add(token.startMark);
      skipToken();
    }
    token = peekToken();
    if (token.type == Token.Type.KEY) {
      Mark mark = token.endMark;
      skipToken();
      token = peekToken();
      if (token.type != Token.Type.KEY && token.type != Token.Type.VALUE && token.type != Token.Type.BLOCK_END) {
        states.add(State.BLOCK_MAPPING_VALUE);
        return parseNode(true, true);
      } else {
        state = State.BLOCK_MAPPING_VALUE;
        return processEmptyScalar(mark);
      }
    } else if (token.type == Token.Type.BLOCK_END) {
      state = pop(states);
      pop(marks);
      Event event = new Event(Event.Type.MAPPING_END, token.startMark, token.endMark);
      skipToken();
      return event;
    } else if (yaml110 && token.type == Token.Type.VALUE) {
      // yaml110: a value without a key (": x" in a block mapping) has an empty key in yaml@1.10
      state = State.BLOCK_MAPPING_VALUE;
      return processEmptyScalar(token.startMark);
    } else {
      throw parserErrorContext("while parsing a block mapping", pop(marks), "did not find expected key", token.startMark);
    }
  }

  /** yaml_parser_parse_block_mapping_value */
  private Event parseBlockMappingValue() {
    Token token = peekToken();
    if (token.type == Token.Type.VALUE) {
      Mark mark = token.endMark;
      skipToken();
      token = peekToken();
      if (token.type != Token.Type.KEY && token.type != Token.Type.VALUE && token.type != Token.Type.BLOCK_END) {
        states.add(State.BLOCK_MAPPING_KEY);
        return parseNode(true, true);
      } else {
        state = State.BLOCK_MAPPING_KEY;
        return processEmptyScalar(mark);
      }
    } else {
      state = State.BLOCK_MAPPING_KEY;
      return processEmptyScalar(token.startMark);
    }
  }

  /** yaml_parser_parse_flow_sequence_entry */
  private Event parseFlowSequenceEntry(boolean first) {
    Token token;
    if (first) {
      token = peekToken();
      marks.add(token.startMark);
      skipToken();
    }
    token = peekToken();
    if (token.type != Token.Type.FLOW_SEQUENCE_END) {
      if (!first) {
        if (token.type == Token.Type.FLOW_ENTRY) {
          skipToken();
          token = peekToken();
        } else {
          throw parserErrorContext("while parsing a flow sequence", pop(marks), "did not find expected ',' or ']'", token.startMark);
        }
      }
      if (token.type == Token.Type.KEY) {
        state = State.FLOW_SEQUENCE_ENTRY_MAPPING_KEY;
        Event event = collectionStart(Event.Type.MAPPING_START, null, null, true, Event.FLOW_STYLE, token.startMark, token.endMark);
        skipToken();
        return event;
      } else if (token.type != Token.Type.FLOW_SEQUENCE_END) {
        states.add(State.FLOW_SEQUENCE_ENTRY);
        return parseNode(false, false);
      }
    }
    state = pop(states);
    pop(marks);
    Event event = new Event(Event.Type.SEQUENCE_END, token.startMark, token.endMark);
    skipToken();
    return event;
  }

  /** yaml_parser_parse_flow_sequence_entry_mapping_key */
  private Event parseFlowSequenceEntryMappingKey() {
    Token token = peekToken();
    if (token.type != Token.Type.VALUE && token.type != Token.Type.FLOW_ENTRY && token.type != Token.Type.FLOW_SEQUENCE_END) {
      states.add(State.FLOW_SEQUENCE_ENTRY_MAPPING_VALUE);
      return parseNode(false, false);
    } else {
      Mark mark = token.endMark;
      skipToken();
      state = State.FLOW_SEQUENCE_ENTRY_MAPPING_VALUE;
      return processEmptyScalar(mark);
    }
  }

  /** yaml_parser_parse_flow_sequence_entry_mapping_value */
  private Event parseFlowSequenceEntryMappingValue() {
    Token token = peekToken();
    if (token.type == Token.Type.VALUE) {
      skipToken();
      token = peekToken();
      if (token.type != Token.Type.FLOW_ENTRY && token.type != Token.Type.FLOW_SEQUENCE_END) {
        states.add(State.FLOW_SEQUENCE_ENTRY_MAPPING_END);
        return parseNode(false, false);
      }
    }
    state = State.FLOW_SEQUENCE_ENTRY_MAPPING_END;
    return processEmptyScalar(token.startMark);
  }

  /** yaml_parser_parse_flow_sequence_entry_mapping_end */
  private Event parseFlowSequenceEntryMappingEnd() {
    Token token = peekToken();
    state = State.FLOW_SEQUENCE_ENTRY;
    return new Event(Event.Type.MAPPING_END, token.startMark, token.startMark);
  }

  /** yaml_parser_parse_flow_mapping_key */
  private Event parseFlowMappingKey(boolean first) {
    Token token;
    if (first) {
      token = peekToken();
      marks.add(token.startMark);
      skipToken();
    }
    token = peekToken();
    if (token.type != Token.Type.FLOW_MAPPING_END) {
      if (!first) {
        if (token.type == Token.Type.FLOW_ENTRY) {
          skipToken();
          token = peekToken();
        } else {
          throw parserErrorContext("while parsing a flow mapping", pop(marks), "did not find expected ',' or '}'", token.startMark);
        }
      }
      if (token.type == Token.Type.KEY) {
        skipToken();
        token = peekToken();
        if (token.type != Token.Type.VALUE && token.type != Token.Type.FLOW_ENTRY && token.type != Token.Type.FLOW_MAPPING_END) {
          states.add(State.FLOW_MAPPING_VALUE);
          return parseNode(false, false);
        } else {
          state = State.FLOW_MAPPING_VALUE;
          return processEmptyScalar(token.startMark);
        }
      } else if (token.type != Token.Type.FLOW_MAPPING_END) {
        states.add(State.FLOW_MAPPING_EMPTY_VALUE);
        return parseNode(false, false);
      }
    }
    state = pop(states);
    pop(marks);
    Event event = new Event(Event.Type.MAPPING_END, token.startMark, token.endMark);
    skipToken();
    return event;
  }

  /** yaml_parser_parse_flow_mapping_value */
  private Event parseFlowMappingValue(boolean empty) {
    Token token = peekToken();
    if (empty) {
      state = State.FLOW_MAPPING_KEY;
      return processEmptyScalar(token.startMark);
    }
    if (token.type == Token.Type.VALUE) {
      skipToken();
      token = peekToken();
      if (token.type != Token.Type.FLOW_ENTRY && token.type != Token.Type.FLOW_MAPPING_END) {
        states.add(State.FLOW_MAPPING_KEY);
        return parseNode(false, false);
      }
    }
    state = State.FLOW_MAPPING_KEY;
    return processEmptyScalar(token.startMark);
  }

  /** yaml_parser_process_empty_scalar */
  private static Event processEmptyScalar(Mark mark) {
    Event event = new Event(Event.Type.SCALAR, mark, mark);
    event.value = new byte[0];
    event.plainImplicit = true;
    event.quotedImplicit = false;
    event.style = Token.PLAIN_SCALAR_STYLE;
    return event;
  }

  /**
   * yaml_parser_process_directives: {@code versionDirectiveRef}/{@code docTagDirectives} are null
   * for an implicit document (the C NULL refs).
   */
  private void processDirectives(int[][] versionDirectiveRef, List<byte[][]> docTagDirectives) {
    int[] versionDirective = null;
    Token token = peekToken();
    while (token.type == Token.Type.VERSION_DIRECTIVE || token.type == Token.Type.TAG_DIRECTIVE) {
      if (token.type == Token.Type.VERSION_DIRECTIVE) {
        if (versionDirective != null) {
          throw parserError("found duplicate %YAML directive", token.startMark);
        }
        if (token.major != 1 || (token.minor != 1 && token.minor != 2)) {
          throw parserError("found incompatible YAML document", token.startMark);
        }
        versionDirective = new int[] {token.major, token.minor};
      } else {
        byte[][] value = {token.handle, token.suffix};
        appendTagDirective(value, false, token.startMark);
        if (docTagDirectives != null) {
          docTagDirectives.add(value);
        }
      }
      skipToken();
      token = peekToken();
    }
    for (byte[][] defaultTagDirective : DEFAULT_TAG_DIRECTIVES) {
      appendTagDirective(defaultTagDirective, true, token.startMark);
    }
    if (versionDirectiveRef != null) {
      versionDirectiveRef[0] = versionDirective;
    }
  }

  /** yaml_parser_append_tag_directive */
  private void appendTagDirective(byte[][] value, boolean allowDuplicates, Mark mark) {
    for (byte[][] tagDirective : tagDirectives) {
      if (cstrEquals(value[0], tagDirective[0])) {
        if (allowDuplicates) {
          return;
        }
        throw parserError("found duplicate %TAG directive", mark);
      }
    }
    tagDirectives.add(new byte[][] {value[0].clone(), value[1].clone()});
  }
}

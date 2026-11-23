package org.hl7.fhir.sushi.importer.parser;

// port of the token model of the antlr4 JS runtime 4.13.2 (CommonToken), as used by SUSHI 3.21.1
/**
 * A lexer token. Indices and columns count UTF-16 code units, like SUSHI's JS runtime ({@code InputStream} without
 * code point decoding); lines are 1-based, columns 0-based. {@code start}/{@code stop} are inclusive char indices
 * into the input; a token conjured during error recovery ("missing X") has start = stop = tokenIndex = -1.
 */
public final class Token {
  public static final int EOF = -1;
  public static final int INVALID_TYPE = 0;
  public static final int EPSILON = -2;
  public static final int DEFAULT_CHANNEL = 0;
  public static final int HIDDEN_CHANNEL = 1;

  private final InputText input;
  private final int type;
  private final int channel;
  private final int start;
  private final int stop;
  private final int line;
  private final int column;
  private final String text;
  private int tokenIndex = -1;

  /** Text null = the input text between start and stop (JS CommonToken.text: "&lt;EOF&gt;" past the end). */
  public Token(InputText input, int type, String text, int channel, int start, int stop, int line, int column) {
    this.input = input;
    this.type = type;
    this.channel = channel;
    this.start = start;
    this.stop = stop;
    this.line = line;
    this.column = column;
    if (text == null && input != null) {
      int n = input.size();
      text = start < n && stop < n ? input.getText(start, stop) : "<EOF>";
    }
    this.text = text;
  }

  public int getType() { return type; }
  public String getText() { return text; }
  public int getChannel() { return channel; }
  /** First char index (inclusive); TS {@code token.start}. */
  public int getStartIndex() { return start; }
  /** Last char index (inclusive); TS {@code token.stop}. */
  public int getStopIndex() { return stop; }
  public int getLine() { return line; }
  /** 0-based UTF-16 column; TS {@code token.column}. */
  public int getCharPositionInLine() { return column; }
  /** Index in the token stream (all channels); -1 for a conjured token. TS {@code token.tokenIndex}. */
  public int getTokenIndex() { return tokenIndex; }
  public void setTokenIndex(int i) { tokenIndex = i; }
  /** The whole input; TS {@code token.getInputStream()}. */
  public InputText getInputStream() { return input; }

  @Override public String toString() {
    String t = text == null ? "<no text>" : text.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    return "[@" + tokenIndex + "," + start + ":" + stop + "='" + t + "',<" + type + ">"
        + (channel > 0 ? ",channel=" + channel : "") + "," + line + ":" + column + "]";
  }
}

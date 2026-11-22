package org.hl7.fhir.jekyll.liquid;

/**
 * Port of liquid-4.0.4/lib/liquid/errors.rb. Ruby has one subclass of {@code Liquid::Error} per
 * kind; here the kind is an enum ({@link #kind()}), and {@link #rubyClassName()} gives the Ruby name.
 *
 * <p>{@link #getMessage()} is Ruby's {@code Error#to_s}: prefix ("Liquid syntax error (line 3): "),
 * base message, then the markup context. The prefix is computed lazily because Ruby mutates
 * {@code line_number}/{@code template_name} after construction.
 */
public class LiquidException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public enum Kind {
    ARGUMENT("ArgumentError"),
    CONTEXT("ContextError"),
    FILE_SYSTEM("FileSystemError"),
    STANDARD("StandardError"),
    SYNTAX("SyntaxError"),
    STACK_LEVEL("StackLevelError"),
    TAINTED("TaintedError"),
    MEMORY("MemoryError"),
    ZERO_DIVISION("ZeroDivisionError"),
    FLOAT_DOMAIN("FloatDomainError"),
    UNDEFINED_VARIABLE("UndefinedVariable"),
    UNDEFINED_DROP_METHOD("UndefinedDropMethod"),
    UNDEFINED_FILTER("UndefinedFilter"),
    METHOD_OVERRIDE("MethodOverrideError"),
    INTERNAL("InternalError");

    final String rubyName;

    Kind(String rubyName) {
      this.rubyName = rubyName;
    }
  }

  private final Kind kind;
  private final String baseMessage;
  private Integer lineNumber;
  private String templateName;
  private String markupContext;

  public LiquidException(Kind kind, String message) {
    this(kind, message, null);
  }

  public LiquidException(Kind kind, String message, Throwable cause) {
    super(message, cause, false, true);
    this.kind = kind;
    this.baseMessage = message == null ? "Liquid::" + kind.rubyName : message;
  }

  public static LiquidException syntax(String message) {
    return new LiquidException(Kind.SYNTAX, message);
  }

  public static LiquidException argument(String message) {
    return new LiquidException(Kind.ARGUMENT, message);
  }

  public Kind kind() {
    return kind;
  }

  public boolean isSyntax() {
    return kind == Kind.SYNTAX;
  }

  public String rubyClassName() {
    return "Liquid::" + kind.rubyName;
  }

  /** The message without prefix/markup context (Ruby {@code super()} inside {@code to_s}). */
  public String baseMessage() {
    return baseMessage;
  }

  public Integer lineNumber() {
    return lineNumber;
  }

  public LiquidException lineNumber(Integer n) {
    this.lineNumber = n;
    return this;
  }

  public String templateName() {
    return templateName;
  }

  public LiquidException templateName(String n) {
    this.templateName = n;
    return this;
  }

  public String markupContext() {
    return markupContext;
  }

  public LiquidException markupContext(String c) {
    this.markupContext = c;
    return this;
  }

  /** Ruby {@code to_s(with_prefix)}. */
  public String toS(boolean withPrefix) {
    StringBuilder str = new StringBuilder();
    if (withPrefix) {
      str.append(messagePrefix());
    }
    str.append(baseMessage);
    if (markupContext != null) {
      str.append(' ').append(markupContext);
    }
    return str.toString();
  }

  private String messagePrefix() {
    StringBuilder str = new StringBuilder();
    str.append(kind == Kind.SYNTAX ? "Liquid syntax error" : "Liquid error");
    if (lineNumber != null) {
      str.append(" (");
      if (templateName != null) {
        str.append(templateName).append(' ');
      }
      str.append("line ").append(lineNumber).append(')');
    }
    str.append(": ");
    return str.toString();
  }

  @Override
  public String getMessage() {
    return toS(true);
  }

  @Override
  public String toString() {
    return toS(true);
  }
}

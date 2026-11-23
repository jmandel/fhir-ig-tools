package org.hl7.fhir.jekyll.liquid;

/**
 * Emulates the core Ruby exceptions (::ArgumentError, ::TypeError, ::NoMethodError,
 * ::ZeroDivisionError, ...) that Liquid code paths raise and then either convert to a Liquid error
 * (Strainer turns ::ArgumentError into Liquid::ArgumentError) or render as
 * {@code Liquid error: internal}. Not a {@link LiquidException}, exactly like in Ruby.
 */
public class RubyError extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final String rubyClass;

  public RubyError(String rubyClass, String message) {
    super(message, null, false, true);
    this.rubyClass = rubyClass;
  }

  public String rubyClass() {
    return rubyClass;
  }

  public boolean isArgumentError() {
    return "ArgumentError".equals(rubyClass);
  }

  public static RubyError argumentError(String msg) {
    return new RubyError("ArgumentError", msg);
  }

  public static RubyError typeError(String msg) {
    return new RubyError("TypeError", msg);
  }

  public static RubyError zeroDivision() {
    return new RubyError("ZeroDivisionError", "divided by 0");
  }

  /** Ruby 3.4's NoMethodError message: "for nil" / "for true" / "for false", else "for an instance of Class". */
  public static RubyError noMethod(String method, Object receiver) {
    return new RubyError("NoMethodError", "undefined method '" + method + "' for " + receiverDescription(receiver));
  }

  /** The receiver part of Ruby 3.4's NoMethodError message. */
  public static String receiverDescription(Object receiver) {
    if (receiver == null || receiver instanceof Boolean) {
      return Values.inspect(receiver);
    }
    return "an instance of " + Values.className(receiver);
  }

  /** Ruby's wrong-arity message; given/expected count the filter input too. */
  public static RubyError arity(int given, int min, int max) {
    String exp = min == max ? String.valueOf(min) : max < 0 ? min + "+" : min + ".." + max;
    return argumentError("wrong number of arguments (given " + given + ", expected " + exp + ")");
  }

  @Override
  public String toString() {
    return rubyClass + ": " + getMessage();
  }
}

package org.hl7.fhir.jekyll.liquid;

/** Port of liquid-4.0.4/lib/liquid/interrupts.rb. */
public class Interrupt {
  private final String message;

  public Interrupt(String message) {
    this.message = message == null ? "interrupt" : message;
  }

  public String message() {
    return message;
  }

  /** Interrupt that is thrown whenever a {% break %} is called. */
  public static final class BreakInterrupt extends Interrupt {
    public BreakInterrupt() {
      super(null);
    }
  }

  /** Interrupt that is thrown whenever a {% continue %} is called. */
  public static final class ContinueInterrupt extends Interrupt {
    public ContinueInterrupt() {
      super(null);
    }
  }
}

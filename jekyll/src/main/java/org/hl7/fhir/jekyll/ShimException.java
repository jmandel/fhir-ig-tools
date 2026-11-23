package org.hl7.fhir.jekyll;

/**
 * The build failed where Jekyll's would: a Liquid error, a missing or invalid include, a failing
 * {@link IncludeProvider} ({@link #isRenderFailure()}; the message is the text Jekyll prints after
 * "Liquid Exception: "), or an I/O / configuration problem (the cause).
 */
public class ShimException extends Exception {
  private static final long serialVersionUID = 1L;
  private final boolean renderFailure;

  public ShimException(String message, Throwable cause, boolean renderFailure) {
    super(message, cause);
    this.renderFailure = renderFailure;
  }

  /** True for the failures Jekyll reports as "Liquid Exception: …" (exit status 1). */
  public boolean isRenderFailure() {
    return renderFailure;
  }
}

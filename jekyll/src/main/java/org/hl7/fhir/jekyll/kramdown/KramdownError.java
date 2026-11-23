package org.hl7.fhir.jekyll.kramdown;

/** Port of kramdown-2.6.1/lib/kramdown/error.rb (Kramdown::Error). */
public class KramdownError extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public KramdownError(String msg) {
    super(msg);
  }
}

package org.hl7.fhir.jekyll.liquid.tags;

import org.hl7.fhir.jekyll.liquid.Interrupt;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.Tag;

/** Port of liquid-4.0.4/lib/liquid/tags/continue.rb. */
public class Continue extends Tag {
  public Continue(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
  }

  public Interrupt interrupt() {
    return new Interrupt.ContinueInterrupt();
  }
}

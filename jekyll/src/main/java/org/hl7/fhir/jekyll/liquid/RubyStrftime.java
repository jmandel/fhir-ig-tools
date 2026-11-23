package org.hl7.fhir.jekyll.liquid;

/**
 * A value that responds to Ruby's {@code strftime} other than {@link RubyTime} (e.g. a Ruby Date
 * from YAML data). Liquid's {@code Utils.to_date} returns such objects unchanged, and the `date`
 * filter calls their own strftime.
 */
public interface RubyStrftime {
  String strftime(String format);
}

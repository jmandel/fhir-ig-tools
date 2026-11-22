package org.hl7.fhir.jekyll.liquid;

import java.util.List;

/**
 * A Liquid filter (a public method of a filter module in Ruby). {@code args} are the evaluated
 * positional arguments; keyword arguments (Ruby passes a trailing Hash) arrive as a final
 * {@link KeywordArgs} element.
 */
@FunctionalInterface
public interface Filter {
  Object apply(Context context, Object input, List<Object> args);
}

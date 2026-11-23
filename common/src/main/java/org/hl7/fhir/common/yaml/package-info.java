/**
 * The YAML event layer: a faithful port of libyaml 0.2.5's reader, scanner and parser
 * ({@code reader.c}, {@code scanner.c}, {@code parser.c}). {@link org.hl7.fhir.common.yaml.Parser}
 * turns bytes into {@link org.hl7.fhir.common.yaml.Event}s exactly as {@code yaml_parser_parse} does,
 * and errors surface as {@link org.hl7.fhir.common.yaml.LibyamlError} with libyaml's problem, context
 * and marks.
 *
 * <p>No value construction happens here. Consumers build their own values from the events: the jekyll
 * module's Psych/SafeYAML layer ({@code org.hl7.fhir.jekyll.yaml}) reproduces Ruby's typing, and a
 * consumer with different YAML semantics (for example a YAML 1.2 core-schema composer) can sit on the
 * same events.
 */
package org.hl7.fhir.common.yaml;

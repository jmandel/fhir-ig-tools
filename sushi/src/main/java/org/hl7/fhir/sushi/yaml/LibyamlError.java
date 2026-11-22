// Vendored from fhir-ig-jekyll@ecd3c065 shim/src/main/java/org/hl7/fhir/jekyll/yaml/LibyamlError.java (libyaml 0.2.5 event layer),
// unchanged except the package line, until the monorepo move puts it in common (PORTING.md section 7).
package org.hl7.fhir.sushi.yaml;

/**
 * The error state libyaml records in {@code yaml_parser_t} ({@code error}, {@code problem},
 * {@code problem_offset}, {@code problem_value}, {@code problem_mark}, {@code context},
 * {@code context_mark}) when a function "returns 0". Thrown instead of the C return-0 chain; every
 * libyaml error is terminal, so this is equivalent. Psych turns it into Psych::SyntaxError.
 */
final class LibyamlError extends RuntimeException {
  /** yaml_error_type_t */
  static final int READER_ERROR = 2, SCANNER_ERROR = 3, PARSER_ERROR = 4;

  final int error;
  final String problem;
  final long problemOffset;
  final int problemValue;
  final Mark problemMark;
  final String context;
  /** Mark.ZERO unless the error set it (yaml_parser_t is zeroed by yaml_parser_initialize). */
  final Mark contextMark;

  LibyamlError(int error, String problem, long problemOffset, int problemValue, Mark problemMark, String context, Mark contextMark) {
    super(problem, null, false, false);
    this.error = error;
    this.problem = problem;
    this.problemOffset = problemOffset;
    this.problemValue = problemValue;
    this.problemMark = problemMark;
    this.context = context;
    this.contextMark = contextMark;
  }
}

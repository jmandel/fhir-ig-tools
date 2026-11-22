package org.hl7.fhir.common.yaml;

/**
 * The error state libyaml records in {@code yaml_parser_t} ({@code error}, {@code problem},
 * {@code problem_offset}, {@code problem_value}, {@code problem_mark}, {@code context},
 * {@code context_mark}) when a function "returns 0". Thrown instead of the C return-0 chain; every
 * libyaml error is terminal, so this is equivalent. Psych turns it into Psych::SyntaxError (the
 * jekyll module's PsychSyntaxError); other consumers word their own messages from these fields.
 */
public final class LibyamlError extends RuntimeException {
  /** yaml_error_type_t */
  public static final int READER_ERROR = 2, SCANNER_ERROR = 3, PARSER_ERROR = 4;

  public final int error;
  public final String problem;
  public final long problemOffset;
  public final int problemValue;
  public final Mark problemMark;
  public final String context;
  /** Mark.ZERO unless the error set it (yaml_parser_t is zeroed by yaml_parser_initialize). */
  public final Mark contextMark;

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

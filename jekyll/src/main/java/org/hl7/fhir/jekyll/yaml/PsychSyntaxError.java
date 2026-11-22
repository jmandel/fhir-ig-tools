package org.hl7.fhir.jekyll.yaml;

/**
 * Port of psych-5.0.1/lib/psych/syntax_error.rb, built the way psych_parser.c's
 * {@code make_exception} does: line/column come from libyaml's {@code context_mark} (+1), which
 * is zero for reader errors and context-less parser errors ("... at line 1 column 1").
 */
public final class PsychSyntaxError extends YamlException {
  private final String file;
  private final long line;
  private final long column;
  private final long offset;
  private final String problem;
  private final String context;

  PsychSyntaxError(String file, long line, long column, long offset, String problem, String context) {
    super("Psych::SyntaxError", message(file, line, column, problem, context));
    this.file = file;
    this.line = line;
    this.column = column;
    this.offset = offset;
    this.problem = problem;
    this.context = context;
  }

  private static String message(String file, long line, long column, String problem, String context) {
    StringBuilder err = new StringBuilder();
    if (problem != null) {
      err.append(problem);
    }
    if (context != null) {
      if (problem != null) {
        err.append(' ');
      }
      err.append(context);
    }
    return "(" + (file != null ? file : "<unknown>") + "): " + err + " at line " + line + " column " + column;
  }

  public String file() {
    return file;
  }

  public long line() {
    return line;
  }

  public long column() {
    return column;
  }

  public long offset() {
    return offset;
  }

  public String problem() {
    return problem;
  }

  public String context() {
    return context;
  }
}

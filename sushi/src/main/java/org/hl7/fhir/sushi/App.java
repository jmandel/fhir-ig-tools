package org.hl7.fhir.sushi;

// port of sushi/src/app.ts -- CONTRACT SKELETON, owned by WP-8 (IG + CLI).
/**
 * CLI: {@code java -jar sushi-java.jar build [path] [-o out] [-s] [-r] [-c key:value] [-l level] ...} with the same
 * options, console output (see utils.FshLogger) and exit status as TS SUSHI 3.21.1 (exit code = number of errors,
 * capped by the OS at 255 as in node's process.exit).
 */
public final class App {
  private App() {}

  public static void main(String[] args) {
    System.exit(run(args));
  }

  public static int run(String[] args) {
    throw new UnsupportedOperationException("TODO WP-8");
  }
}

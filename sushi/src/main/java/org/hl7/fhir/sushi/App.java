package org.hl7.fhir.sushi;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.export.ExportFHIR;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.ig.IGExporter;
import org.hl7.fhir.sushi.ig.PredefinedResources;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.importer.RawFSH;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.NodePath;
import org.hl7.fhir.sushi.utils.Processing;
import org.hl7.fhir.sushi.utils.Puns;

// port of sushi/src/app.ts (SUSHI 3.21.1)
/**
 * CLI: {@code java -jar sushi.jar [build] [path] [-o out] [-p] [-s] [-r] [-c key:value]... [-l level]},
 * {@code init [name]}, {@code update-dependencies [path]}, with the same options (commander 13 behaviour: help/error
 * texts, default command, combined short flags), console output (utils.FshLogger + the summary box) and exit status
 * (number of errors; the OS keeps the low 8 bits, as with node's process.exit) as TS SUSHI 3.21.1.
 *
 * <p>Deliberate differences (PORTING D7, docs/core-divergences.md T3): the latest-version check is off unless enabled
 * ({@code -Dsushi.versionCheck=true} / {@code SUSHI_VERSION_CHECK=true}); it queries the npm registry over HTTPS
 * instead of running {@code npm view}. {@code --require-latest} checks that this sushi.jar is from the latest
 * fhir-ig-tools release (GitHub), not that npm has no newer fsh-sushi.
 */
public final class App {
  private App() {}

  static final String FSH_VERSION = "3.0.0";

  /** Thrown to end the run with process.exit(code) semantics (caught in {@link #run}). */
  static final class Exit extends RuntimeException {
    private static final long serialVersionUID = 1L;
    final int code;

    Exit(int code) {
      super(null, null, false, false);
      this.code = code;
    }
  }

  /** stdout as node writes it (UTF-8); console.log and the logger share it. */
  static PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
  static PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);

  public static void main(String[] args) {
    // core's package layer logs through SLF4J without a provider; keep its "no providers" notice off stderr
    if (System.getProperty("slf4j.internal.verbosity") == null) System.setProperty("slf4j.internal.verbosity", "ERROR");
    // network access goes through core's ManagedWebAccess (PORTING.md "I/O policy"); as the IG Publisher does, apply
    // fhir-settings.json (~/.fhir/fhir-settings.json, or -Dfhir.settings.path): prohibitNetworkAccess, servers and
    // their authentication, SSRF protection
    org.hl7.fhir.utilities.http.ManagedWebAccess.loadFromFHIRSettings();
    int code = run(args);
    out.flush();
    err.flush();
    System.exit(code);
  }

  /** Runs the CLI and returns the process exit code (never calls System.exit). */
  public static int run(String[] args) {
    boolean color = chalkEnabled();
    FshLogger.logger.setColorEnabled(color);
    FshLogger.logger.setSink(FshLogger.consoleSink(out, color));
    try {
      app(args);
      return 0;
    } catch (Exit e) {
      return e.code;
    } catch (RuntimeException | StackOverflowError e) {
      return logUnexpectedError(e);
    }
  }

  private static int logUnexpectedError(Throwable e) {
    FshLogger.logger.error("SUSHI encountered the following unexpected error: " + message(e));
    FshLogger.logger.debug(stack(e));
    return 1;
  }

  private static String message(Throwable e) {
    if (e instanceof Processing.ThrownErrorConstructor) return "undefined";
    return e.getMessage() == null ? "undefined" : e.getMessage();
  }

  private static String stack(Throwable e) {
    java.io.StringWriter sw = new java.io.StringWriter();
    e.printStackTrace(new java.io.PrintWriter(sw));
    return sw.toString().stripTrailing();
  }

  static String getVersion() {
    String sushiVersion = Processing.getLocalSushiVersion();
    if (sushiVersion != null) {
      return "SUSHI v" + sushiVersion + " (implements FHIR Shorthand specification v" + FSH_VERSION + ")";
    }
    return "unknown";
  }

  // ============================================================================================== commander

  static final String PROGRAM_HELP = """
      Usage: sushi [options] [command]

      Options:
        -v, --version                                        print SUSHI version
        -h, --help                                           display help for command

      Commands:
        build [options] [path-to-fsh-project]                build a SUSHI project
        init [options] [name]                                initialize a SUSHI project
        update-dependencies [options] [path-to-fsh-project]  update FHIR packages in project configuration
        help [command]                                       display help for command
      """;

  static final String BUILD_HELP = """
      Usage: sushi build [options] [path-to-fsh-project]

      build a SUSHI project

      Arguments:
        path-to-fsh-project      path to your FSH project (default: ".")

      Options:
        -l, --log-level <level>  specify the level of log messages (default: "info")
                                 (choices: "error", "warn", "info", "debug")
        -o, --out <out>          the path to the output folder (default:
                                 "fsh-generated")
        -p, --preprocessed       output FSH produced by preprocessing steps
        -r, --require-latest     exit with error if this is not the latest version of
                                 SUSHI (default: false)
        -s, --snapshot           generate snapshot in Structure Definition output
                                 (default: false)
        -c, --config <config>    override elements in sushi-config.yaml (supported:
                                 'version', 'status', 'releaselabel') (eg: --config
                                 status:draft)
        -h, --help               display help for command
      """;

  static final String INIT_HELP = """
      Usage: sushi init [options] [name]

      initialize a SUSHI project

      Arguments:
        name                     project name

      Options:
        -c, --config <config>    configure a specific property for the project
                                 (supported: 'id', 'canonical', 'status', 'version',
                                 'releaselabel', 'publisher-name', 'publisher-url')
                                 (eg: --config status:draft)
        -d, --default            accept all remaining defaults
        -a, --auto-initialize    automatically initialize the SUSHI project in the
                                 current directory
        -l, --log-level <level>  specify the level of log messages (default: "info")
                                 (choices: "error", "warn", "info", "debug")
        -h, --help               display help for command
      """;

  static final String UPDATE_HELP = """
      Usage: sushi update-dependencies [options] [path-to-fsh-project]

      update FHIR packages in project configuration

      Arguments:
        path-to-fsh-project      path to your FSH project (default: ".")

      Options:
        -l, --log-level <level>  specify the level of log messages (default: "info")
                                 (choices: "error", "warn", "info", "debug")
        -h, --help               display help for command
      """;

  /** One option of a command: short flag, long flag, value placeholder (null for booleans). */
  record Opt(String shortFlag, String longFlag, String arg, String key) {
    String flags() {
      return shortFlag + ", " + longFlag + (arg != null ? " " + arg : "");
    }
  }

  static final List<Opt> BUILD_OPTS = List.of(
      new Opt("-l", "--log-level", "<level>", "logLevel"), new Opt("-o", "--out", "<out>", "out"),
      new Opt("-p", "--preprocessed", null, "preprocessed"), new Opt("-r", "--require-latest", null, "requireLatest"),
      new Opt("-s", "--snapshot", null, "snapshot"), new Opt("-c", "--config", "<config>", "config"),
      new Opt("-i", "--init", null, "init"));
  static final List<Opt> INIT_OPTS = List.of(
      new Opt("-c", "--config", "<config>", "config"), new Opt("-d", "--default", null, "default"),
      new Opt("-a", "--auto-initialize", null, "autoInitialize"), new Opt("-l", "--log-level", "<level>", "logLevel"));
  static final List<Opt> UPDATE_OPTS = List.of(new Opt("-l", "--log-level", "<level>", "logLevel"));
  static final List<String> LEVELS = List.of("error", "warn", "info", "debug");

  /** Parsed options of a command (commander's opts()). */
  static final class Options {
    String logLevel;
    String out;
    boolean preprocessed;
    boolean requireLatest;
    boolean snapshot;
    boolean isDefault;
    boolean autoInitialize;
    Map<String, String> config;
    final List<String> args = new ArrayList<>();
  }

  private static void commanderError(String message, String help) {
    err.print("error: " + message + "\n\n" + help);
    err.flush();
    throw new Exit(1);
  }

  private static void app(String[] argv) {
    List<String> args = List.of(argv);
    // program-level options (-v/-h) are recognised anywhere before "--"
    int dd = args.indexOf("--");
    List<String> scan = dd >= 0 ? args.subList(0, dd) : args;
    String command = !args.isEmpty() && List.of("build", "init", "update-dependencies", "help").contains(args.get(0))
        ? args.get(0) : null;
    if (command == null && (scan.contains("-v") || scan.contains("--version"))) {
      out.println(getVersion());
      throw new Exit(0);
    }
    if ("help".equals(command)) {
      String which = args.size() > 1 ? args.get(1) : null;
      out.print(switch (which == null ? "" : which) {
        case "build" -> BUILD_HELP;
        case "init" -> INIT_HELP;
        case "update-dependencies" -> UPDATE_HELP;
        default -> PROGRAM_HELP;
      });
      throw new Exit(0);
    }
    if (command == null && (scan.contains("-h") || scan.contains("--help"))
        && args.indexOf(scan.contains("-h") ? "-h" : "--help") == 0) {
      out.print(PROGRAM_HELP);
      throw new Exit(0);
    }
    List<String> rest = command == null ? args : args.subList(1, args.size());
    switch (command == null ? "build" : command) {
      case "init" -> {
        Options o = parse(rest, INIT_OPTS, INIT_HELP, true);
        if (o.args.size() > 1) {
          commanderError("too many arguments for 'init'. Expected 1 argument but got " + o.args.size() + ".",
              INIT_HELP);
        }
        setLogLevel(o);
        Processing.init(o.args.isEmpty() ? null : o.args.get(0), o.config, o.isDefault, o.autoInitialize);
        throw new Exit(0);
      }
      case "update-dependencies" -> {
        Options o = parse(rest, UPDATE_OPTS, UPDATE_HELP, false);
        setLogLevel(o);
        runUpdateDependencies(o.args.isEmpty() ? null : o.args.get(0));
        throw new Exit(0);
      }
      default -> {
        Options o = parse(rest, BUILD_OPTS, BUILD_HELP, false);
        if (o.args.size() > 1) {
          commanderError("too many arguments for 'build'. Expected 1 argument but got " + o.args.size() + ".",
              BUILD_HELP);
        }
        setLogLevel(o);
        try {
          runBuild(o.args.isEmpty() ? null : o.args.get(0), o, argv);
        } catch (Exit e) {
          throw e;
        } catch (RuntimeException | StackOverflowError e) {
          throw new Exit(logUnexpectedError(e));
        }
      }
    }
  }

  /** commander option parsing for one command: long/short flags, --x=v, -xv, combined short booleans, "--". */
  private static Options parse(List<String> args, List<Opt> opts, String help, boolean lowerConfigKeys) {
    Options o = new Options();
    for (int i = 0; i < args.size(); i++) {
      String a = args.get(i);
      if (a.equals("--")) {
        o.args.addAll(args.subList(i + 1, args.size()));
        break;
      }
      if (a.equals("-h") || a.equals("--help")) {
        out.print(help);
        throw new Exit(0);
      }
      if (a.startsWith("--") ) {
        String name = a;
        String value = null;
        int eq = a.indexOf('=');
        if (eq > 0) {
          name = a.substring(0, eq);
          value = a.substring(eq + 1);
        }
        Opt opt = find(opts, name);
        if (opt == null) commanderError("unknown option '" + a + "'", help);
        if (opt.arg() != null && value == null) {
          if (i + 1 >= args.size()) commanderError("option '" + opt.flags() + "' argument missing", help);
          value = args.get(++i);
        } else if (opt.arg() == null && value != null) {
          commanderError("option '" + opt.flags() + "' doesn't take an argument", help);
        }
        apply(o, opt, value, help, lowerConfigKeys);
      } else if (a.startsWith("-") && a.length() > 1) {
        // short flag(s): -p, -ps, -ofoo, -o foo
        for (int k = 1; k < a.length(); k++) {
          String flag = "-" + a.charAt(k);
          Opt opt = find(opts, flag);
          if (opt == null) {
            commanderError("unknown option '" + (k == 1 ? a : flag) + "'", help);
          }
          if (opt.arg() != null) {
            String value;
            if (k + 1 < a.length()) {
              value = a.substring(k + 1);
            } else {
              if (i + 1 >= args.size()) commanderError("option '" + opt.flags() + "' argument missing", help);
              value = args.get(++i);
            }
            apply(o, opt, value, help, lowerConfigKeys);
            break;
          }
          apply(o, opt, null, help, lowerConfigKeys);
        }
      } else {
        o.args.add(a);
      }
    }
    return o;
  }

  private static Opt find(List<Opt> opts, String flag) {
    for (Opt opt : opts) {
      if (opt.shortFlag().equals(flag) || opt.longFlag().equals(flag)) return opt;
    }
    return null;
  }

  private static void apply(Options o, Opt opt, String value, String help, boolean lowerConfigKeys) {
    switch (opt.key()) {
      case "logLevel" -> {
        if (!LEVELS.contains(value)) {
          commanderError("option '" + opt.flags() + "' argument '" + value + "' is invalid. Allowed choices are "
              + String.join(", ", LEVELS) + ".", help);
        }
        o.logLevel = value;
      }
      case "out" -> o.out = value;
      case "preprocessed" -> o.preprocessed = true;
      case "requireLatest" -> o.requireLatest = true;
      case "snapshot" -> o.snapshot = true;
      case "default" -> o.isDefault = true;
      case "autoInitialize" -> o.autoInitialize = true;
      case "config" -> {
        // (value, previous = {}) => { const [k, ...v] = value.split(':'); previous[k] = v.join(':') }
        int c = value.indexOf(':');
        String k = c < 0 ? value : value.substring(0, c);
        String v = c < 0 ? "" : value.substring(c + 1);
        if (o.config == null) o.config = new LinkedHashMap<>();
        o.config.put(lowerConfigKeys ? k.toLowerCase(java.util.Locale.ROOT) : k, v);
      }
      case "init" -> {
        // init was moved to a separate command, so log a message to indicate how to use it
        out.println("The --init option has been moved to a separate command. Instead, run the following command: "
            + "sushi init");
        throw new Exit(1);
      }
      default -> throw new IllegalStateException(opt.key());
    }
  }

  private static void setLogLevel(Options options) {
    // Set the log level. If no level is specified, logger defaults to info
    if (options.logLevel != null) {
      // options.logLevel has only valid log levels because the CLI sets the choices
      FshLogger.logger.setLevel(options.logLevel);
    }
  }

  // ============================================================================================== commands

  private static void runUpdateDependencies(String projectPath) {
    String input = Processing.ensureInputDir(projectPath);
    Configuration config = Processing.readConfig(input);
    Processing.updateExternalDependencies(config, Processing.defaultRegistryClient());
  }

  private static void runBuild(String input, Options program, String[] argv) {
    // NOTE: This is included to provide nicer handling for the previous CLI structure for building FSH projects.
    // Check the first argument passed into sushi. If it is not "build", then this is a legacy build,
    // in which case we should make sure that the first argument is a flag or a valid path.
    String arg = argv.length > 0 ? argv[0] : null;
    if (arg != null && !arg.equals("build") && !arg.startsWith("-") && !Processing.exists(arg)) {
      // It's not a flag or a path, so it's probably a typo of an existing command
      out.println(PROGRAM_HELP);
      throw new Exit(1);
    }

    FshLogger logger = FshLogger.logger;
    logger.info("Running " + getVersion());
    logger.info("Arguments:");
    if (program.logLevel != null) {
      logger.info("  --log-level " + program.logLevel);
    }
    if (program.preprocessed) {
      logger.info("  --preprocessed");
    }
    if (program.snapshot) {
      logger.info("  --snapshot");
    }
    if (program.requireLatest) {
      logger.info("  --require-latest");
    }
    if (program.out != null && !program.out.isEmpty()) {
      logger.info("  --out " + NodePath.resolve(program.out));
    }
    if (program.config != null) {
      program.config.forEach((k, v) -> logger.info("  --config " + k + ":" + v));
    }
    logger.info("  " + NodePath.resolve(input == null || input.isEmpty() ? "." : input));

    // The version check hits the network, so only wait on it where the result is needed
    boolean checkVersion = Processing.versionCheckEnabled();
    Processing.SushiVersions sushiVersions = null;
    if (program.requireLatest) {
      // The port's releases are fhir-ig-tools releases, not npm's fsh-sushi (core-divergences.md T3)
      Processing.PortReleaseCheck release = Processing.checkLatestPortRelease();
      if (!release.ok()) {
        logger.error(release.message());
        throw new Exit(1);
      }
      if (!checkVersion) {
        // the latest port release: no version block in the summary, as TS when it runs the latest release
        sushiVersions = Processing.checkSushiVersion(false);
      }
    }

    input = Processing.ensureInputDir(input);

    String ignoredWarnings = getIgnoredMessages(input, "sushi-ignoreWarnings.txt");
    if (ignoredWarnings != null) {
      logger.setIgnoredWarnings(ignoredWarnings);
    }
    String ignoredErrors = getIgnoredMessages(input, "sushi-ignoreErrors.txt");
    if (ignoredErrors != null) {
      logger.setIgnoredErrors(ignoredErrors);
    }

    String originalInput = input;
    input = Processing.findInputDir(input);

    // If an input/fsh subdirectory is used, we are in an IG Publisher context
    boolean fshFolder = NodePath.basename(input).equals("fsh");
    boolean inputFshFolder = fshFolder && NodePath.basename(NodePath.dirname(input)).equals("input");
    if (!inputFshFolder) {
      // Since current supported tank configuration requires input/fsh folder,
      // both legacy IG publisher mode and legacy flat tank cases occur when
      // there is no input/fsh/ folder.
      // If we detect this case, things are about to go very wrong, so exit immediately.
      logger.error("Migration to current SUSHI project structure is required. See above error message for details. "
          + "Exiting.");
      throw new Exit(1);
    }
    String outDir = Processing.ensureOutputDir(input, program.out);

    FSHTank tank;
    Configuration config;
    try {
      List<RawFSH> rawFSH;
      if (!Processing.exists(input)) {
        // If we have a path that ends with input/fsh but that folder does not exist,
        // we are in a sushi-config.yaml-only case (current tank configuration with no FSH files)
        // so we can safely say there are no FSH files and therefore rawFSH is empty.
        rawFSH = List.of();
      } else {
        rawFSH = Processing.getRawFSHes(input);
      }
      if (rawFSH.isEmpty() && !(Processing.exists(NodePath.join(originalInput, "sushi-config.yaml"))
          || Processing.exists(NodePath.join(originalInput, "sushi-config.yml")))) {
        logger.info("No FSH files or sushi-config.yaml present.");
        throw new Exit(0);
      }
      config = Processing.readConfig(originalInput);
      Processing.updateConfig(config, program.config);
      tank = Processing.fillTank(rawFSH, config);
      tank.checkDuplicateNameEntities();
    } catch (Exit e) {
      throw e;
    } catch (RuntimeException | StackOverflowError e) {
      // If no errors have been logged yet, log this exception so the user knows why we're exiting
      if (logger.stats.numError == 0) {
        String m = e instanceof Processing.ThrownErrorConstructor ? e.toString()
            : e.getMessage() != null ? e.getMessage() : e.toString();
        logger.error("An unexpected error occurred: " + m);
        logger.debug(stack(e));
      }
      throw new Exit(1);
    }

    // Load dependencies
    FHIRDefinitions defs = FHIRDefinitions.createFHIRDefinitions();
    Processing.loadExternalDependencies(defs, config);

    // Load custom resources from typical input/* paths and custom configured paths
    PredefinedResources.loadPredefinedResources(defs, NodePath.resolve(input, ".."), NodePath.resolve(originalInput),
        config.parameters);

    // Optimize the database after loading to ensure the most efficient queries
    defs.optimize();

    // Check for StructureDefinition
    Object structDef = defs.fishForFHIR("StructureDefinition", FishableType.Resource);
    Object sdVersion = structDef instanceof Map<?, ?> m ? m.get("version") : null;
    if (structDef == null || !Processing.isSupportedFHIRVersion(sdVersion instanceof String s ? s : null)) {
      logger.error("Valid StructureDefinition resource not found. The FHIR package in your local cache"
          + " may be corrupt. Local FHIR cache can be found at <home-directory>/.fhir/packages."
          + " For more information, see https://wiki.hl7.org/FHIR_Package_Cache#Location.");
      throw new Exit(1);
    }

    logger.info("Converting FSH to FHIR resources...");
    Package outPackage = ExportFHIR.exportFHIR(tank, defs);
    Processing.WriteResult written = Processing.writeFHIRResources(outDir, outPackage, defs, program.snapshot);
    Processing.writeFSHIndex(outDir, outPackage, input, written.skippedResources());

    if (program.preprocessed) {
      logger.info("Writing preprocessed FSH...");
      Processing.writePreprocessedFSH(outDir, input, tank);
    }

    // If FSHOnly is true in the config, do not generate IG content, otherwise, generate IG content
    if (Boolean.TRUE.equals(config.FSHOnly)) {
      logger.info("Exporting FSH definitions only. No IG related content will be exported.");
    } else {
      String igFilesPath = NodePath.resolve(input, "..", "..");
      logger.info("Assembling Implementation Guide sources...");
      IGExporter igExporter = new IGExporter(outPackage, defs, igFilesPath);
      igExporter.export(outDir);
      logger.info("Assembled Implementation Guide sources; ready for IG Publisher.");
      if (Processing.readdir(outDir).stream().noneMatch(file -> file.startsWith("_build"))) {
        logger.info("The _build script hosted at https://github.com/HL7/ig-publisher-scripts is useful for "
            + "downloading and running the IG Publisher.");
      }
    }

    out.println();
    if (sushiVersions == null) sushiVersions = Processing.checkSushiVersion(checkVersion);
    printResults(outPackage, sushiVersions);

    out.println();

    throw new Exit(logger.stats.numError);
  }

  static String getIgnoredMessages(String input, String fileName) {
    String contents = null;
    String rootIgnoreFilePath = NodePath.join(input, fileName);
    String nestedIgnoreFilePath = NodePath.join(input, "input", fileName);
    if (Processing.exists(rootIgnoreFilePath)) {
      if (Processing.exists(nestedIgnoreFilePath)) {
        FshLogger.logger.warn("Found " + fileName + " files in the following locations:\n\n"
            + " - " + rootIgnoreFilePath + "\n"
            + " - " + nestedIgnoreFilePath + "\n\n"
            + "Only the file at " + rootIgnoreFilePath + " will be processed. "
            + "Remove one of these files to avoid this warning.");
      }
      contents = Processing.readFile(rootIgnoreFilePath);
    } else if (Processing.exists(nestedIgnoreFilePath)) {
      contents = Processing.readFile(nestedIgnoreFilePath);
    }
    return contents;
  }

  // ============================================================================================== summary box

  /** chalk's colour support (supports-color 7): FORCE_COLOR, else a TTY whose TERM is not "dumb". */
  static boolean chalkEnabled() {
    String force = System.getenv("FORCE_COLOR");
    if (force != null) {
      return !(force.equals("0") || force.equals("false"));
    }
    if (!stdoutIsTty()) return false;
    String term = System.getenv("TERM");
    return !"dumb".equals(term);
  }

  static boolean stdoutIsTty() {
    java.io.Console c = System.console();
    if (c == null) return false;
    try {
      // JDK 22+: System.console() is also non-null when redirected; isTerminal() tells
      java.lang.reflect.Method m = java.io.Console.class.getMethod("isTerminal");
      return (Boolean) m.invoke(c);
    } catch (ReflectiveOperationException e) {
      return true;
    }
  }

  private static java.util.function.UnaryOperator<String> clr(int numError, int numWarn, boolean color) {
    if (!color) return s -> s;
    String open = numError > 0 ? "\u001b[31m" : numWarn > 0 ? "\u001b[38;2;179;98;0m" : "\u001b[32m";
    return s -> open + s + "\u001b[39m";
  }

  static void printResults(Package pkg, Processing.SushiVersions sushiVersions) {
    FshLogger.Stats stats = FshLogger.logger.stats;
    // NOTE: These variables are creatively names to align well in the strings below while keeping prettier happy
    String profileNum = Js.pad(String.valueOf(pkg.profiles.size()), 13);
    String extentNum = Js.pad(String.valueOf(pkg.extensions.size()), 12);
    String logiclNum = Js.pad(String.valueOf(pkg.logicals.size()), 12);
    String resourcNum = Js.pad(String.valueOf(pkg.resources.size()), 13);
    String valueSetsNumber = Js.pad(String.valueOf(pkg.valueSets.size()), 18);
    String codeSystemsNum = Js.pad(String.valueOf(pkg.codeSystems.size()), 17);
    String instancesNumber = Js.pad(String.valueOf(pkg.instances.size()), 18);
    String errorNumMsg = Js.pad(stats.numError + " Error" + (stats.numError != 1 ? "s" : "") + " ", 13);
    String wrNumMsg = Js.padStart(stats.numWarn + " Warning" + (stats.numWarn != 1 ? "s" : ""), 12);

    String aWittyMessageInvolvingABadFishPun = Js.padEnd(Puns.getRandomPun(stats.numError, stats.numWarn), 36);
    java.util.function.UnaryOperator<String> clr = clr(stats.numError, stats.numWarn,
        FshLogger.logger.colorEnabled());

    String ignoredMessages = null;
    if (stats.numIgnoredError != 0 || stats.numIgnoredWarn != 0) {
      String ignoredErrors = Js.pad(stats.numIgnoredError + " ignored", 13);
      String ignoredWarnings = Js.padStart(stats.numIgnoredWarn + " ignored ", 12);
      ignoredMessages = clr.apply("║") + Js.pad("", 38) + ignoredErrors + " " + ignoredWarnings + " "
          + clr.apply("║");
    }

    // NOTE: Doing some funky things w/ strings on some lines to keep overall alignment in the code
    List<String> results = new ArrayList<>();
    results.add(clr.apply("╔" + "════════════════════════ SUSHI RESULTS ══════════════════════════" + "╗"));
    results.add(clr.apply("║") + " ╭───────────────┬──────────────┬──────────────┬───────────────╮ " + clr.apply("║"));
    results.add(clr.apply("║") + " │    Profiles   │  Extensions  │   Logicals   │   Resources   │ " + clr.apply("║"));
    results.add(clr.apply("║") + " ├───────────────┼──────────────┼──────────────┼───────────────┤ " + clr.apply("║"));
    results.add(clr.apply("║") + " │ " + profileNum + " │ " + extentNum + " │ " + logiclNum + " │ " + resourcNum
        + " │ " + clr.apply("║"));
    results.add(clr.apply("║") + " ╰───────────────┴──────────────┴──────────────┴───────────────╯ " + clr.apply("║"));
    results.add(clr.apply("║") + " ╭────────────────────┬───────────────────┬────────────────────╮ " + clr.apply("║"));
    results.add(clr.apply("║") + " │      ValueSets     │    CodeSystems    │     Instances      │ " + clr.apply("║"));
    results.add(clr.apply("║") + " ├────────────────────┼───────────────────┼────────────────────┤ " + clr.apply("║"));
    results.add(clr.apply("║") + " │ " + valueSetsNumber + " │ " + codeSystemsNum + " │ " + instancesNumber + " │ "
        + clr.apply("║"));
    results.add(clr.apply("║") + " ╰────────────────────┴───────────────────┴────────────────────╯ " + clr.apply("║"));
    results.add(clr.apply("║" + "                                                                 " + "║"));
    results.add(clr.apply("╠" + "═════════════════════════════════════════════════════════════════" + "╣"));
    results.add(clr.apply("║") + " " + aWittyMessageInvolvingABadFishPun + " " + errorNumMsg + " " + wrNumMsg + " "
        + clr.apply("║"));
    if (ignoredMessages != null) results.add(ignoredMessages);
    results.add(clr.apply("╚" + "═════════════════════════════════════════════════════════════════" + "╝"));

    String latest = sushiVersions.latest();
    String current = sushiVersions.current();
    if (latest != null && current != null && !latest.equals(current)) {
      String endline = results.remove(results.size() - 1);
      results.add(clr.apply("╠" + "═════════════════════════════════════════════════════════════════" + "╣"));
      results.add(clr.apply("║") + Js.pad("You are using SUSHI version " + current + ", but the latest stable", 65)
          + clr.apply("║"));
      results.add(clr.apply("║") + Js.pad("release is version " + latest + ". To install the latest release, run:", 65)
          + clr.apply("║"));
      results.add(clr.apply("║") + Js.pad("npm install -g fsh-sushi", 65) + clr.apply("║"));
      results.add(endline);
    } else if (latest == null || current == null) {
      String endline = results.remove(results.size() - 1);
      results.add(clr.apply("╠" + "═════════════════════════════════════════════════════════════════" + "╣"));
      results.add(clr.apply("║") + Js.pad("SUSHI cannot determine if it is running the latest version.", 65)
          + clr.apply("║"));
      results.add(clr.apply("║") + Js.pad("To see a listing of releases, including the latest, visit:", 65)
          + clr.apply("║"));
      results.add(clr.apply("║") + Js.pad("https://github.com/FHIR/sushi/releases", 65) + clr.apply("║"));
      results.add(endline);
    }

    boolean convertChars = !supportsFancyCharacters();
    for (String r : results) {
      if (convertChars) {
        r = r.replaceAll("[╔╝╚╗╠╣═]", "=").replaceAll("[╭╯╰╮]", " ").replaceAll("[─┬┼┴]", "-")
            .replaceAll("[║│├┤]", "|");
      }
      out.println(r);
    }
  }

  private static boolean supportsFancyCharacters() {
    // There is no sure-fire way, but we know that most problems are when running in the IG Publisher,
    // so try to detect that situation (which is still actually pretty tricky and not guaranteed).

    // 1. If the user wants the fancy characters, allow them
    String force = System.getenv("FORCE_FANCY_CHARACTERS");
    if (force != null && java.util.regex.Pattern.compile("(true|1)", java.util.regex.Pattern.CASE_INSENSITIVE)
        .matcher(force).find()) {
      return true;
    }
    // 2. Many JVM will insert an environment variable indicating the main Java class being run.
    //      E.g., JAVA_MAIN_CLASS_25538=org.hl7.fhir.igtools.publisher.Publisher
    //    We won't check the actual class; we'll just assume that if it's run in Java, best not take chances.
    if (System.getenv().keySet().stream().anyMatch(k -> k.startsWith("JAVA_MAIN_CLASS"))) {
      return false;
    }
    // 3. It appears that in a Java-launched process, certain aspects of stdout aren't available, so
    //    use that to test if it's likely the fancy chars will be supported (node: stdout.hasColors exists only on
    //    a TTY).
    return stdoutIsTty();
  }
}

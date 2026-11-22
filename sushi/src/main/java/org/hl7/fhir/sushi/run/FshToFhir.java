package org.hl7.fhir.sushi.run;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.hl7.fhir.sushi.export.ExportFHIR;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirtypes.FhirResource;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.importer.RawFSH;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.Processing;

// port of sushi/src/run/FshToFhir.ts (SUSHI 3.21.1)
/**
 * Programmatic API: FSH text in, FHIR JSON (value model) plus the errors and warnings logged while processing out.
 * Not safe for concurrent calls (the logger is process-wide), as in TS.
 */
public final class FshToFhir {
  private FshToFhir() {}

  /**
   * TS fshToFhirOptions. The 'snapshot' option triggers the generation of StructureDefinition.snapshot (EXPERIMENTAL:
   * SUSHI's snapshots are likely not perfect and differ from what the IG Publisher and/or Simplifier would create).
   */
  public static final class Options {
    public String canonical;
    public String version;
    public String fhirVersion;
    /** ImplementationGuideDependsOn[] ({packageId, version, ...}) */
    public List<JsObject> dependencies;
    /** silly | debug | verbose | http | info | warn | error | silent */
    public String logLevel;
    public Boolean snapshot;
  }

  /** {fhir, errors, warnings}; fhir is null when the options are invalid. */
  public record Result(List<Object> fhir, List<FshLogger.Tracked> errors, List<FshLogger.Tracked> warnings) {}

  /** Loads the configured dependencies (Processing.loadExternalDependencies); tests substitute it (TS: jest.spyOn). */
  public static java.util.function.BiConsumer<FHIRDefinitions, Configuration> dependencyLoader =
      Processing::loadExternalDependencies;

  private static volatile boolean silent;

  /** TS logger.transports[0].silent: true while a call with logLevel 'silent' runs. */
  public static boolean loggerSilent() {
    return silent;
  }

  // Winston levels: https://github.com/winstonjs/winston#logging-levels plus a silent option
  private static final List<String> LEVELS =
      List.of("silly", "debug", "verbose", "http", "info", "warn", "error", "silent");

  public static Result fshToFhir(String input) {
    return fshToFhir(List.of(input), false, new Options());
  }

  public static Result fshToFhir(String input, Options options) {
    return fshToFhir(List.of(input), false, options);
  }

  public static Result fshToFhir(List<String> input, Options options) {
    return fshToFhir(input, true, options);
  }

  private static Result fshToFhir(List<String> input, boolean isArray, Options options) {
    Options opts = options != null ? options : new Options();
    FshLogger logger = FshLogger.logger;
    // track errors and warnings, and determine log level from options
    logger.errorsAndWarnings.reset();
    logger.errorsAndWarnings.shouldTrack = true;
    Consumer<FshLogger.LogEntry> originalSink = null;
    String originalLevel = logger.level();
    try {
      if ("silent".equals(opts.logLevel)) {
        originalSink = logger.setSink(e -> {});
        silent = true;
      } else if (opts.logLevel != null) {
        if (!LEVELS.contains(opts.logLevel)) {
          FshLogger.Tracked err = new FshLogger.Tracked("Invalid logLevel: " + opts.logLevel
              + ". Valid levels include: " + String.join(", ", LEVELS) + ".", null, null);
          return new Result(null, List.of(err), List.of());
        }
        logger.setLevel(opts.logLevel);
      }

      boolean snapshot = opts.snapshot != null && opts.snapshot;

      // set up a config so that sushi can run
      Configuration config = new Configuration();
      config.canonical = opts.canonical != null ? opts.canonical : "http://example.org";
      config.FSHOnly = true;
      config.fhirVersion = new ArrayList<>(List.of(opts.fhirVersion != null ? opts.fhirVersion : "4.0.1"));
      config.dependencies = opts.dependencies;
      config.version = opts.version;

      // load dependencies
      FHIRDefinitions defs = FHIRDefinitions.createFHIRDefinitions();
      dependencyLoader.accept(defs, config);

      // load FSH text into memory
      List<RawFSH> rawFSHes = new ArrayList<>();
      if (isArray) {
        for (int i = 0; i < input.size(); i++) rawFSHes.add(new RawFSH(input.get(i), "Input_" + i));
      } else {
        rawFSHes.add(new RawFSH(input.get(0)));
      }
      FSHTank tank = Processing.fillTank(rawFSHes, config);
      tank.checkDuplicateNameEntities();

      // process FSH text into FHIR
      Package outPackage = ExportFHIR.exportFHIR(tank, defs);
      List<Object> fhir = new ArrayList<>();
      List<List<? extends FhirResource>> artifactLists = List.of(outPackage.profiles, outPackage.extensions,
          outPackage.instances, outPackage.valueSets, outPackage.codeSystems, outPackage.logicals,
          outPackage.resources);
      for (List<? extends FhirResource> artifacts : artifactLists) {
        for (FhirResource artifact : artifacts) {
          fhir.add(artifact.toJSON(snapshot));
        }
      }
      return new Result(fhir, logger.errorsAndWarnings.errors, logger.errorsAndWarnings.warnings);
    } finally {
      if (originalSink != null) logger.setSink(originalSink);
      silent = false;
      logger.setLevel(originalLevel);
    }
  }
}

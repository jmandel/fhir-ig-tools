package org.hl7.fhir.sushi.importer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.common.js.JsNumber;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.testhelpers.JsDump;
import org.hl7.fhir.sushi.utils.FshLogger;

/**
 * WP-2 gate, Java half (the TS half is scripts/oracle/tank-dump.js; scripts/tank-dump.sh compares them): imports one
 * FSH folder and writes
 * <ul>
 *   <li>{@code tank.json}: canonical dump of the FSHDocuments (sorted keys; {@code $class} = TS constructor name
 *       for class instances, none for TS plain objects; {@code $map}, {@code $bigint}, {@code $regex}, {@code $num});
 *   <li>{@code log.txt}: the console log of the import ({@code "<level padded to 5> <message>"});
 *   <li>{@code _preprocessed/}: writePreprocessedFSH straight after import (no export).
 * </ul>
 */
public final class TankDump {
  private TankDump() {}

  public static void main(String[] args) throws IOException {
    run(Path.of(args[0]), Path.of(args[1]));
  }

  /** getFilesRecursive: libuv scandir order (strcmp on the UTF-8 names), depth first. */
  static List<Path> filesRecursive(Path dir) throws IOException {
    List<Path> out = new ArrayList<>();
    if (Files.isDirectory(dir)) {
      List<Path> kids = new ArrayList<>();
      try (var s = Files.list(dir)) {
        s.forEach(kids::add);
      }
      kids.sort(Comparator.comparing(p -> p.getFileName().toString().getBytes(StandardCharsets.UTF_8),
          Arrays::compareUnsigned));
      for (Path k : kids) out.addAll(filesRecursive(k));
    } else if (Files.exists(dir)) {
      out.add(dir);
    }
    return out;
  }

  public static void run(Path in, Path out) throws IOException {
    List<String> lines = new ArrayList<>();
    Consumer<FshLogger.LogEntry> old = FshLogger.logger.setSink(e -> lines.add(padEnd(e.level().name(), 5) + " "
        + e.message() + "\n"));
    try {
      List<RawFSH> raws = new ArrayList<>();
      for (Path f : filesRecursive(in.toAbsolutePath())) {
        if (f.toString().endsWith(".fsh")) raws.add(new RawFSH(new String(Files.readAllBytes(f), StandardCharsets.UTF_8), f.toString()));
      }
      List<FSHDocument> docs = ImportText.importText(raws);
      Configuration config = new Configuration();
      config.canonical = "http://example.org";
      config.version = "1.0.0";
      FSHTank tank = new FSHTank(docs, config);
      tank.checkDuplicateNameEntities();
      Files.createDirectories(out);
      Files.writeString(out.resolve("tank.json"), Json.stringify(JsDump.dump(docs), 1) + "\n", StandardCharsets.UTF_8);
      writePreprocessedFSH(out, in.toAbsolutePath(), tank);
    } finally {
      FshLogger.logger.setSink(old);
    }
    Files.writeString(out.resolve("log.txt"), String.join("", lines), StandardCharsets.UTF_8);
  }

  private static String padEnd(String s, int n) {
    return s.length() >= n ? s : s + " ".repeat(n - s.length());
  }

  /** Port of utils/Processing.ts writePreprocessedFSH (SUSHI 3.21.1), for the gate only (WP-10 owns the real one). */
  static void writePreprocessedFSH(Path outDir, Path inDir, FSHTank tank) throws IOException {
    Path preprocessedPath = outDir.resolve("_preprocessed");
    Files.createDirectories(preprocessedPath);
    for (FSHDocument doc : tank.docs) {
      StringBuilder fileContent = new StringBuilder();
      if (!doc.aliases.isEmpty()) {
        doc.aliases.forEach((alias, url) -> fileContent.append("Alias: ").append(alias).append(" = ")
            .append(Common.jsString(url)).append("\n"));
        fileContent.append("\n");
      }
      List<FshEntity> entities = new ArrayList<>();
      entities.addAll(doc.profiles.values());
      entities.addAll(doc.extensions.values());
      entities.addAll(doc.logicals.values());
      entities.addAll(doc.resources.values());
      entities.addAll(doc.instances.values());
      entities.addAll(doc.valueSets.values());
      entities.addAll(doc.codeSystems.values());
      entities.addAll(doc.invariants.values());
      entities.addAll(doc.mappings.values());
      entities.sort(Comparator.comparingInt(e -> e.sourceInfo.location.startLine));
      for (FshEntity entity : entities) {
        fileContent.append("// Originally defined on lines ").append(entity.sourceInfo.location.startLine)
            .append(" - ").append(entity.sourceInfo.location.endLine).append("\n");
        fileContent.append(toFSH(entity)).append("\n\n");
      }
      String content = fileContent.length() == 0 ? "// This file has no content after preprocessing." : fileContent.toString();
      Path target = preprocessedPath.resolve(inDir.relativize(Path.of(doc.file)).toString());
      Files.createDirectories(target.getParent());
      Files.writeString(target, content, StandardCharsets.UTF_8);
    }
    FshLogger.logger.info("Wrote preprocessed FSH to " + preprocessedPath);
  }

  private static String toFSH(FshEntity e) {
    if (e instanceof FshStructure s) return s.toFSH();
    if (e instanceof Instance i) return i.toFSH();
    if (e instanceof FshValueSet v) return v.toFSH();
    if (e instanceof FshCodeSystem c) return c.toFSH();
    if (e instanceof Invariant i) return i.toFSH();
    if (e instanceof Mapping m) return m.toFSH();
    throw new IllegalArgumentException(e.getClass().getName());
  }

}

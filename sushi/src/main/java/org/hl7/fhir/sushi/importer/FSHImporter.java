package org.hl7.fhir.sushi.importer;

import static org.hl7.fhir.sushi.importer.ImporterText.extractMultilineString;
import static org.hl7.fhir.sushi.importer.ImporterText.extractNumberValue;
import static org.hl7.fhir.sushi.importer.ImporterText.unescapeQuotedString;
import static org.hl7.fhir.sushi.importer.JsTypeError.readNull;
import static org.hl7.fhir.sushi.importer.JsTypeError.readUndefined;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.RequiredMetadataError;
import org.hl7.fhir.sushi.errors.ValueSetFilterMissingValueError;
import org.hl7.fhir.sushi.errors.ValueSetFilterOperatorError;
import org.hl7.fhir.sushi.errors.ValueSetFilterValueTypeError;
import org.hl7.fhir.sushi.fshtypes.Common;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.FshCanonical;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.FshQuantity;
import org.hl7.fhir.sushi.fshtypes.FshRatio;
import org.hl7.fhir.sushi.fshtypes.FshReference;
import org.hl7.fhir.sushi.fshtypes.FshStructure;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.InstanceUsage;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Mapping;
import org.hl7.fhir.sushi.fshtypes.ParamRuleSet;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.Resource;
import org.hl7.fhir.sushi.fshtypes.RuleSet;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.fshtypes.ValueSetComponentFrom;
import org.hl7.fhir.sushi.fshtypes.ValueSetFilter;
import org.hl7.fhir.sushi.fshtypes.VsOperator;
import org.hl7.fhir.sushi.fshtypes.rules.AddElementRule;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.BindingRule;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ConceptRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.FlagCarryingRule;
import org.hl7.fhir.sushi.fshtypes.rules.FlagRule;
import org.hl7.fhir.sushi.fshtypes.rules.InsertRule;
import org.hl7.fhir.sushi.fshtypes.rules.MappingRule;
import org.hl7.fhir.sushi.fshtypes.rules.ObeysRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.fshtypes.rules.PathRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.fshtypes.rules.ValueSetComponentRule;
import org.hl7.fhir.sushi.fshtypes.rules.ValueSetConceptComponentRule;
import org.hl7.fhir.sushi.fshtypes.rules.ValueSetFilterComponentRule;
import org.hl7.fhir.sushi.importer.parser.FSHErrorListener;
import org.hl7.fhir.sushi.importer.parser.FSHParser;
import org.hl7.fhir.sushi.importer.parser.FSHParser.*;
import org.hl7.fhir.sushi.importer.parser.ParseTree;
import org.hl7.fhir.sushi.importer.parser.ParserRuleContext;
import org.hl7.fhir.sushi.importer.parser.TerminalNode;
import org.hl7.fhir.sushi.importer.parser.Token;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.utils.FshLogger;

// port of sushi/src/import/FSHImporter.ts (SUSHI 3.21.1)
/**
 * Parses RawFSH into FSHDocuments: a visitor over the CST of the hand-written parser (WP-1, package
 * importer.parser; no ANTLR at runtime). Syntax errors are logged through FSHErrorListener with SUSHI's exact
 * messages; entities carry SourceInfo (file + TextLocation from tokens, columns in UTF-16 code units as in JS).
 *
 * <p>Parity notes: every {@code extractStartStop} call of the TS is kept, in the same order, because it may log the
 * "cannot be indented" error. Where TS would throw a TypeError on a CST left incomplete by error recovery, this
 * class throws {@link JsTypeError} with V8's message, so "Error in parsing: ..." reads the same.
 */
public class FSHImporter {
  private enum SdMetadataKey { Id, Parent, Title, Description, Unknown }
  private enum InstanceMetadataKey { InstanceOf, Title, Description, Usage, Unknown }
  private enum VsMetadataKey { Id, Title, Description, Unknown }
  private enum InvariantMetadataKey { Description, Expression, XPath, Severity, Unknown }
  private enum MappingMetadataKey { Id, Source, Target, Description, Title, Unknown }
  private enum Flag { MustSupport, Summary, Modifier, TrialUse, Normative, Draft, Unknown }

  private record Pair<K>(K key, Object value, ParserRuleContext context) {}

  private static final List<String> FLAGS = List.of("MS", "SU", "?!", "TU", "N", "D");
  private static final int INDENT_WIDTH = 2;
  private static final int DEFAULT_START_COLUMN = 1;
  /** JS /^\$?[a-zA-z0-9_\-\.]+$/ (sic: A-z also admits [\]^_`). */
  private static final Pattern ALIAS_REGEX = Pattern.compile("^\\$?[a-zA-z0-9_\\-\\.]+\\z");
  private static final Pattern SOFT_INDEX_PLUS = Pattern.compile("\\[\\+\\]");
  private static final Pattern OR_SPLIT = Pattern.compile(Common.JS_WS + "+or" + Common.JS_WS + "+");
  /** JS /\s*\|\s*(.+)/ ('.' excludes JS line terminators). */
  private static final Pattern CANONICAL_VERSION =
      Pattern.compile(Common.JS_WS + "*\\|" + Common.JS_WS + "*([^\\n\\r\\u2028\\u2029]+)");
  private static final Pattern BRACKETED_UNESCAPE =
      Pattern.compile("(\\]\\]" + Common.JS_WS + "*)\\\\([,\\)])|(\\\\\\\\)");
  private static final Pattern PLAIN_UNESCAPE = Pattern.compile("\\\\([,\\)])");

  private final FshLogger logger = FshLogger.logger;

  private final List<FSHDocument> docs = new ArrayList<>();
  private String currentFile;
  private FSHDocument currentDoc;
  private LinkedHashMap<String, String> allAliases;
  /** Parameterized RuleSets seen so far, by name (public in TS; tests read it). */
  public final LinkedHashMap<String, ParamRuleSet> paramRuleSets = new LinkedHashMap<>();
  private boolean topLevelParse = true;
  private List<List<String>> pathContext = new ArrayList<>();

  public FSHImporter() {}

  /**
   * TS import(rawFSHes): one FSHDocument per RawFSH, in input order (inputs are sorted by the caller). Logs
   * "Preprocessed N documents with M aliases." and "Imported N definitions and M instances." at info level.
   */
  public List<FSHDocument> importFsh(List<RawFSH> rawFSHes) {
    allAliases = new LinkedHashMap<>();
    List<DocContext> contexts = new ArrayList<>();

    // Preprocess the FSH files
    for (RawFSH rawFSH : rawFSHes) {
      // Create and store doc for main import process
      FSHDocument doc = new FSHDocument(rawFSH.path);
      docs.add(doc);
      currentDoc = doc;
      currentFile = currentDoc.file != null ? currentDoc.file : "";

      // A comment at the end of the file is only tokenized if a newline follows it, so add one if missing.
      String content = rawFSH.content.endsWith("\n") ? rawFSH.content : rawFSH.content + "\n";
      DocContext ctx = parseDoc(content, rawFSH.path);
      contexts.add(ctx);

      // Collect the aliases and store in global map
      String docFile = doc.file != null ? doc.file : "";
      for (EntityContext e : ctx.entity()) {
        if (e.alias() != null) {
          AliasContext alias = e.alias();
          String name = getText(alias.name());
          String value = alias.SEQUENCE() != null ? alias.SEQUENCE().getText() : null;
          // When the url contains a fragment (http://example.org#fragment), the grammar will read it as a
          // CODE, so we also accept that for the value here
          if (!Common.truthy(value) && alias.CODE() != null) {
            value = alias.CODE().getText();
          }
          if (name.contains("|")) {
            logger.error("Alias " + name
                + " cannot include \"|\" since the \"|\" character is reserved for indicating a version",
                new SourceInfo(docFile, extractStartStop(alias)));
            continue;
          } else if (!ALIAS_REGEX.matcher(name).find()) {
            logger.warn("Alias " + name + " includes unsupported characters. Alias names can only contain letters, "
                + "numbers, underscores (\"_\"), hyphens (\"-\"), and dots (\".\").",
                new SourceInfo(docFile, extractStartStop(alias)));
          }
          if (allAliases.containsKey(name) && !Objects.equals(allAliases.get(name), value)) {
            logger.error("Alias " + name + " cannot be redefined to " + Common.jsString(value)
                + "; it is already defined as " + Common.jsString(allAliases.get(name)) + ".",
                new SourceInfo(docFile, extractStartStop(alias)));
            // don't set it -- just keep the original definition
          } else {
            allAliases.put(name, value);
            doc.aliases.put(name, value);
          }
        }
        if (e.paramRuleSet() != null) {
          visitParamRuleSet(e.paramRuleSet());
        }
      }
      currentDoc = null;
      currentFile = null;
    }
    logger.info("Preprocessed " + docs.size() + " documents with " + allAliases.size() + " aliases.");

    // Now do the main import
    for (int index = 0; index < contexts.size(); index++) {
      currentDoc = docs.get(index);
      currentFile = currentDoc.file != null ? currentDoc.file : "";
      visitDoc(contexts.get(index));
      currentDoc = null;
      currentFile = null;
    }

    int definitions = 0;
    int instances = 0;
    for (FSHDocument doc : docs) {
      definitions += doc.codeSystems.size() + doc.extensions.size() + doc.profiles.size() + doc.valueSets.size()
          + doc.logicals.size() + doc.resources.size();
      instances += doc.instances.size();
    }
    logger.info("Imported " + definitions + " definitions and " + instances + " instances.");
    return docs;
  }

  private void visitDoc(DocContext ctx) {
    for (EntityContext e : ctx.entity()) {
      try {
        visitEntity(e);
      } catch (RuntimeException err) {
        SourceInfo sourceInfo = new SourceInfo(currentFile, extractStartStop(e));
        logger.error("Error in parsing: " + Common.jsString(err.getMessage()), sourceInfo);
        logger.debug(stackOf(err));
      }
    }
  }

  private static String stackOf(Throwable t) {
    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  private void visitEntity(EntityContext ctx) {
    // Reset the pathContext for each entity
    pathContext = new ArrayList<>();
    if (ctx.profile() != null) {
      visitProfile(ctx.profile());
    } else if (ctx.extension() != null) {
      visitExtension(ctx.extension());
    } else if (ctx.resource() != null) {
      visitResource(ctx.resource());
    } else if (ctx.logical() != null) {
      visitLogical(ctx.logical());
    } else if (ctx.instance() != null) {
      visitInstance(ctx.instance());
    } else if (ctx.valueSet() != null) {
      visitValueSet(ctx.valueSet());
    } else if (ctx.codeSystem() != null) {
      visitCodeSystem(ctx.codeSystem());
    } else if (ctx.invariant() != null) {
      visitInvariant(ctx.invariant());
    } else if (ctx.ruleSet() != null) {
      visitRuleSet(ctx.ruleSet());
    } else if (ctx.mapping() != null) {
      visitMapping(ctx.mapping());
    }
  }

  private SourceInfo src(TextLocation location) {
    return new SourceInfo(currentFile, location);
  }

  private void visitProfile(ProfileContext ctx) {
    Profile profile = new Profile(getText(ctx.name())).withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (docs.stream().anyMatch(doc -> doc.profiles.containsKey(profile.name))) {
      logger.error("Skipping Profile: a Profile named " + profile.name + " already exists.",
          src(extractStartStop(ctx)));
    } else {
      parseProfileOrExtension(profile, ctx.sdMetadata(), ctx.sdRule());
      currentDoc.profiles.put(profile.name, profile);
    }
  }

  private void visitExtension(FSHParser.ExtensionContext ctx) {
    Extension extension =
        new Extension(getText(ctx.name())).withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (docs.stream().anyMatch(doc -> doc.extensions.containsKey(extension.name))) {
      logger.error("Skipping Extension: an Extension named " + extension.name + " already exists.",
          src(extractStartStop(ctx)));
    } else {
      parseProfileOrExtension(extension, ctx.sdMetadata(), ctx.sdRule());
      for (ContextContext extContext : ctx.context()) {
        if (extension.contexts != null && !extension.contexts.isEmpty()) {
          logger.error("Metadata field 'Context' already declared.", src(extractStartStop(extContext)));
        } else {
          extension.contexts = visitContext(extContext);
        }
      }
      currentDoc.extensions.put(extension.name, extension);
    }
  }

  private void applySdMetadata(FshStructure def, List<SdMetadataContext> metaCtx) {
    LinkedHashMap<SdMetadataKey, Object> seenPairs = new LinkedHashMap<>();
    List<Pair<SdMetadataKey>> pairs = new ArrayList<>();
    for (SdMetadataContext sdMeta : metaCtx) pairs.add(visitSdMetadata(sdMeta));
    for (Pair<SdMetadataKey> pair : pairs) {
      if (seenPairs.containsKey(pair.key())) {
        alreadyDeclared(pair, seenPairs.get(pair.key()));
        continue;
      }
      seenPairs.put(pair.key(), pair.value());
      switch (pair.key()) {
        case Id -> def.setId((String) pair.value());
        case Parent -> def.parent = (String) pair.value();
        case Title -> def.title = (String) pair.value();
        case Description -> def.description = (String) pair.value();
        default -> {}
      }
    }
  }

  private void alreadyDeclared(Pair<?> pair, Object seen) {
    logger.error("Metadata field '" + pair.key() + "' already declared with value '" + Common.jsString(seen) + "'.",
        src(extractStartStop(pair.context())));
  }

  private void parseProfileOrExtension(FshStructure def, List<SdMetadataContext> metaCtx,
      List<SdRuleContext> ruleCtx) {
    applySdMetadata(def, metaCtx);
    for (SdRuleContext sdRule : ruleCtx) def.rules.addAll(visitSdRule(sdRule));
  }

  private void visitResource(ResourceContext ctx) {
    Resource resource = new Resource(getText(ctx.name())).withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (docs.stream().anyMatch(doc -> doc.resources.containsKey(resource.name))) {
      logger.error("Skipping Resource: a Resource named " + resource.name + " already exists.",
          src(extractStartStop(ctx)));
    } else {
      parseResourceOrLogical(resource, ctx.sdMetadata(), ctx.lrRule());
      currentDoc.resources.put(resource.name, resource);
    }
  }

  private void visitLogical(LogicalContext ctx) {
    Logical logical = new Logical(getText(ctx.name())).withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (docs.stream().anyMatch(doc -> doc.logicals.containsKey(logical.name))) {
      logger.error("Skipping Logical Model: a Logical Model named " + logical.name + " already exists.",
          src(extractStartStop(ctx)));
    } else {
      parseResourceOrLogical(logical, ctx.sdMetadata(), ctx.lrRule());
      for (CharacteristicsContext characteristics : ctx.characteristics()) {
        if (logical.characteristics != null && !logical.characteristics.isEmpty()) {
          logger.error("Metadata field 'Characteristics' already declared.",
              src(extractStartStop(characteristics)));
        } else {
          logical.characteristics = visitCharacteristics(characteristics);
        }
      }
      currentDoc.logicals.put(logical.name, logical);
    }
  }

  private void parseResourceOrLogical(FshStructure def, List<SdMetadataContext> metaCtx,
      List<LrRuleContext> ruleCtx) {
    applySdMetadata(def, metaCtx);
    for (LrRuleContext lrRule : ruleCtx) def.rules.addAll(visitLrRule(lrRule));
  }

  private void visitInstance(InstanceContext ctx) {
    TextLocation location = extractStartStop(ctx);
    Instance instance = new Instance(getText(ctx.name())).withLocation(location).withFile(currentFile);
    if (docs.stream().anyMatch(doc -> doc.instances.containsKey(instance.name))) {
      logger.error("Skipping Instance: an Instance named " + instance.name + " already exists.", src(location));
    } else {
      try {
        parseInstance(instance, location, ctx.instanceMetadata(), ctx.instanceRule());
        currentDoc.instances.put(instance.name, instance);
      } catch (RuntimeException e) {
        logger.error(Common.jsString(e.getMessage()), instance.sourceInfo);
        logger.debug(stackOf(e));
      }
    }
  }

  private void parseInstance(Instance instance, TextLocation location, List<InstanceMetadataContext> metaCtx,
      List<InstanceRuleContext> ruleCtx) {
    LinkedHashMap<InstanceMetadataKey, Object> seenPairs = new LinkedHashMap<>();
    List<Pair<InstanceMetadataKey>> pairs = new ArrayList<>();
    for (InstanceMetadataContext m : metaCtx) pairs.add(visitInstanceMetadata(m));
    for (Pair<InstanceMetadataKey> pair : pairs) {
      if (seenPairs.containsKey(pair.key())) {
        alreadyDeclared(pair, seenPairs.get(pair.key()));
        continue;
      }
      seenPairs.put(pair.key(), pair.value());
      switch (pair.key()) {
        case InstanceOf -> instance.instanceOf = (String) pair.value();
        case Title -> instance.title = (String) pair.value();
        case Description -> instance.description = (String) pair.value();
        case Usage -> instance.usage = (InstanceUsage) pair.value();
        default -> {}
      }
    }
    if (!Common.truthy(instance.instanceOf)) {
      throw new RequiredMetadataError("InstanceOf", "Instance", instance.name);
    }
    if (FhirCommonBridge.CONFORMANCE_AND_TERMINOLOGY_RESOURCES.contains(instance.instanceOf)
        && metaCtx.stream().noneMatch(c -> c.usage() != null)) {
      logger.warn("No usage was specified on " + instance.name + ". The default #example usage will be applied, but "
          + instance.instanceOf + " Instances are typically definitions. Specify a usage to remove this warning.",
          src(location));
    }
    for (InstanceRuleContext instanceRule : ruleCtx) {
      Rule rule = visitInstanceRule(instanceRule);
      if (rule != null) instance.rules.add(rule);
    }
  }

  private void visitValueSet(ValueSetContext ctx) {
    FshValueSet valueSet =
        new FshValueSet(getText(ctx.name())).withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (docs.stream().anyMatch(doc -> doc.valueSets.containsKey(valueSet.name))) {
      logger.error("Skipping ValueSet: a ValueSet named " + valueSet.name + " already exists.",
          src(extractStartStop(ctx)));
    } else {
      parseValueSet(valueSet, ctx.vsMetadata(), ctx.vsRule());
      currentDoc.valueSets.put(valueSet.name, valueSet);
    }
  }

  private void parseValueSet(FshValueSet valueSet, List<VsMetadataContext> metaCtx, List<VsRuleContext> vsRuleCtx) {
    LinkedHashMap<VsMetadataKey, Object> seenPairs = new LinkedHashMap<>();
    List<Pair<VsMetadataKey>> pairs = new ArrayList<>();
    for (VsMetadataContext m : metaCtx) pairs.add(visitVsMetadata(m.id(), m.title(), m.description(), m));
    for (Pair<VsMetadataKey> pair : pairs) {
      if (seenPairs.containsKey(pair.key())) {
        alreadyDeclared(pair, seenPairs.get(pair.key()));
        continue;
      }
      seenPairs.put(pair.key(), pair.value());
      switch (pair.key()) {
        case Id -> valueSet.setId((String) pair.value());
        case Title -> valueSet.title = (String) pair.value();
        case Description -> valueSet.description = (String) pair.value();
        default -> {}
      }
    }
    for (VsRuleContext vsRule : vsRuleCtx) {
      Rule rule = visitVsRule(vsRule);
      // if rule is a concept component, we may be able to merge its concepts into an existing concept component.
      if (rule instanceof ValueSetConceptComponentRule cr) {
        ValueSetConceptComponentRule matchedComponent = null;
        for (Rule existing : valueSet.rules) {
          if (existing instanceof ValueSetConceptComponentRule ex && cr.inclusion == ex.inclusion
              && Objects.equals(cr.from.system, ex.from.system)
              && sortBy(cr.from.valueSets).equals(sortBy(ex.from.valueSets))) {
            matchedComponent = ex;
            break;
          }
        }
        if (matchedComponent != null) {
          matchedComponent.concepts.addAll(cr.concepts);
        } else {
          valueSet.rules.add(rule);
        }
      } else if (rule != null) {
        valueSet.rules.add(rule);
      }
    }
  }

  /** lodash sortBy(list): a sorted copy; undefined gives []. */
  private static List<String> sortBy(List<String> l) {
    List<String> out = l == null ? new ArrayList<>() : new ArrayList<>(l);
    out.sort((a, b) -> a == null ? (b == null ? 0 : 1) : b == null ? -1 : a.compareTo(b));
    return out;
  }

  private void visitCodeSystem(CodeSystemContext ctx) {
    FshCodeSystem codeSystem =
        new FshCodeSystem(getText(ctx.name())).withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (docs.stream().anyMatch(doc -> doc.codeSystems.containsKey(codeSystem.name))) {
      logger.error("Skipping code system: a code system named " + codeSystem.name + " already exists.",
          src(extractStartStop(ctx)));
    } else {
      parseCodeSystem(codeSystem, ctx.csMetadata(), ctx.csRule());
      currentDoc.codeSystems.put(codeSystem.name, codeSystem);
    }
  }

  private void parseCodeSystem(FshCodeSystem codeSystem, List<CsMetadataContext> metaCtx,
      List<CsRuleContext> csRuleCtx) {
    LinkedHashMap<VsMetadataKey, Object> seenPairs = new LinkedHashMap<>();
    List<Pair<VsMetadataKey>> pairs = new ArrayList<>();
    for (CsMetadataContext m : metaCtx) pairs.add(visitVsMetadata(m.id(), m.title(), m.description(), m));
    for (Pair<VsMetadataKey> pair : pairs) {
      if (seenPairs.containsKey(pair.key())) {
        alreadyDeclared(pair, seenPairs.get(pair.key()));
        continue;
      }
      seenPairs.put(pair.key(), pair.value());
      switch (pair.key()) {
        case Id -> codeSystem.setId((String) pair.value());
        case Title -> codeSystem.title = (String) pair.value();
        case Description -> codeSystem.description = (String) pair.value();
        default -> {}
      }
    }
    for (CsRuleContext ruleCtx : csRuleCtx) {
      // TS pushes the result even when undefined
      codeSystem.rules.add(visitCsRule(ruleCtx));
    }
  }

  private void visitInvariant(InvariantContext ctx) {
    Invariant invariant =
        new Invariant(getText(ctx.name())).withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (docs.stream().anyMatch(doc -> doc.invariants.containsKey(invariant.name))) {
      logger.error("Skipping Invariant: an Invariant named " + invariant.name + " already exists.",
          src(extractStartStop(ctx)));
    } else {
      parseInvariant(invariant, ctx.invariantMetadata(), ctx.invariantRule());
      currentDoc.invariants.put(invariant.name, invariant);
    }
  }

  private void parseInvariant(Invariant invariant, List<InvariantMetadataContext> metaCtx,
      List<InvariantRuleContext> ruleCtx) {
    LinkedHashMap<InvariantMetadataKey, Object> seenPairs = new LinkedHashMap<>();
    List<Pair<InvariantMetadataKey>> pairs = new ArrayList<>();
    for (InvariantMetadataContext m : metaCtx) pairs.add(visitInvariantMetadata(m));
    for (Pair<InvariantMetadataKey> pair : pairs) {
      if (seenPairs.containsKey(pair.key())) {
        alreadyDeclared(pair, seenPairs.get(pair.key()));
        continue;
      }
      seenPairs.put(pair.key(), pair.value());
      switch (pair.key()) {
        case Description -> invariant.description = (String) pair.value();
        case Expression -> invariant.expression = (String) pair.value();
        case Severity -> invariant.severity = (FshCode) pair.value();
        case XPath -> invariant.xpath = (String) pair.value();
        default -> {}
      }
    }
    for (InvariantRuleContext invariantRule : ruleCtx) {
      Rule rule = visitInvariantRule(invariantRule);
      if (rule != null) invariant.rules.add(rule);
    }
  }

  private void visitRuleSet(RuleSetContext ctx) {
    RuleSet ruleSet = new RuleSet(Js.trim(getText(ctx.RULESET_REFERENCE())))
        .withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (docs.stream().anyMatch(doc -> doc.ruleSets.containsKey(ruleSet.name))) {
      logger.error("Skipping RuleSet: a RuleSet named " + ruleSet.name + " already exists.",
          src(extractStartStop(ctx)));
    } else {
      parseRuleSet(ruleSet, ctx.ruleSetRule());
      currentDoc.ruleSets.put(ruleSet.name, ruleSet);
    }
  }

  private void parseRuleSet(RuleSet ruleSet, List<RuleSetRuleContext> rules) {
    for (RuleSetRuleContext rule : rules) {
      if (rule.sdRule() != null) {
        ruleSet.rules.addAll(visitSdRule(rule.sdRule()));
      } else if (rule.vsComponent() != null) {
        ruleSet.rules.add(visitVsComponent(rule.vsComponent()));
      } else if (rule.concept() != null) {
        ruleSet.rules.add(visitConcept(rule.concept()));
      } else if (rule.addElementRule() != null) {
        ruleSet.rules.add(visitAddElementRule(rule.addElementRule()));
      } else if (rule.addCRElementRule() != null) {
        ruleSet.rules.add(visitAddCRElementRule(rule.addCRElementRule()));
      } else if (rule.codeCaretValueRule() != null) {
        ruleSet.rules.add(visitCodeCaretValueRule(rule.codeCaretValueRule(), false));
      } else if (rule.mappingRule() != null) {
        ruleSet.rules.add(visitMappingRule(rule.mappingRule()));
      }
    }
  }

  private void visitParamRuleSet(ParamRuleSetContext ctx) {
    RuleSetReference ref = parseRuleSetReference(ctx.paramRuleSetRef());
    ParamRuleSet paramRuleSet = new ParamRuleSet(ref.name()).withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (paramRuleSets.containsKey(paramRuleSet.name)) {
      logger.error("Skipping RuleSet: a RuleSet named " + paramRuleSet.name + " already exists.",
          src(extractStartStop(ctx)));
    } else {
      paramRuleSet.parameters = ref.params();
      paramRuleSet.contents = visitParamRuleSetContent(ctx.paramRuleSetContent());
      List<String> unusedParameters = paramRuleSet.getUnusedParameters();
      if (!unusedParameters.isEmpty()) {
        logger.warn("RuleSet " + paramRuleSet.name + " contains unused parameter"
            + (unusedParameters.size() > 1 ? "s" : "") + ": " + String.join(", ", unusedParameters),
            paramRuleSet.sourceInfo);
      }
      paramRuleSets.put(paramRuleSet.name, paramRuleSet);
    }
  }

  private String visitParamRuleSetContent(ParamRuleSetContentContext ctx) {
    if (ctx == null) throw readNull("start");
    return ctx.start.getInputStream().getText(ctx.start.getStartIndex(), ctx.stop.getStopIndex());
  }

  private void visitMapping(MappingContext ctx) {
    Mapping mapping = new Mapping(getText(ctx.name())).withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (docs.stream().anyMatch(doc -> doc.mappings.containsKey(mapping.name))) {
      logger.error("Skipping Mapping: a Mapping named " + mapping.name + " already exists.",
          src(extractStartStop(ctx)));
    } else {
      parseMapping(mapping, ctx.mappingMetadata(), ctx.mappingEntityRule());
      currentDoc.mappings.put(mapping.name, mapping);
    }
  }

  private void parseMapping(Mapping mapping, List<MappingMetadataContext> metaCtx,
      List<MappingEntityRuleContext> ruleCtx) {
    LinkedHashMap<MappingMetadataKey, Object> seenPairs = new LinkedHashMap<>();
    List<Pair<MappingMetadataKey>> pairs = new ArrayList<>();
    for (MappingMetadataContext m : metaCtx) pairs.add(visitMappingMetadata(m));
    for (Pair<MappingMetadataKey> pair : pairs) {
      if (seenPairs.containsKey(pair.key())) {
        alreadyDeclared(pair, seenPairs.get(pair.key()));
        continue;
      }
      seenPairs.put(pair.key(), pair.value());
      switch (pair.key()) {
        case Id -> mapping.id = (String) pair.value();
        case Source -> mapping.source = (String) pair.value();
        case Target -> mapping.target = (String) pair.value();
        case Description -> mapping.description = (String) pair.value();
        case Title -> mapping.title = (String) pair.value();
        default -> {}
      }
    }
    for (MappingEntityRuleContext mappingRule : ruleCtx) {
      Rule rule = visitMappingEntityRule(mappingRule);
      if (rule != null) mapping.rules.add(rule);
    }
  }

  private Pair<SdMetadataKey> visitSdMetadata(SdMetadataContext ctx) {
    if (ctx.id() != null) return new Pair<>(SdMetadataKey.Id, visitId(ctx.id()), ctx);
    if (ctx.parent() != null) return new Pair<>(SdMetadataKey.Parent, visitParent(ctx.parent()), ctx);
    if (ctx.title() != null) return new Pair<>(SdMetadataKey.Title, visitTitle(ctx.title()), ctx);
    if (ctx.description() != null) {
      return new Pair<>(SdMetadataKey.Description, visitDescription(ctx.description()), ctx);
    }
    return new Pair<>(SdMetadataKey.Unknown, ctx.getText(), ctx);
  }

  private Pair<InstanceMetadataKey> visitInstanceMetadata(InstanceMetadataContext ctx) {
    if (ctx.instanceOf() != null) {
      return new Pair<>(InstanceMetadataKey.InstanceOf, visitInstanceOf(ctx.instanceOf()), ctx);
    }
    if (ctx.title() != null) return new Pair<>(InstanceMetadataKey.Title, visitTitle(ctx.title()), ctx);
    if (ctx.description() != null) {
      return new Pair<>(InstanceMetadataKey.Description, visitDescription(ctx.description()), ctx);
    }
    if (ctx.usage() != null) return new Pair<>(InstanceMetadataKey.Usage, visitUsage(ctx.usage()), ctx);
    return new Pair<>(InstanceMetadataKey.Unknown, ctx.getText(), ctx);
  }

  /** visitVsMetadata / visitCsMetadata (same shape: id | title | description). */
  private Pair<VsMetadataKey> visitVsMetadata(IdContext id, TitleContext title, DescriptionContext description,
      ParserRuleContext ctx) {
    if (id != null) return new Pair<>(VsMetadataKey.Id, visitId(id), ctx);
    if (title != null) return new Pair<>(VsMetadataKey.Title, visitTitle(title), ctx);
    if (description != null) return new Pair<>(VsMetadataKey.Description, visitDescription(description), ctx);
    return new Pair<>(VsMetadataKey.Unknown, ctx.getText(), ctx);
  }

  private Pair<InvariantMetadataKey> visitInvariantMetadata(InvariantMetadataContext ctx) {
    if (ctx.description() != null) {
      return new Pair<>(InvariantMetadataKey.Description, visitDescription(ctx.description()), ctx);
    }
    if (ctx.expression() != null) {
      return new Pair<>(InvariantMetadataKey.Expression, visitExpression(ctx.expression()), ctx);
    }
    if (ctx.xpath() != null) return new Pair<>(InvariantMetadataKey.XPath, visitXpath(ctx.xpath()), ctx);
    if (ctx.severity() != null) {
      return new Pair<>(InvariantMetadataKey.Severity, visitSeverity(ctx.severity()), ctx);
    }
    return new Pair<>(InvariantMetadataKey.Unknown, ctx.getText(), ctx);
  }

  private Pair<MappingMetadataKey> visitMappingMetadata(MappingMetadataContext ctx) {
    if (ctx.id() != null) return new Pair<>(MappingMetadataKey.Id, visitId(ctx.id()), ctx);
    if (ctx.source() != null) return new Pair<>(MappingMetadataKey.Source, visitSource(ctx.source()), ctx);
    if (ctx.target() != null) return new Pair<>(MappingMetadataKey.Target, visitTarget(ctx.target()), ctx);
    if (ctx.description() != null) {
      return new Pair<>(MappingMetadataKey.Description, visitDescription(ctx.description()), ctx);
    }
    if (ctx.title() != null) return new Pair<>(MappingMetadataKey.Title, visitTitle(ctx.title()), ctx);
    return new Pair<>(MappingMetadataKey.Unknown, ctx.getText(), ctx);
  }

  private String visitId(IdContext ctx) {
    return getText(ctx.name());
  }

  private String visitParent(ParentContext ctx) {
    return aliasAwareValue(ctx.name());
  }

  private String visitTitle(TitleContext ctx) {
    return extractString(ctx.STRING());
  }

  private String visitDescription(DescriptionContext ctx) {
    if (ctx.STRING() != null) return extractString(ctx.STRING());
    if (ctx.MULTILINE_STRING() != null) return extractMultilineString(ctx.MULTILINE_STRING().getText());
    // this can happen due to parsing errors, so just return empty string
    return "";
  }

  private String visitInstanceOf(InstanceOfContext ctx) {
    return aliasAwareValue(ctx.name());
  }

  private InstanceUsage visitUsage(UsageContext ctx) {
    FshCode usageConcept = parseCodeLexeme(getText(ctx.CODE()), ctx.CODE())
        .withLocation(extractStartStop(ctx.CODE())).withFile(currentFile);
    if (Common.truthy(usageConcept.system)) {
      logger.warn("Do not specify a system for instance Usage.", usageConcept.sourceInfo);
    }
    String usage = Js.upperFirst(usageConcept.code);
    if (!InstanceUsage.isInstanceUsage(usage)) {
      logger.error("Invalid Usage. Supported usage codes are \"#example\", \"#definition\", and \"#inline\". "
          + "Instance will be treated as an example.", usageConcept.sourceInfo);
      usage = "Example";
    }
    return InstanceUsage.fromString(usage);
  }

  private String visitExpression(ExpressionContext ctx) {
    if (ctx.STRING() != null) return extractString(ctx.STRING());
    if (ctx.MULTILINE_STRING() != null) return extractMultilineString(ctx.MULTILINE_STRING().getText());
    // this can happen due to parsing errors, so just return empty string
    return "";
  }

  private String visitXpath(XpathContext ctx) {
    return extractString(ctx.STRING());
  }

  private FshCode visitSeverity(SeverityContext ctx) {
    return parseCodeLexeme(getText(ctx.CODE()), ctx.CODE()).withLocation(extractStartStop(ctx.CODE()))
        .withFile(currentFile);
  }

  private String visitSource(SourceContext ctx) {
    return aliasAwareValue(ctx.name());
  }

  private String visitTarget(TargetContext ctx) {
    return extractString(ctx.STRING());
  }

  private List<org.hl7.fhir.sushi.fshtypes.ExtensionContext> visitContext(ContextContext ctx) {
    List<org.hl7.fhir.sushi.fshtypes.ExtensionContext> contexts = new ArrayList<>();
    for (ContextItemContext contextItem : ctx.contextItem()) {
      if (contextItem.QUOTED_CONTEXT() != null) {
        String t = contextItem.QUOTED_CONTEXT().getText();
        contexts.add(new org.hl7.fhir.sushi.fshtypes.ExtensionContext(
            unescapeQuotedString(Js.trim(t.substring(0, Math.max(0, t.length() - 1)))), true,
            src(extractStartStop(contextItem.QUOTED_CONTEXT()))));
      } else {
        String t = getText(contextItem.UNQUOTED_CONTEXT());
        contexts.add(new org.hl7.fhir.sushi.fshtypes.ExtensionContext(
            Js.trim(t.substring(0, Math.max(0, t.length() - 1))), false,
            src(extractStartStop(contextItem.UNQUOTED_CONTEXT()))));
      }
    }
    LastContextItemContext last = ctx.lastContextItem();
    if (last == null) throw readNull("LAST_QUOTED_CONTEXT");
    if (last.LAST_QUOTED_CONTEXT() != null) {
      contexts.add(new org.hl7.fhir.sushi.fshtypes.ExtensionContext(
          unescapeQuotedString(last.LAST_QUOTED_CONTEXT().getText()), true,
          src(extractStartStop(last.LAST_QUOTED_CONTEXT()))));
    } else {
      contexts.add(new org.hl7.fhir.sushi.fshtypes.ExtensionContext(getText(last.LAST_UNQUOTED_CONTEXT()), false,
          src(extractStartStop(last.LAST_UNQUOTED_CONTEXT()))));
    }
    return contexts;
  }

  private List<String> visitCharacteristics(CharacteristicsContext ctx) {
    List<String> characteristics = new ArrayList<>();
    for (TerminalNode codeCtx : ctx.CODE_ITEM()) {
      String t = codeCtx.getText();
      characteristics.add(jsSlice(Js.trim(t.substring(0, Math.max(0, t.length() - 1))), 1));
    }
    String lastCode = jsSlice(Js.trim(getText(ctx.LAST_CODE_ITEM())), 1);
    characteristics.add(lastCode);
    return characteristics;
  }

  private static String jsSlice(String s, int from) {
    return from >= s.length() ? "" : s.substring(from);
  }

  private FshCode parseCodeLexeme(String conceptText, ParseTree parentCtx) {
    FshCode concept = ParseCodeLexeme.parseCodeLexeme(conceptText);
    if (Common.truthy(concept.system)) {
      concept.system = aliasAwareValue(parentCtx, concept.system);
    }
    return concept;
  }

  private List<Rule> visitLrRule(LrRuleContext ctx) {
    if (ctx.addElementRule() != null) return new ArrayList<>(List.of(visitAddElementRule(ctx.addElementRule())));
    if (ctx.addCRElementRule() != null) {
      return new ArrayList<>(List.of(visitAddCRElementRule(ctx.addCRElementRule())));
    }
    if (ctx.sdRule() != null) return visitSdRule(ctx.sdRule());
    logger.warn("Unsupported rule: " + ctx.getText(), src(extractStartStop(ctx)));
    return new ArrayList<>();
  }

  private AddElementRule visitAddElementRule(AddElementRuleContext ctx) {
    AddElementRule addElementRule = parseNewElement(ctx, ctx.path(), ctx.CARD(), ctx.flag(), ctx.STRING(),
        ctx.MULTILINE_STRING());
    addElementRule.types = parseTargetType(ctx.targetType());
    for (OnlyRuleType onlyRuleType : addElementRule.types) {
      if (FLAGS.contains(onlyRuleType.type)) {
        logger.warn("The targetType '" + onlyRuleType.type
            + "' appears to be a flag value rather than a valid target data type.", src(extractStartStop(ctx)));
      }
    }
    return addElementRule;
  }

  private AddElementRule visitAddCRElementRule(AddCRElementRuleContext ctx) {
    AddElementRule addElementRule = parseNewElement(ctx, ctx.path(), ctx.CARD(), ctx.flag(), ctx.STRING(),
        ctx.MULTILINE_STRING());
    if (ctx.SEQUENCE() != null) {
      addElementRule.contentReference = aliasAwareValue(ctx.SEQUENCE());
    } else if (ctx.CODE() != null) {
      addElementRule.contentReference = aliasAwareValue(ctx.CODE());
    }
    return addElementRule;
  }

  private AddElementRule parseNewElement(ParserRuleContext ctx, PathContext pathCtx, TerminalNode cardNode,
      List<FlagContext> flags, List<TerminalNode> strings, TerminalNode multiline) {
    String path = getPathWithContext(visitPath(pathCtx), ctx, false, false);
    AddElementRule addElementRule = new AddElementRule(path).withLocation(extractStartStop(ctx)).withFile(currentFile);

    Card card = parseCard(getText(cardNode), addElementRule.path, addElementRule.sourceInfo);
    if (card.min() == null || Double.isNaN(card.min())) {
      logger.error("The 'min' cardinality attribute in AddElementRule for path '" + path + "' must be specified.",
          src(extractStartStop(ctx)));
    }
    if (card.max() == null || card.max().isEmpty()) {
      logger.error("The 'max' cardinality attribute in AddElementRule for path '" + path + "' must be specified.",
          src(extractStartStop(ctx)));
    }
    addElementRule.min = card.min();
    addElementRule.max = card.max();

    if (flags != null && !flags.isEmpty()) parseFlags(addElementRule, flags);

    if (strings.isEmpty()) {
      logger.error("The 'short' attribute in AddElementRule for path '" + path + "' must be specified.",
          src(extractStartStop(ctx)));
    } else {
      addElementRule.short_ = extractString(strings.get(0));
      if (strings.size() < 2 && multiline == null) {
        // Default definition to the value of short
        addElementRule.definition = addElementRule.short_;
      } else if (strings.size() >= 2) {
        addElementRule.definition = extractString(strings.get(1));
      } else {
        addElementRule.definition = extractMultilineString(multiline.getText());
      }
    }
    return addElementRule;
  }

  private List<Rule> visitSdRule(SdRuleContext ctx) {
    List<Rule> out = new ArrayList<>();
    if (ctx.cardRule() != null) {
      out.addAll(visitCardRule(ctx.cardRule()));
    } else if (ctx.flagRule() != null) {
      out.addAll(visitFlagRule(ctx.flagRule()));
    } else if (ctx.valueSetRule() != null) {
      out.add(visitValueSetRule(ctx.valueSetRule()));
    } else if (ctx.fixedValueRule() != null) {
      out.add(visitFixedValueRule(ctx.fixedValueRule()));
    } else if (ctx.onlyRule() != null) {
      out.add(visitOnlyRule(ctx.onlyRule()));
    } else if (ctx.containsRule() != null) {
      out.addAll(visitContainsRule(ctx.containsRule()));
    } else if (ctx.caretValueRule() != null) {
      out.add(visitCaretValueRule(ctx.caretValueRule()));
    } else if (ctx.obeysRule() != null) {
      out.addAll(visitObeysRule(ctx.obeysRule()));
    } else if (ctx.insertRule() != null) {
      InsertRule rule = visitInsertRule(ctx.insertRule(), false);
      if (rule != null) out.add(rule);
    } else if (ctx.pathRule() != null) {
      visitPathRule(ctx.pathRule(), false);
    } else {
      logger.warn("Unsupported rule: " + ctx.getText(), src(extractStartStop(ctx)));
    }
    return out;
  }

  private Rule visitInstanceRule(InstanceRuleContext ctx) {
    if (ctx.fixedValueRule() != null) return visitFixedValueRule(ctx.fixedValueRule());
    if (ctx.insertRule() != null) return visitInsertRule(ctx.insertRule(), false);
    if (ctx.pathRule() != null) return visitPathRule(ctx.pathRule(), true);
    return null;
  }

  private Rule visitVsRule(VsRuleContext ctx) {
    if (ctx.vsComponent() != null) return visitVsComponent(ctx.vsComponent());
    if (ctx.caretValueRule() != null) {
      CaretValueRule rule = visitCaretValueRule(ctx.caretValueRule());
      if (ctx.caretValueRule().path() != null) {
        logger.error("Caret rule on ValueSet cannot contain path before ^, skipping rule.", rule.sourceInfo);
      } else {
        // if this rule is indented, it may have a concept context but it never needs a regular path.
        rule.path = "";
        return rule;
      }
    } else if (ctx.codeCaretValueRule() != null) {
      CaretValueRule rule = visitCodeCaretValueRule(ctx.codeCaretValueRule(), true);
      // the rule needs to have a caretPath, a value, and a pathArray with one element.
      // various syntax errors may lead to some of these values being missing.
      if (rule.pathArray.size() > 1) {
        logger.error("Only one concept may be listed before a caret rule on a ValueSet.", rule.sourceInfo);
      } else if (rule.caretPath != null && rule.value != null) {
        return rule;
      }
    } else if (ctx.insertRule() != null) {
      return visitInsertRule(ctx.insertRule(), true);
    } else if (ctx.codeInsertRule() != null) {
      InsertRule rule = visitCodeInsertRule(ctx.codeInsertRule(), true);
      if (rule == null) throw readUndefined("pathArray");
      if (rule.pathArray.size() > 1) {
        logger.error("Only one concept may be listed before an insert rule on a ValueSet.", rule.sourceInfo);
      } else {
        return rule;
      }
    }
    return null;
  }

  private Rule visitCsRule(CsRuleContext ctx) {
    if (ctx.concept() != null) return visitConcept(ctx.concept());
    if (ctx.codeCaretValueRule() != null) return visitCodeCaretValueRule(ctx.codeCaretValueRule(), false);
    if (ctx.codeInsertRule() != null) return visitCodeInsertRule(ctx.codeInsertRule(), false);
    return null;
  }

  private Rule visitInvariantRule(InvariantRuleContext ctx) {
    if (ctx.fixedValueRule() != null) return visitFixedValueRule(ctx.fixedValueRule());
    if (ctx.insertRule() != null) return visitInsertRule(ctx.insertRule(), false);
    if (ctx.pathRule() != null) visitPathRule(ctx.pathRule(), false);
    return null;
  }

  private Rule visitMappingEntityRule(MappingEntityRuleContext ctx) {
    if (ctx.mappingRule() != null) return visitMappingRule(ctx.mappingRule());
    if (ctx.insertRule() != null) return visitInsertRule(ctx.insertRule(), false);
    if (ctx.pathRule() != null) {
      // A path rule may swallow a mapping rule that has no spaces, so catch that case here
      if (visitPath(ctx.pathRule().path()).contains("->")) {
        logger.error("Mapping rules must include at least one space both before and after the '->' operator",
            src(extractStartStop(ctx.pathRule())));
      }
      visitPathRule(ctx.pathRule(), false);
    }
    return null;
  }

  String getPathWithContext(String path, ParserRuleContext parentCtx, boolean isPathRule, boolean isInstanceRule) {
    List<String> splitPath = splitPath(path);
    return String.join(".", getArrayPathWithContext(splitPath, parentCtx, isPathRule, isInstanceRule, false));
  }

  private static List<String> splitPath(String path) {
    if (path.equals(".")) return new ArrayList<>(List.of(path));
    List<String> out = new ArrayList<>();
    for (String p : FhirCommonBridge.splitOnPathPeriods(path)) {
      if (!p.isEmpty()) out.add(p);
    }
    return out;
  }

  List<String> getArrayPathWithContext(List<String> pathArray, ParserRuleContext parentCtx, boolean isPathRule,
      boolean isInstanceRule, boolean suppressError) {
    return prependPathContext(pathArray, parentCtx, isPathRule, isInstanceRule, suppressError);
  }

  private static String visitPath(PathContext ctx) {
    return ctx == null ? "" : ctx.getText();
  }

  private static String visitCaretPath(CaretPathContext ctx) {
    if (ctx == null) throw readNull("CARET_SEQUENCE");
    return getText(ctx.CARET_SEQUENCE());
  }

  private List<Rule> visitCardRule(CardRuleContext ctx) {
    List<Rule> rules = new ArrayList<>();
    CardRule cardRule = new CardRule(getPathWithContext(visitPath(ctx.path()), ctx, false, false))
        .withLocation(extractStartStop(ctx)).withFile(currentFile);
    Card card = parseCard(getText(ctx.CARD()), cardRule.path, cardRule.sourceInfo);
    cardRule.min = card.min();
    cardRule.max = card.max();
    rules.add(cardRule);

    if (ctx.flag() != null && !ctx.flag().isEmpty()) {
      FlagRule flagRule = new FlagRule(cardRule.path).withLocation(extractStartStop(ctx)).withFile(currentFile);
      parseFlags(flagRule, ctx.flag());
      rules.add(flagRule);
    }
    return rules;
  }

  private record Card(Double min, String max) {}

  private Card parseCard(String card, String rulePath, SourceInfo ruleSourceInfo) {
    // JS card.split('..', 2): split everywhere, keep the first two parts
    String[] all = card.split(Pattern.quote(".."), -1);
    String p0 = all[0];
    String p1 = all.length > 1 ? all[1] : null;
    if ("".equals(p0) && "".equals(p1)) {
      logger.error("Neither side of the cardinality was specified on path \"" + rulePath
          + "\". A min, max, or both need to be specified.", ruleSourceInfo);
    }
    return new Card(ImporterText.jsParseInt(p0), p1);
  }

  private List<FlagRule> visitFlagRule(FlagRuleContext ctx) {
    List<FlagRule> out = new ArrayList<>();
    for (PathContext path : ctx.path()) {
      FlagRule flagRule = new FlagRule(getPathWithContext(visitPath(path), ctx, false, false))
          .withLocation(extractStartStop(ctx)).withFile(currentFile);
      parseFlags(flagRule, ctx.flag());
      out.add(flagRule);
    }
    return out;
  }

  private void parseFlags(FlagCarryingRule flagRule, List<FlagContext> flagContext) {
    List<Flag> flags = new ArrayList<>();
    for (FlagContext f : flagContext) flags.add(visitFlag(f));
    if (flags.contains(Flag.MustSupport)) flagRule.mustSupport = true;
    if (flags.contains(Flag.Summary)) flagRule.summary = true;
    if (flags.contains(Flag.Modifier)) flagRule.modifier = true;
    if (flags.contains(Flag.TrialUse)) flagRule.trialUse = true;
    if (flags.contains(Flag.Normative)) flagRule.normative = true;
    if (flags.contains(Flag.Draft)) flagRule.draft = true;
  }

  private static Flag visitFlag(FlagContext ctx) {
    if (ctx.KW_MS() != null) return Flag.MustSupport;
    if (ctx.KW_SU() != null) return Flag.Summary;
    if (ctx.KW_MOD() != null) return Flag.Modifier;
    if (ctx.KW_TU() != null) return Flag.TrialUse;
    if (ctx.KW_NORMATIVE() != null) return Flag.Normative;
    if (ctx.KW_DRAFT() != null) return Flag.Draft;
    return Flag.Unknown;
  }

  private BindingRule visitValueSetRule(ValueSetRuleContext ctx) {
    BindingRule vsRule = new BindingRule(getPathWithContext(visitPath(ctx.path()), ctx, false, false))
        .withLocation(extractStartStop(ctx)).withFile(currentFile);
    vsRule.valueSet = aliasAwareValue(ctx.name());
    vsRule.strength = ctx.strength() != null ? visitStrength(ctx.strength()) : "required";
    return vsRule;
  }

  private static String visitStrength(StrengthContext ctx) {
    if (ctx.KW_EXAMPLE() != null) return "example";
    if (ctx.KW_PREFERRED() != null) return "preferred";
    if (ctx.KW_EXTENSIBLE() != null) return "extensible";
    return "required";
  }

  private AssignmentRule visitFixedValueRule(FixedValueRuleContext ctx) {
    AssignmentRule assignmentRule = new AssignmentRule(getPathWithContext(visitPath(ctx.path()), ctx, false, false))
        .withLocation(extractStartStop(ctx)).withFile(currentFile);
    assignmentRule.value = visitValue(ctx.value());
    // for numbers and booleans, keep the raw value to handle cases of unusual Instance id
    if (ctx.value() != null && ctx.value().NUMBER() != null) assignmentRule.rawValue = ctx.value().NUMBER().getText();
    if (ctx.value() != null && ctx.value().bool() != null) assignmentRule.rawValue = ctx.value().bool().getText();
    assignmentRule.exactly = ctx.KW_EXACTLY() != null;
    if (ctx.value() == null) throw readNull("name");
    assignmentRule.isInstance = ctx.value().name() != null && !allAliases.containsKey(ctx.value().name().getText());
    return assignmentRule;
  }

  private Object visitValue(ValueContext ctx) {
    // In cases where the parser encounters an error, ctx might be null
    if (ctx == null) return null;
    if (ctx.name() != null) return aliasAwareValue(ctx, ctx.name().getText());
    if (ctx.STRING() != null) return extractString(ctx.STRING());
    if (ctx.MULTILINE_STRING() != null) return extractMultilineString(ctx.MULTILINE_STRING().getText());
    if (ctx.NUMBER() != null) return extractNumberValue(ctx.NUMBER().getText());
    // for now, treat datetime and time like a string
    if (ctx.DATETIME() != null) return ctx.DATETIME().getText();
    if (ctx.TIME() != null) return ctx.TIME().getText();
    if (ctx.reference() != null) return visitReference(ctx.reference());
    if (ctx.canonical() != null) {
      List<FshCanonical> canonicals = visitCanonical(ctx.canonical());
      if (canonicals.size() > 1) {
        logger.error("Multiple choices of canonicals are not allowed when setting a value. "
            + "Only the first choice will be used.", src(extractStartStop(ctx.canonical())));
      }
      return canonicals.get(0);
    }
    if (ctx.code() != null) return visitCode(ctx.code());
    if (ctx.quantity() != null) return visitQuantity(ctx.quantity());
    if (ctx.ratio() != null) return visitRatio(ctx.ratio());
    if (ctx.bool() != null) return visitBool(ctx.bool());
    return null;
  }

  private FshCode visitCode(CodeContext ctx) {
    FshCode concept = parseCodeLexeme(getText(ctx.CODE()), ctx.CODE()).withLocation(extractStartStop(ctx))
        .withFile(currentFile);
    if (ctx.STRING() != null) concept.display = extractString(ctx.STRING());
    return concept;
  }

  private ConceptRule visitConcept(ConceptContext ctx) {
    List<FshCode> localCodePath = new ArrayList<>();
    for (TerminalNode codeCtx : ctx.CODE()) localCodePath.add(parseCodeLexeme(codeCtx.getText(), codeCtx));
    // the last code in allCodes is the one we are actually defining.
    // the rest are the hierarchy, which may be empty.
    // indentation may also be used to define the hierarchy, which is what the code path with context is for.
    // the # is included in the array path since it is needed for caret rules.
    List<String> hashed = new ArrayList<>();
    for (FshCode localCode : localCodePath) hashed.add("#" + Common.jsString(localCode.code));
    List<String> fullCodePath = getArrayPathWithContext(hashed, ctx, false, false, false);
    if (localCodePath.isEmpty()) throw readUndefined("code");
    FshCode codePart = localCodePath.get(localCodePath.size() - 1);
    List<String> availableStrings = new ArrayList<>();
    for (TerminalNode strCtx : ctx.STRING()) availableStrings.add(extractString(strCtx));
    ConceptRule concept = new ConceptRule(codePart.code).withLocation(extractStartStop(ctx)).withFile(currentFile);
    // concepts in the array path have a leading #, which we don't want in the hierarchy
    List<String> hierarchy = new ArrayList<>();
    for (String code : fullCodePath.subList(0, Math.max(0, fullCodePath.size() - 1))) hierarchy.add(code.substring(1));
    concept.hierarchy = hierarchy;
    if (!availableStrings.isEmpty()) concept.display = availableStrings.get(0);
    if (availableStrings.size() > 1) {
      concept.definition = availableStrings.get(1);
    } else if (ctx.MULTILINE_STRING() != null) {
      concept.definition = extractMultilineString(ctx.MULTILINE_STRING().getText());
    }
    if (localCodePath.stream().anyMatch(listedConcept -> Common.truthy(listedConcept.system))) {
      // If this concept is part of a RuleSet and has a system, no definition, and no hierarchy,
      // it might actually represent a ValueSetConceptComponent. We can't know for sure until the RuleSet
      // is inserted somewhere. For now, assume it will be okay, so keep the system for later.
      if (ctx.getParent() instanceof RuleSetRuleContext && Common.truthy(codePart.system)
          && !Common.truthy(concept.definition) && concept.hierarchy.isEmpty()) {
        concept.system = codePart.system;
      } else {
        logger.error("Do not include the system when listing concepts for a code system.", concept.sourceInfo);
      }
    }
    return concept;
  }

  private FshQuantity visitQuantity(QuantityContext ctx) {
    Double value = null;
    if (ctx.NUMBER() != null) value = Double.parseDouble(ctx.NUMBER().getText());
    String delimitedUnit = ctx.UNIT() != null ? ctx.UNIT().getText() : ""; // e.g., 'mm'
    // We'll want to assume the UCUM code system unless another system is specified
    String unitSystem = "http://unitsofmeasure.org";
    // If there's no unit string, then we're using FSHCode syntax
    if (delimitedUnit.isEmpty()) {
      FshCode unitCode = parseCodeLexeme(getText(ctx.CODE()), ctx.CODE()).withLocation(extractStartStop(ctx))
          .withFile(currentFile);
      unitSystem = unitCode.system;
      delimitedUnit = unitCode.code;
    } else {
      delimitedUnit = delimitedUnit.length() >= 2 ? delimitedUnit.substring(1, delimitedUnit.length() - 1) : "";
    }
    String displayUnit = null;
    if (ctx.STRING() != null) displayUnit = extractString(ctx.STRING());
    FshCode unit = new FshCode(delimitedUnit, unitSystem, displayUnit)
        .withLocation(ctx.UNIT() != null ? extractStartStop(ctx.UNIT()) : extractStartStop(ctx)).withFile(currentFile);
    return new FshQuantity(value, unit).withLocation(extractStartStop(ctx)).withFile(currentFile);
  }

  private FshRatio visitRatio(RatioContext ctx) {
    List<RatioPartContext> parts = ctx.ratioPart();
    FshQuantity numerator = visitRatioPart(parts.size() > 0 ? parts.get(0) : null);
    FshQuantity denominator = visitRatioPart(parts.size() > 1 ? parts.get(1) : null);
    return new FshRatio(numerator, denominator).withLocation(extractStartStop(ctx)).withFile(currentFile);
  }

  private FshQuantity visitRatioPart(RatioPartContext ctx) {
    if (ctx == null) throw readUndefined("NUMBER");
    if (ctx.NUMBER() != null) {
      return new FshQuantity(Double.parseDouble(ctx.NUMBER().getText()))
          .withLocation(extractStartStop(ctx.NUMBER())).withFile(currentFile);
    }
    if (ctx.quantity() == null) throw readNull("NUMBER");
    return visitQuantity(ctx.quantity());
  }

  // This function is called when fixing a value, and a value can only be set
  // to a specific reference, not a choice of references.
  private FshReference visitReference(ReferenceContext ctx) {
    List<String> parsedReferences = parseOrReference(getText(ctx.REFERENCE()));
    FshReference ref = new FshReference(aliasAwareValue(ctx.REFERENCE(), parsedReferences.get(0)));
    if (parsedReferences.size() > 1) {
      logger.error("Multiple choices of references are not allowed when setting a value. "
          + "Only the first choice will be used.", src(extractStartStop(ctx)));
    }
    ref.withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (ctx.STRING() != null) ref.display = extractString(ctx.STRING());
    return ref;
  }

  private static List<String> parseOrReference(String reference) {
    String inner = jsSliceRange(reference, reference.indexOf('(') + 1, reference.length() - 1);
    List<String> out = new ArrayList<>();
    for (String r : OR_SPLIT.split(inner, -1)) out.add(Js.trim(r));
    return out;
  }

  /** JS s.slice(a, b) for 0 <= a, b possibly < a. */
  private static String jsSliceRange(String s, int a, int b) {
    a = Math.max(0, Math.min(a, s.length()));
    b = Math.max(0, Math.min(b, s.length()));
    return b <= a ? "" : s.substring(a, b);
  }

  private List<FshCanonical> visitCanonical(CanonicalContext ctx) {
    String canonicalText = getText(ctx.CANONICAL());
    String inner = jsSliceRange(canonicalText, canonicalText.indexOf('(') + 1, canonicalText.length() - 1);
    List<FshCanonical> out = new ArrayList<>();
    for (String raw : OR_SPLIT.split(inner, -1)) {
      String c = Js.trim(raw);
      // [item, version] = c.split(/\s*\|\s*(.+)/).map(trim)
      String item;
      String version = null;
      Matcher m = CANONICAL_VERSION.matcher(c);
      if (m.find()) {
        item = Js.trim(c.substring(0, m.start()));
        version = Js.trim(m.group(1));
      } else {
        item = Js.trim(c);
      }
      FshCanonical fshCanonical = new FshCanonical(item).withLocation(extractStartStop(ctx)).withFile(currentFile);
      if (Common.truthy(version)) fshCanonical.version = version;
      out.add(fshCanonical);
    }
    return out;
  }

  private static boolean visitBool(BoolContext ctx) {
    return ctx.KW_TRUE() != null;
  }

  private OnlyRule visitOnlyRule(OnlyRuleContext ctx) {
    OnlyRule onlyRule = new OnlyRule(getPathWithContext(visitPath(ctx.path()), ctx, false, false))
        .withLocation(extractStartStop(ctx)).withFile(currentFile);
    onlyRule.types = parseTargetType(ctx.targetType());
    return onlyRule;
  }

  private List<OnlyRuleType> parseTargetType(List<TargetTypeContext> targetTypes) {
    List<OnlyRuleType> orTypes = new ArrayList<>();
    for (TargetTypeContext t : targetTypes) {
      if (t.referenceType() != null) {
        TerminalNode referenceToken = t.referenceType().REFERENCE();
        for (String r : parseOrReference(getText(referenceToken))) {
          OnlyRuleType type = new OnlyRuleType(aliasAwareValue(referenceToken, r));
          type.isReference = true;
          orTypes.add(type);
        }
      } else if (t.codeableReferenceType() != null) {
        TerminalNode codeableReferenceToken = t.codeableReferenceType().CODEABLE_REFERENCE();
        for (String r : parseOrReference(getText(codeableReferenceToken))) {
          OnlyRuleType type = new OnlyRuleType(aliasAwareValue(codeableReferenceToken, r));
          type.isCodeableReference = true;
          orTypes.add(type);
        }
      } else if (t.canonical() != null) {
        for (FshCanonical c : visitCanonical(t.canonical())) {
          OnlyRuleType type = new OnlyRuleType(aliasAwareValue(t.canonical(), c.entityName)
              + (Common.truthy(c.version) ? "|" + c.version : ""));
          type.isCanonical = true;
          orTypes.add(type);
        }
      } else {
        orTypes.add(new OnlyRuleType(aliasAwareValue(t.name())));
      }
    }
    return orTypes;
  }

  private List<Rule> visitContainsRule(ContainsRuleContext ctx) {
    List<Rule> rules = new ArrayList<>();
    ContainsRule containsRule = new ContainsRule(getPathWithContext(visitPath(ctx.path()), ctx, false, false))
        .withLocation(extractStartStop(ctx)).withFile(currentFile);
    rules.add(containsRule);
    for (ItemContext i : ctx.item()) {
      List<NameContext> names = i.name();
      ContainsRuleItem item;
      if (i.KW_NAMED() != null) {
        NameContext n0 = names.isEmpty() ? null : names.get(0);
        if (n0 == null) throw readUndefined("getText");
        String type = aliasAwareValue(n0, n0.getText());
        if (names.size() < 2) throw readUndefined("getText");
        item = new ContainsRuleItem(names.get(1).getText(), type);
      } else {
        if (names.isEmpty()) throw readUndefined("getText");
        item = new ContainsRuleItem(names.get(0).getText());
      }
      containsRule.items.add(item);

      CardRule cardRule = new CardRule(containsRule.path + "[" + item.name + "]").withLocation(extractStartStop(ctx))
          .withFile(currentFile);
      Card card = parseCard(getText(i.CARD()), cardRule.path, cardRule.sourceInfo);
      cardRule.min = card.min();
      cardRule.max = card.max();
      rules.add(cardRule);

      if (i.flag() != null && !i.flag().isEmpty()) {
        FlagRule flagRule = new FlagRule(containsRule.path + "[" + item.name + "]")
            .withLocation(extractStartStop(ctx)).withFile(currentFile);
        parseFlags(flagRule, i.flag());
        rules.add(flagRule);
      }
    }
    return rules;
  }

  private CaretValueRule visitCaretValueRule(CaretValueRuleContext ctx) {
    String path = visitPath(ctx.path());
    List<String> splitPath = splitPath(path);
    List<String> pathArray = getArrayPathWithContext(splitPath, ctx, false, false, false);
    CaretValueRule caretValueRule = new CaretValueRule(String.join(".", pathArray))
        .withLocation(extractStartStop(ctx)).withFile(currentFile);
    // We must save the path in array form, in case this rule is a part of a RuleSet applied on a CodeSystem
    caretValueRule.pathArray = pathArray;
    // Get the caret path, but slice off the starting ^
    caretValueRule.caretPath = jsSlice(visitCaretPath(ctx.caretPath()), 1);
    caretValueRule.value = visitValue(ctx.value());
    // for numbers and booleans, keep the raw value to handle cases of unusual Instance id
    if (ctx.value() != null && ctx.value().NUMBER() != null) caretValueRule.rawValue = ctx.value().NUMBER().getText();
    if (ctx.value() != null && ctx.value().bool() != null) caretValueRule.rawValue = ctx.value().bool().getText();
    caretValueRule.isInstance = ctx.value() != null && ctx.value().name() != null
        && !allAliases.containsKey(ctx.value().name().getText());
    return caretValueRule;
  }

  // when parsing a ValueSet, we need to keep the system. in all other cases, the system is not needed.
  private CaretValueRule visitCodeCaretValueRule(CodeCaretValueRuleContext ctx, boolean keepSystem) {
    List<String> localCodePath = new ArrayList<>();
    for (TerminalNode code : ctx.CODE()) {
      FshCode parsedCode = parseCodeLexeme(code.getText(), ctx);
      localCodePath.add(keepSystem ? (parsedCode.system != null ? parsedCode.system : "") + "#" + parsedCode.code
          : "#" + parsedCode.code);
    }
    List<String> fullCodePath = getArrayPathWithContext(localCodePath, ctx, false, false, false);
    // It's fine to make a CodeCaretValueRule with an empty code path.
    CaretValueRule caretRule = new CaretValueRule("").withLocation(extractStartStop(ctx)).withFile(currentFile);
    caretRule.pathArray = fullCodePath;
    // Get the caret path, but slice off the starting ^
    caretRule.caretPath = jsSlice(visitCaretPath(ctx.caretPath()), 1);
    caretRule.value = visitValue(ctx.value());
    // for numbers and booleans, keep the raw value to handle cases where an Instance id looks like a number
    if (ctx.value() != null && ctx.value().NUMBER() != null) caretRule.rawValue = ctx.value().NUMBER().getText();
    if (ctx.value() != null && ctx.value().bool() != null) caretRule.rawValue = ctx.value().bool().getText();
    caretRule.isInstance = ctx.value() != null && ctx.value().name() != null
        && !allAliases.containsKey(ctx.value().name().getText());
    return caretRule;
  }

  private List<ObeysRule> visitObeysRule(ObeysRuleContext ctx) {
    List<ObeysRule> rules = new ArrayList<>();
    String path = getPathWithContext(visitPath(ctx.path()), ctx, false, false);
    for (NameContext invariant : ctx.name()) {
      ObeysRule obeysRule = new ObeysRule(path).withLocation(extractStartStop(ctx)).withFile(currentFile);
      obeysRule.invariant = invariant.getText();
      rules.add(obeysRule);
    }
    return rules;
  }

  private PathRule visitPathRule(PathRuleContext ctx, boolean isInstanceRule) {
    return new PathRule(getPathWithContext(visitPath(ctx.path()), ctx, true, isInstanceRule))
        .withLocation(extractStartStop(ctx)).withFile(currentFile);
  }

  // when parsing a ValueSet, we need to keep the system. in all other cases, the system is not needed.
  private InsertRule visitCodeInsertRule(CodeInsertRuleContext ctx, boolean keepSystem) {
    InsertRule insertRule = new InsertRule("").withLocation(extractStartStop(ctx)).withFile(currentFile);
    List<String> localCodePath = new ArrayList<>();
    for (TerminalNode code : ctx.CODE()) {
      FshCode parsedCode = parseCodeLexeme(code.getText(), ctx);
      localCodePath.add(keepSystem ? (parsedCode.system != null ? parsedCode.system : "") + "#" + parsedCode.code
          : "#" + parsedCode.code);
    }
    insertRule.pathArray = getArrayPathWithContext(localCodePath, ctx, false, false, false);
    return applyRuleSetParams(ctx.paramRuleSetRef(), ctx.RULESET_REFERENCE(), insertRule);
  }

  private InsertRule visitInsertRule(InsertRuleContext ctx, boolean withPathArray) {
    String localPath = visitPath(ctx.path());
    List<String> fullPathArray = getArrayPathWithContext(
        localPath.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(localPath)), ctx, false, false, false);
    InsertRule insertRule =
        new InsertRule(String.join(".", fullPathArray)).withLocation(extractStartStop(ctx)).withFile(currentFile);
    if (withPathArray) insertRule.pathArray = fullPathArray;
    return applyRuleSetParams(ctx.paramRuleSetRef(), ctx.RULESET_REFERENCE(), insertRule);
  }

  private record RuleSetReference(String name, List<String> params) {}

  private static String dropLastTrim(String s) {
    return Js.trim(s.substring(0, Math.max(0, s.length() - 1)));
  }

  private static RuleSetReference parseRuleSetReference(ParamRuleSetRefContext ctx) {
    if (ctx == null) throw readNull("PARAM_RULESET_REFERENCE");
    String ruleSetName = dropLastTrim(getText(ctx.PARAM_RULESET_REFERENCE()));
    List<String> ruleSetParams = new ArrayList<>();
    for (ParameterContext param : ctx.parameter()) ruleSetParams.add(dropLastTrim(param.getText()));
    if (ctx.lastParameter() == null) throw readNull("getText");
    ruleSetParams.add(dropLastTrim(ctx.lastParameter().getText()));
    return new RuleSetReference(ruleSetName, ruleSetParams);
  }

  /** JS s.slice(2, -2) */
  private static String sliceBrackets(String s) {
    return jsSliceRange(s, 2, s.length() - 2);
  }

  private static List<String> parseInsertRuleParams(ParamRuleSetRefContext ctx) {
    List<String> params = new ArrayList<>();
    for (ParameterContext param : ctx.parameter()) {
      if (param.BRACKETED_PARAM() != null) {
        // trim off the brackets and comma, and unescape literal ]], and ]])
        params.add(BRACKETED_UNESCAPE.matcher(sliceBrackets(dropLastTrim(param.BRACKETED_PARAM().getText())))
            .replaceAll("$1$2$3"));
      } else {
        // trim off the trailing comma, and unescape , and )
        params.add(PLAIN_UNESCAPE.matcher(dropLastTrim(getText(param.PLAIN_PARAM()))).replaceAll("$1"));
      }
    }
    LastParameterContext last = ctx.lastParameter();
    if (last.LAST_BRACKETED_PARAM() != null) {
      // trim off the brackets and right parentheses, and unescape literal ]], and ]])
      params.add(BRACKETED_UNESCAPE.matcher(sliceBrackets(dropLastTrim(last.LAST_BRACKETED_PARAM().getText())))
          .replaceAll("$1$2$3"));
    } else {
      // trim off the trailing right parentheses, and unescape , and )
      params.add(PLAIN_UNESCAPE.matcher(dropLastTrim(getText(last.LAST_PLAIN_PARAM()))).replaceAll("$1"));
    }
    return params;
  }

  private InsertRule applyRuleSetParams(ParamRuleSetRefContext paramRef, TerminalNode rulesetRef,
      InsertRule insertRule) {
    String name;
    List<String> params = null;
    if (paramRef != null) {
      name = parseRuleSetReference(paramRef).name();
      params = parseInsertRuleParams(paramRef);
    } else {
      name = Js.trim(getText(rulesetRef));
    }

    insertRule.ruleSet = name;
    if (params != null) {
      insertRule.params = params;
      ParamRuleSet ruleSet = paramRuleSets.get(insertRule.ruleSet);
      if (ruleSet != null) {
        List<Object> idParts = new ArrayList<>();
        idParts.add(ruleSet.name);
        idParts.addAll(insertRule.params);
        String ruleSetIdentifier = Json.stringify(idParts);
        if (ruleSet.parameters.size() == insertRule.params.size()) {
          // no need to create the appliedRuleSet again if we already have it
          if (!currentDoc.appliedRuleSets.containsKey(ruleSetIdentifier)) {
            // create a new document with the substituted parameters
            String appliedFsh = MiniFSHImporter.applyRuleSetSubstitutions(ruleSet, insertRule.params);
            RuleSet appliedRuleSet = parseGeneratedRuleSet(appliedFsh, ruleSet.name, insertRule);
            if (appliedRuleSet != null) {
              // set the source info based on the original source info
              appliedRuleSet.sourceInfo.file = ruleSet.sourceInfo.file;
              appliedRuleSet.sourceInfo.location =
                  ruleSet.sourceInfo.location == null ? null : ruleSet.sourceInfo.location.copy();
              int shift = appliedRuleSet.sourceInfo.location.startLine - 1;
              for (Rule rule : appliedRuleSet.rules) {
                if (rule == null) throw readUndefined("sourceInfo");
                rule.sourceInfo.file = appliedRuleSet.sourceInfo.file;
                rule.sourceInfo.location.startLine += shift;
                rule.sourceInfo.location.endLine += shift;
              }
              currentDoc.appliedRuleSets.put(ruleSetIdentifier, appliedRuleSet);
            } else {
              logger.error("Failed to parse RuleSet " + insertRule.ruleSet + " with provided parameters ("
                  + String.join(", ", insertRule.params) + ")", insertRule.sourceInfo);
              return null;
            }
          }
        } else {
          logger.error("Incorrect number of parameters applied to RuleSet " + insertRule.ruleSet,
              insertRule.sourceInfo);
          return null;
        }
      } else {
        logger.error("Could not find parameterized RuleSet named " + insertRule.ruleSet, insertRule.sourceInfo);
        return null;
      }
    }
    return insertRule;
  }

  private RuleSet parseGeneratedRuleSet(String input, String name, InsertRule insertRule) {
    // define a temporary document that will contain this RuleSet
    FSHDocument tempDocument = new FSHDocument(currentFile);
    // save the currentDoc so it can be restored after parsing this RuleSet
    FSHDocument parentDocument = currentDoc;
    List<List<String>> parentContext = pathContext;
    currentDoc = tempDocument;
    // errors should be collected, not printed, when parsing generated documents
    // we should only retrieve errors if we are currently in the top-level parse
    FshLogger.LoggerData topLevelInfo = null;
    if (topLevelParse) {
      topLevelParse = false;
      topLevelInfo = logger.switchToSecretLogger();
    }
    try {
      DocContext subContext = parseDoc(input, null);
      visitDoc(subContext);
    } finally {
      // be sure to restore parentDocument
      currentDoc = parentDocument;
      pathContext = parentContext;
    }
    // if tempDocument has appliedRuleSets, merge them in
    tempDocument.appliedRuleSets.forEach((identifier, ruleSet) -> currentDoc.appliedRuleSets.put(identifier, ruleSet));
    if (topLevelInfo != null) {
      // exit logger collection mode, write collected errors and warnings
      FshLogger.ErrorsAndWarnings collectedMessages = logger.restoreMainLogger(topLevelInfo);
      topLevelParse = true;
      if (!collectedMessages.errors.isEmpty()) {
        List<String> lines = new ArrayList<>();
        lines.add("Error" + (collectedMessages.errors.size() > 1 ? "s" : "")
            + " parsing insert rule with parameterized RuleSet " + name);
        for (FshLogger.Tracked log : collectedMessages.errors) lines.add("- " + log.message());
        logger.error(String.join(Common.EOL, lines), insertRule.sourceInfo);
      }
      if (!collectedMessages.warnings.isEmpty()) {
        List<String> lines = new ArrayList<>();
        lines.add("Warning" + (collectedMessages.warnings.size() > 1 ? "s" : "")
            + " parsing insert rule with parameterized RuleSet " + name);
        for (FshLogger.Tracked log : collectedMessages.warnings) lines.add("- " + log.message());
        logger.warn(String.join(Common.EOL, lines), insertRule.sourceInfo);
      }
    }
    // if the RuleSet parsed successfully, it will be on the document, and we should return it.
    return tempDocument.ruleSets.get(name);
  }

  private MappingRule visitMappingRule(MappingRuleContext ctx) {
    MappingRule mappingRule = new MappingRule(getPathWithContext(visitPath(ctx.path()), ctx, false, false))
        .withLocation(extractStartStop(ctx)).withFile(currentFile);
    List<TerminalNode> strings = ctx.STRING();
    mappingRule.map = extractString(strings.isEmpty() ? null : strings.get(0));
    if (strings.size() > 1) {
      mappingRule.comment = extractString(strings.get(1));
    } else if (ctx.MULTILINE_STRING() != null) {
      mappingRule.comment = extractMultilineString(ctx.MULTILINE_STRING().getText());
    }
    if (ctx.CODE() != null) {
      mappingRule.language = parseCodeLexeme(ctx.CODE().getText(), ctx.CODE())
          .withLocation(extractStartStop(ctx.CODE())).withFile(currentFile);
      if (Common.truthy(mappingRule.language.system)) {
        logger.warn("Do not specify a system for mapping language.", mappingRule.language.sourceInfo);
      }
    }
    return mappingRule;
  }

  private ValueSetComponentRule visitVsComponent(VsComponentContext ctx) {
    boolean inclusion = ctx.KW_EXCLUDE() == null;
    ValueSetComponentRule vsComponent = null;
    if (ctx.vsConceptComponent() != null) {
      ValueSetConceptComponentRule cc = new ValueSetConceptComponentRule(inclusion)
          .withLocation(extractStartStop(ctx)).withFile(currentFile);
      ConceptComponent parts = visitVsConceptComponent(ctx.vsConceptComponent());
      cc.concepts = parts.concepts();
      cc.from = parts.from();
      if (cc.concepts.size() == 1) {
        // set context... but if this is indented, this will produce the "don't indent me" error a second time
        // so, we set a flag to tell it to not produce that error.
        getArrayPathWithContext(new ArrayList<>(List.of(Common.jsString(cc.concepts.get(0).system) + "#"
            + Common.jsString(cc.concepts.get(0).code))), ctx, false, false, true);
      } else {
        // reset the context
        getArrayPathWithContext(new ArrayList<>(), ctx, false, false, true);
      }
      vsComponent = cc;
    } else if (ctx.vsFilterComponent() != null) {
      ValueSetFilterComponentRule fc = new ValueSetFilterComponentRule(inclusion)
          .withLocation(extractStartStop(ctx)).withFile(currentFile);
      FilterComponent parts = visitVsFilterComponent(ctx.vsFilterComponent());
      fc.filters = parts.filters();
      fc.from = parts.from();
      // reset the context
      getArrayPathWithContext(new ArrayList<>(), ctx, false, false, true);
      vsComponent = fc;
    }
    return vsComponent;
  }

  private record ConceptComponent(List<FshCode> concepts, ValueSetComponentFrom from) {}

  private record FilterComponent(List<ValueSetFilter> filters, ValueSetComponentFrom from) {}

  private ConceptComponent visitVsConceptComponent(VsConceptComponentContext ctx) {
    List<FshCode> concepts = new ArrayList<>();
    ValueSetComponentFrom from =
        ctx.vsComponentFrom() != null ? visitVsComponentFrom(ctx.vsComponentFrom()) : new ValueSetComponentFrom();
    if (ctx.code() == null) throw readNull("CODE");
    FshCode singleCode = visitCode(ctx.code());
    if (Common.truthy(singleCode.system) && Common.truthy(from.system)) {
      logger.error("Concept " + singleCode.code + " specifies system multiple times", src(extractStartStop(ctx)));
    } else if (Common.truthy(singleCode.system)) {
      from.system = singleCode.system;
      concepts.add(singleCode);
    } else if (Common.truthy(from.system)) {
      singleCode.system = from.system;
      concepts.add(singleCode);
    } else {
      logger.error("Concept " + singleCode.code
          + " must include system as \"SYSTEM#CONCEPT\" or \"#CONCEPT from system SYSTEM\"",
          src(extractStartStop(ctx)));
    }
    return new ConceptComponent(concepts, from);
  }

  private FilterComponent visitVsFilterComponent(VsFilterComponentContext ctx) {
    List<ValueSetFilter> filters = new ArrayList<>();
    ValueSetComponentFrom from =
        ctx.vsComponentFrom() != null ? visitVsComponentFrom(ctx.vsComponentFrom()) : new ValueSetComponentFrom();
    if (ctx.vsFilterList() != null) {
      if (Common.truthy(from.system)) {
        for (VsFilterDefinitionContext filterDefinition : ctx.vsFilterList().vsFilterDefinition()) {
          try {
            filters.add(visitVsFilterDefinition(filterDefinition));
          } catch (RuntimeException e) {
            // TS logs the Error object itself; winston prints its toString(): "<name>: <message>"
            String errName = e instanceof JsTypeError ? "TypeError" : "Error";
            logger.error(errName + ": " + Common.jsString(e.getMessage()), src(extractStartStop(filterDefinition)));
          }
        }
      } else {
        logger.error("System is required when filtering a value set component", src(extractStartStop(ctx)));
      }
    }
    return new FilterComponent(filters, from);
  }

  private ValueSetComponentFrom visitVsComponentFrom(VsComponentFromContext ctx) {
    ValueSetComponentFrom from = new ValueSetComponentFrom();
    if (ctx.vsFromSystem() != null) from.system = aliasAwareValue(ctx.vsFromSystem().name());
    if (ctx.vsFromValueset() != null && !ctx.vsFromValueset().name().isEmpty()) {
      List<String> valueSets = new ArrayList<>();
      for (NameContext name : ctx.vsFromValueset().name()) valueSets.add(aliasAwareValue(name));
      from.valueSets = valueSets;
    }
    return from;
  }

  /** Thrown for an operator SUSHI doesn't know combined with a missing value (TS passes the raw text). */
  private static final class MissingValueError extends RuntimeException {
    MissingValueError(String operator) {
      super("Not applying value set filter: missing value for operator \"" + operator + "\".");
    }
  }

  /**
   * The replace makes FSH permissive in regards to the official specifications, which spells operator
   * "descendant-of" as "descendent-of". See http://hl7.org/fhir/valueset-filter-operator.html
   */
  private ValueSetFilter visitVsFilterDefinition(VsFilterDefinitionContext ctx) {
    String property = getText(ctx.name());
    if (ctx.vsFilterOperator() == null) throw readNull("getText");
    String operatorText = ctx.vsFilterOperator().getText().toLowerCase(Locale.ROOT);
    int d = operatorText.indexOf("descendant");
    if (d >= 0) operatorText = operatorText.substring(0, d) + "descendent" + operatorText.substring(d + 10);
    VsOperator operator = VsOperator.fromValue(operatorText);
    if (ctx.vsFilterValue() == null && operator != VsOperator.EXISTS) {
      if (operator == null) throw new MissingValueError(operatorText);
      throw new ValueSetFilterMissingValueError(operator);
    }
    Object value = ctx.vsFilterValue() != null ? visitVsFilterValue(ctx.vsFilterValue()) : Boolean.TRUE;

    // NOTE: We support string value for every operator, in addition to the specific typed values
    // for some operators based on the filter value documentation:
    // http://hl7.org/fhir/R4/valueset-definitions.html#ValueSet.compose.include.filter.value
    // and the discussion on https://github.com/FHIR/sushi/issues/936
    if (operator == null) throw new ValueSetFilterOperatorError(ctx.vsFilterOperator().getText());
    switch (operator) {
      case EQUALS, IN, NOT_IN, IS_A, DESCENDENT_OF, IS_NOT_A, GENERALIZES -> {
        if (!(value instanceof FshCode) && !(value instanceof String)) {
          throw new ValueSetFilterValueTypeError(operator, List.of("code", "string"));
        }
      }
      case REGEX -> {
        if (!(value instanceof Pattern) && !(value instanceof String)) {
          throw new ValueSetFilterValueTypeError(operator, List.of("regex", "string"));
        }
      }
      case EXISTS -> {
        List<String> allowedStrings = List.of("true", "false");
        if (!(value instanceof Boolean) && !(value instanceof String s && allowedStrings.contains(s))) {
          throw new ValueSetFilterValueTypeError(operator, List.of("boolean"), allowedStrings);
        }
      }
      default -> throw new ValueSetFilterOperatorError(ctx.vsFilterOperator().getText());
    }
    return new ValueSetFilter(property, operator, value);
  }

  private Object visitVsFilterValue(VsFilterValueContext ctx) {
    if (ctx.code() != null) return visitCode(ctx.code());
    if (ctx.REGEX() != null) return ImporterText.jsRegExp(jsSliceRange(ctx.REGEX().getText(), 1,
        ctx.REGEX().getText().length() - 1));
    if (ctx.STRING() != null) return extractString(ctx.STRING());
    if (ctx.KW_TRUE() != null) return Boolean.TRUE;
    if (ctx.KW_FALSE() != null) return Boolean.FALSE;
    return null;
  }

  private void validateAliasResolves(ParseTree parentCtx, String value) {
    boolean hasAlias = allAliases.containsKey(value);
    if (!hasAlias && value.startsWith("$")) {
      logger.error("Value " + value + " does not resolve as alias, values beginning with \"$\" must resolve",
          src(extractStartStop(parentCtx)));
    }
  }

  private String aliasAwareValue(ParseTree parentCtx) {
    if (parentCtx == null) throw readNull("getText");
    return aliasAwareValue(parentCtx, parentCtx.getText());
  }

  private String aliasAwareValue(ParseTree parentCtx, String value) {
    // const [valueWithoutVersion, version] = value.split('|');
    String[] parts = value.split("\\|", -1);
    String valueWithoutVersion = parts[0];
    String version = parts.length > 1 ? parts[1] : null;
    validateAliasResolves(parentCtx, valueWithoutVersion);
    if (allAliases.containsKey(valueWithoutVersion)) {
      return Common.jsString(allAliases.get(valueWithoutVersion)) + (Common.truthy(version) ? "|" + version : "");
    }
    return value;
  }

  /**
   * Given a path and the context containing it, apply the path context indicated by the path's indent.
   *
   * @return the path with context prepended
   */
  private List<String> prependPathContext(List<String> path, ParserRuleContext parentCtx, boolean isPathRule,
      boolean isInstanceRule, boolean suppressError) {
    try {
      TextLocation location = extractStartStop(parentCtx, suppressError);
      int currentIndent = location.startColumn - DEFAULT_START_COLUMN;
      double contextIndexD = currentIndent / (double) INDENT_WIDTH;

      if (!isValidContext(location, currentIndent, pathContext.size())) {
        return path;
      }
      int contextIndex = (int) contextIndexD;

      // If the element is not indented, just reset the context
      if (contextIndex == 0) {
        // If the last context still has [+], that means the path was never used
        // and the [+] will be discarded without incrementing
        if (!pathContext.isEmpty()
            && pathContext.get(pathContext.size() - 1).stream().anyMatch(p -> SOFT_INDEX_PLUS.matcher(p).find())) {
          logger.warn("The previous line(s) use path rules to establish a context using soft indexing "
              + "(e.g., [+]), but that context is reset by the following rule before it is ever "
              + "used. As a result, the previous path context will be ignored and its "
              + "soft-indices will not be incremented", src(location));
        }
        pathContext = new ArrayList<>();
        pathContext.add(path);
        return path;
      }

      if (path.size() == 1 && path.get(0).equals(".")) {
        logger.error("The special '.' path is only allowed in top-level rules. "
            + "The rule will be processed as if it is not indented.", src(location));
        return path;
      }

      // Otherwise, get the context based on the indent level.
      List<String> currentContext = pathContext.get(contextIndex - 1);
      if (currentContext.isEmpty() && !suppressError) {
        logger.error("Rule cannot be indented below rule which has no path. "
            + "The rule will be processed as if it is not indented.", src(location));
        return path;
      }

      // Trim out-of-scope contexts
      while (pathContext.size() > contextIndex) pathContext.remove(pathContext.size() - 1);

      // Get the new path and add as the last context
      List<String> fullPath = new ArrayList<>(currentContext);
      fullPath.addAll(path);
      pathContext.add(fullPath);
      return fullPath;
    } finally {
      // Once we have used the existing context in a non-path rule or any rule (including path rules) on an Instance,
      // replace [+] with [=] in all existing contexts so that the [+] isn't re-applied in further contexts
      if (!isPathRule || isInstanceRule) {
        for (int i = 0; i < pathContext.size(); i++) {
          List<String> replaced = new ArrayList<>();
          for (String c : pathContext.get(i)) replaced.add(c.replace("[+]", "[=]"));
          pathContext.set(i, replaced);
        }
      }
    }
  }

  private boolean isValidContext(TextLocation location, int currentIndent, int existingContextLength) {
    if (currentIndent > 0 && existingContextLength == 0) {
      logger.error("The first rule of a definition must be left-aligned. "
          + "The rule will be processed as if it is not indented.", src(location));
      return false;
    }
    if (currentIndent % INDENT_WIDTH != 0 || currentIndent < 0) {
      logger.error("Unable to determine path context for rule indented " + currentIndent
          + " space(s). Rules must be indented in multiples of " + INDENT_WIDTH + " space(s).", src(location));
      return false;
    }
    // And we require that rules are not indented too deeply
    int contextIndex = currentIndent / INDENT_WIDTH;
    if (contextIndex > existingContextLength) {
      logger.error("Cannot determine path context of rule since it is indented too deeply. "
          + "Rules must be indented in increments of " + INDENT_WIDTH + " space(s).", src(location));
      return false;
    }
    return true;
  }

  private static String extractString(TerminalNode stringCtx) {
    String str = stringCtx != null ? stringCtx.getText() : "\"\""; // default to empty string if stringCtx is null
    return unescapeQuotedString(str);
  }

  /** getText() of a node TS reads without a null check: null throws TS's TypeError. */
  private static String getText(ParseTree node) {
    if (node == null) throw readNull("getText");
    return node.getText();
  }

  // ------------------------------------------------------------------------------------------ source locations

  /** Rule contexts whose rule starts with STAR (TS pc.isStarContext: the context class has a STAR accessor). */
  private static TerminalNode starOf(ParseTree ctx) {
    if (ctx instanceof CardRuleContext c) return c.STAR();
    if (ctx instanceof FlagRuleContext c) return c.STAR();
    if (ctx instanceof ValueSetRuleContext c) return c.STAR();
    if (ctx instanceof FixedValueRuleContext c) return c.STAR();
    if (ctx instanceof ContainsRuleContext c) return c.STAR();
    if (ctx instanceof OnlyRuleContext c) return c.STAR();
    if (ctx instanceof ObeysRuleContext c) return c.STAR();
    if (ctx instanceof CaretValueRuleContext c) return c.STAR();
    if (ctx instanceof CodeCaretValueRuleContext c) return c.STAR();
    if (ctx instanceof MappingRuleContext c) return c.STAR();
    if (ctx instanceof InsertRuleContext c) return c.STAR();
    if (ctx instanceof CodeInsertRuleContext c) return c.STAR();
    if (ctx instanceof AddCRElementRuleContext c) return c.STAR();
    if (ctx instanceof AddElementRuleContext c) return c.STAR();
    if (ctx instanceof PathRuleContext c) return c.STAR();
    if (ctx instanceof VsComponentContext c) return c.STAR();
    if (ctx instanceof ConceptContext c) return c.STAR();
    if (ctx instanceof ParamRuleSetContentContext c) return c.STAR();
    return null;
  }

  private static boolean isStarContext(ParseTree ctx) {
    return ctx instanceof CardRuleContext || ctx instanceof FlagRuleContext || ctx instanceof ValueSetRuleContext
        || ctx instanceof FixedValueRuleContext || ctx instanceof ContainsRuleContext || ctx instanceof OnlyRuleContext
        || ctx instanceof ObeysRuleContext || ctx instanceof CaretValueRuleContext
        || ctx instanceof CodeCaretValueRuleContext || ctx instanceof MappingRuleContext
        || ctx instanceof InsertRuleContext || ctx instanceof CodeInsertRuleContext
        || ctx instanceof AddCRElementRuleContext || ctx instanceof AddElementRuleContext
        || ctx instanceof PathRuleContext || ctx instanceof VsComponentContext || ctx instanceof ConceptContext
        || ctx instanceof ParamRuleSetContentContext;
  }

  /** TS pc.containsPathContext: the context class has a path accessor. */
  private static boolean containsPathContext(ParseTree ctx) {
    return ctx instanceof CardRuleContext || ctx instanceof FlagRuleContext || ctx instanceof ValueSetRuleContext
        || ctx instanceof FixedValueRuleContext || ctx instanceof ContainsRuleContext || ctx instanceof OnlyRuleContext
        || ctx instanceof ObeysRuleContext || ctx instanceof CaretValueRuleContext || ctx instanceof MappingRuleContext
        || ctx instanceof InsertRuleContext || ctx instanceof AddCRElementRuleContext
        || ctx instanceof AddElementRuleContext || ctx instanceof PathRuleContext;
  }

  /**
   * TS pc.containsCodePathContext: a non-empty CODE() list (concept, codeCaretValueRule, codeInsertRule), or a
   * non-null caretPath(), RULESET_REFERENCE() or PARAM_RULESET_REFERENCE() accessor result.
   */
  private static boolean containsCodePathContext(ParseTree ctx) {
    if (ctx instanceof ConceptContext c) return !c.CODE().isEmpty();
    if (ctx instanceof CodeCaretValueRuleContext c) return !c.CODE().isEmpty() || c.caretPath() != null;
    if (ctx instanceof CodeInsertRuleContext c) return !c.CODE().isEmpty() || c.RULESET_REFERENCE() != null;
    if (ctx instanceof CaretValueRuleContext c) return c.caretPath() != null;
    if (ctx instanceof InsertRuleContext c) return c.RULESET_REFERENCE() != null;
    return false;
  }

  private TextLocation extractStartStop(ParseTree ctx) {
    return extractStartStop(ctx, false);
  }

  private TextLocation extractStartStop(ParseTree ctx, boolean suppressError) {
    if (ctx == null) throw readNull("STAR");
    if (isStarContext(ctx)) {
      ParserRuleContext rc = (ParserRuleContext) ctx;
      TerminalNode star = starOf(ctx);
      if (star == null) throw readNull("symbol");
      if (rc.stop == null) throw readNull("line");
      TextLocation location = new TextLocation(star.getSymbol().getLine() + 1, getStarContextStartColumn(star),
          rc.stop.getLine(), rc.stop.getStopIndex() - rc.stop.getStartIndex() + rc.stop.getCharPositionInLine() + 1);
      if (!suppressError && !(containsPathContext(ctx) || containsCodePathContext(ctx))
          && location.startColumn - DEFAULT_START_COLUMN > 0) {
        logger.error("A rule that does not use a path cannot be indented to indicate context. "
            + "The rule will be processed as if it is not indented.", src(location));
      }
      return location;
    } else if (ctx instanceof TerminalNode t) {
      Token s = t.getSymbol();
      return new TextLocation(s.getLine(), s.getCharPositionInLine() + 1, s.getLine(),
          s.getStopIndex() - s.getStartIndex() + s.getCharPositionInLine() + 1);
    } else {
      ParserRuleContext rc = (ParserRuleContext) ctx;
      if (rc.start == null) throw readNull("line");
      if (rc.stop == null) throw readNull("line");
      return new TextLocation(rc.start.getLine(), rc.start.getCharPositionInLine() + 1, rc.stop.getLine(),
          rc.stop.getStopIndex() - rc.stop.getStartIndex() + rc.stop.getCharPositionInLine() + 1);
    }
  }

  private static int getStarContextStartColumn(TerminalNode star) {
    String t = star.getText();
    return t.length() - t.lastIndexOf('\n') - 2;
  }

  private static DocContext parseDoc(String input, String file) {
    FSHErrorListener listener = new FSHErrorListener(file);
    return FSHParser.parseDoc(input, listener);
  }
}

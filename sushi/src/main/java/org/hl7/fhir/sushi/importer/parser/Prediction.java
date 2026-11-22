package org.hl7.fhir.sushi.importer.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// port of the antlr4 JS runtime 4.13.2 ParserATNSimulator, PredictionMode, ATNConfig(Set), PredictionContext(Utils)
// (as used by SUSHI 3.21.1), without the parts this grammar cannot reach (semantic predicates, precedence,
// left recursion) and without the DFA cache (which only memoises results).
/**
 * ANTLR's adaptive LL(*) prediction (ALL(*)) for the parser's non-LL(1) decisions: simulate all alternatives in
 * parallel on the ATN, first with context-free (SLL) stacks, falling back to the full call stack (LL) when SLL sees
 * a conflict. Besides the predicted alternative, what is observable is where it stops reading (when a token can't
 * continue any alternative: a "no viable alternative" error from the decision's first token to that token, unless an
 * alternative could already finish the decision's rule, which is then chosen and the error left to the caller).
 */
final class Prediction {
  static final int INVALID_ALT = 0;
  static final int EMPTY_RETURN_STATE = Integer.MAX_VALUE;

  // ---------------------------------------------------------------- prediction contexts (graph-structured stacks)

  abstract static class Ctx {
    final int hash;

    Ctx(int hash) { this.hash = hash; }

    abstract int size();

    abstract Ctx parent(int i);

    abstract int returnState(int i);

    boolean isEmpty() { return this == EMPTY; }

    boolean hasEmptyPath() { return returnState(size() - 1) == EMPTY_RETURN_STATE; }

    @Override public int hashCode() { return hash; }
  }

  static class Singleton extends Ctx {
    final Ctx parent;
    final int returnState;

    Singleton(Ctx parent, int returnState) {
      super(parent == null ? 1 : 31 * parent.hash + returnState);
      this.parent = parent;
      this.returnState = returnState;
    }

    static Ctx create(Ctx parent, int returnState) {
      if (returnState == EMPTY_RETURN_STATE && parent == null) return EMPTY;
      return new Singleton(parent, returnState);
    }

    @Override int size() { return 1; }

    @Override Ctx parent(int i) { return parent; }

    @Override int returnState(int i) { return returnState; }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Singleton s) || o instanceof Empty || this instanceof Empty) return false;
      if (hash != s.hash || returnState != s.returnState) return false;
      return parent == null ? s.parent == null : parent.equals(s.parent);
    }
  }

  static final class Empty extends Singleton {
    Empty() { super(null, EMPTY_RETURN_STATE); }

    @Override boolean isEmpty() { return true; }

    @Override Ctx parent(int i) { return null; }

    @Override public boolean equals(Object o) { return this == o; }
  }

  static final Ctx EMPTY = new Empty();

  static final class Array extends Ctx {
    final Ctx[] parents;
    final int[] returnStates;

    Array(Ctx[] parents, int[] returnStates) {
      super(31 * Arrays.hashCode(parents) + Arrays.hashCode(returnStates));
      this.parents = parents;
      this.returnStates = returnStates;
    }

    @Override boolean isEmpty() { return returnStates[0] == EMPTY_RETURN_STATE; }

    @Override int size() { return returnStates.length; }

    @Override Ctx parent(int i) { return parents[i]; }

    @Override int returnState(int i) { return returnStates[i]; }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Array a) || hash != a.hash) return false;
      return Arrays.equals(returnStates, a.returnStates) && Arrays.equals(parents, a.parents);
    }
  }

  static Ctx fromRuleContext(Atn atn, ParserRuleContext outer) {
    if (outer == null || outer.parent == null) return EMPTY;
    Ctx parent = fromRuleContext(atn, outer.parent);
    Atn.State state = atn.states.get(outer.invokingState);
    return Singleton.create(parent, state.transition(0).followState.number);
  }

  static Ctx merge(Ctx a, Ctx b, boolean rootIsWildcard) {
    if (a == b) return a;
    if (a instanceof Singleton sa && b instanceof Singleton sb) return mergeSingletons(sa, sb, rootIsWildcard);
    if (rootIsWildcard) {
      if (a instanceof Empty) return a;
      if (b instanceof Empty) return b;
    }
    Array aa = a instanceof Singleton s ? new Array(new Ctx[] {s.parent(0)}, new int[] {s.returnState}) : (Array) a;
    Array bb = b instanceof Singleton s ? new Array(new Ctx[] {s.parent(0)}, new int[] {s.returnState}) : (Array) b;
    return mergeArrays(aa, bb, rootIsWildcard);
  }

  private static Ctx mergeSingletons(Singleton a, Singleton b, boolean rootIsWildcard) {
    Ctx rootMerge = mergeRoot(a, b, rootIsWildcard);
    if (rootMerge != null) return rootMerge;
    if (a.returnState == b.returnState) {
      Ctx parent = merge(a.parent, b.parent, rootIsWildcard);
      if (parent == a.parent) return a;
      if (parent == b.parent) return b;
      return Singleton.create(parent, a.returnState);
    } else {
      Ctx singleParent = null;
      if (a == b || (a.parent != null && a.parent == b.parent)) singleParent = a.parent;
      if (singleParent != null) {
        int[] payloads = {a.returnState, b.returnState};
        if (a.returnState > b.returnState) {
          payloads[0] = b.returnState;
          payloads[1] = a.returnState;
        }
        return new Array(new Ctx[] {singleParent, singleParent}, payloads);
      }
      int[] payloads = {a.returnState, b.returnState};
      Ctx[] parents = {a.parent, b.parent};
      if (a.returnState > b.returnState) {
        payloads[0] = b.returnState;
        payloads[1] = a.returnState;
        parents = new Ctx[] {b.parent, a.parent};
      }
      return new Array(parents, payloads);
    }
  }

  private static Ctx mergeRoot(Singleton a, Singleton b, boolean rootIsWildcard) {
    if (rootIsWildcard) {
      if (a == EMPTY) return EMPTY;
      if (b == EMPTY) return EMPTY;
    } else {
      if (a == EMPTY && b == EMPTY) return EMPTY;
      if (a == EMPTY) return new Array(new Ctx[] {b.parent, null}, new int[] {b.returnState, EMPTY_RETURN_STATE});
      if (b == EMPTY) return new Array(new Ctx[] {a.parent, null}, new int[] {a.returnState, EMPTY_RETURN_STATE});
    }
    return null;
  }

  private static Ctx mergeArrays(Array a, Array b, boolean rootIsWildcard) {
    int i = 0, j = 0, k = 0;
    int[] mergedReturnStates = new int[a.returnStates.length + b.returnStates.length];
    Ctx[] mergedParents = new Ctx[a.returnStates.length + b.returnStates.length];
    while (i < a.returnStates.length && j < b.returnStates.length) {
      Ctx aParent = a.parents[i];
      Ctx bParent = b.parents[j];
      if (a.returnStates[i] == b.returnStates[j]) {
        int payload = a.returnStates[i];
        boolean bothDollars = payload == EMPTY_RETURN_STATE && aParent == null && bParent == null;
        boolean axAx = aParent != null && bParent != null && aParent == bParent;
        if (bothDollars || axAx) {
          mergedParents[k] = aParent;
          mergedReturnStates[k] = payload;
        } else {
          mergedParents[k] = merge(aParent, bParent, rootIsWildcard);
          mergedReturnStates[k] = payload;
        }
        i++;
        j++;
      } else if (a.returnStates[i] < b.returnStates[j]) {
        mergedParents[k] = aParent;
        mergedReturnStates[k] = a.returnStates[i];
        i++;
      } else {
        mergedParents[k] = bParent;
        mergedReturnStates[k] = b.returnStates[j];
        j++;
      }
      k++;
    }
    if (i < a.returnStates.length) {
      for (int p = i; p < a.returnStates.length; p++) {
        mergedParents[k] = a.parents[p];
        mergedReturnStates[k] = a.returnStates[p];
        k++;
      }
    } else {
      for (int p = j; p < b.returnStates.length; p++) {
        mergedParents[k] = b.parents[p];
        mergedReturnStates[k] = b.returnStates[p];
        k++;
      }
    }
    if (k < mergedParents.length) {
      if (k == 1) return Singleton.create(mergedParents[0], mergedReturnStates[0]);
      mergedParents = Arrays.copyOf(mergedParents, k);
      mergedReturnStates = Arrays.copyOf(mergedReturnStates, k);
    }
    Array m = new Array(mergedParents, mergedReturnStates);
    if (m.equals(a)) return a;
    if (m.equals(b)) return b;
    return m;
  }

  // ---------------------------------------------------------------- configurations

  static final class Config {
    final Atn.State state;
    final int alt;
    Ctx context;
    int reachesIntoOuterContext;

    Config(Atn.State state, int alt, Ctx context) {
      this.state = state;
      this.alt = alt;
      this.context = context;
    }

    /** new ATNConfig({state}, c): same alt/context/outer depth. */
    Config(Atn.State state, Config c) {
      this(state, c.alt, c.context);
      this.reachesIntoOuterContext = c.reachesIntoOuterContext;
    }

    /** new ATNConfig({state, context}, c) */
    Config(Atn.State state, Ctx context, Config c) {
      this(state, c.alt, context);
      this.reachesIntoOuterContext = c.reachesIntoOuterContext;
    }

    @Override public boolean equals(Object o) {
      return o instanceof Config c && c.state.number == state.number && c.alt == alt
          && Objects.equals(context, c.context);
    }

    @Override public int hashCode() { return (state.number * 31 + alt) * 31 + Objects.hashCode(context); }
  }

  static final class ConfigSet {
    final List<Config> configs = new ArrayList<>();
    private final Map<Long, Config> lookup = new HashMap<>();
    final boolean fullCtx;
    int uniqueAlt;
    BitSet conflictingAlts;
    boolean dipsIntoOuterContext;

    ConfigSet(boolean fullCtx) { this.fullCtx = fullCtx; }

    void add(Config config) {
      if (config.reachesIntoOuterContext > 0) dipsIntoOuterContext = true;
      long key = ((long) config.state.number << 32) | config.alt;
      Config existing = lookup.putIfAbsent(key, config);
      if (existing == null) {
        configs.add(config);
        return;
      }
      Ctx merged = merge(existing.context, config.context, !fullCtx);
      existing.reachesIntoOuterContext = Math.max(existing.reachesIntoOuterContext, config.reachesIntoOuterContext);
      existing.context = merged;
    }

    int size() { return configs.size(); }

    boolean isEmpty() { return configs.isEmpty(); }

    @Override public boolean equals(Object o) {
      return o instanceof ConfigSet c && c.fullCtx == fullCtx && c.configs.equals(configs);
    }

    @Override public int hashCode() { return configs.hashCode(); }
  }

  // ---------------------------------------------------------------- PredictionMode

  private record StateCtx(int state, Ctx ctx) {}

  static List<BitSet> getConflictingAltSubsets(ConfigSet configs) {
    Map<StateCtx, BitSet> m = new LinkedHashMap<>();
    for (Config c : configs.configs) m.computeIfAbsent(new StateCtx(c.state.number, c.context), k -> new BitSet()).set(c.alt);
    return new ArrayList<>(m.values());
  }

  static boolean hasSLLConflictTerminatingPrediction(ConfigSet configs) {
    if (allConfigsInRuleStopStates(configs)) return true;
    List<BitSet> altsets = getConflictingAltSubsets(configs);
    return hasConflictingAltSet(altsets) && !hasStateAssociatedWithOneAlt(configs);
  }

  static boolean hasConfigInRuleStopState(ConfigSet configs) {
    for (Config c : configs.configs) if (c.state.type == Atn.StateType.RULE_STOP) return true;
    return false;
  }

  static boolean allConfigsInRuleStopStates(ConfigSet configs) {
    for (Config c : configs.configs) if (c.state.type != Atn.StateType.RULE_STOP) return false;
    return true;
  }

  static boolean hasConflictingAltSet(List<BitSet> altsets) {
    for (BitSet alts : altsets) if (alts.cardinality() > 1) return true;
    return false;
  }

  static boolean hasStateAssociatedWithOneAlt(ConfigSet configs) {
    Map<Integer, BitSet> m = new LinkedHashMap<>();
    for (Config c : configs.configs) m.computeIfAbsent(c.state.number, k -> new BitSet()).set(c.alt);
    for (BitSet alts : m.values()) if (alts.cardinality() == 1) return true;
    return false;
  }

  static BitSet getAlts(List<BitSet> altsets) {
    BitSet all = new BitSet();
    for (BitSet alts : altsets) all.or(alts);
    return all;
  }

  static int getSingleViableAlt(List<BitSet> altsets) {
    int result = -1;
    for (BitSet alts : altsets) {
      int minAlt = alts.nextSetBit(0);
      if (result == -1) result = minAlt;
      else if (result != minAlt) return INVALID_ALT;
    }
    return result == -1 ? INVALID_ALT : result;
  }

  // ---------------------------------------------------------------- the simulator

  /** Thrown when no alternative is viable; the parser reports it ("no viable alternative at input ..."). */
  static final class NoViableAlt extends RuntimeException {
    final Token startToken;
    final Token offendingToken;

    NoViableAlt(Token startToken, Token offendingToken) {
      super(null, null, false, false);
      this.startToken = startToken;
      this.offendingToken = offendingToken;
    }
  }

  private final Atn atn;
  /** The SLL DFA per decision (ANTLR's DFA cache): memoises reach sets; results are those of the plain simulation. */
  private final Map<Atn.State, Dfa> dfas = new java.util.concurrent.ConcurrentHashMap<>();

  Prediction(Atn atn) {
    this.atn = atn;
  }

  /** A DFA state: an SLL configuration set, what it predicts, and its outgoing edges per token type. */
  private static final class DState {
    final ConfigSet configs;
    /** INVALID_ALT: keep reading; otherwise the unique alternative */
    final int prediction;
    final boolean requiresFullContext;
    final Map<Integer, DState> edges = new java.util.concurrent.ConcurrentHashMap<>();

    DState(ConfigSet configs, int prediction, boolean requiresFullContext) {
      this.configs = configs;
      this.prediction = prediction;
      this.requiresFullContext = requiresFullContext;
    }
  }

  private static final DState ERROR = new DState(null, INVALID_ALT, false);

  private static final class Dfa {
    final DState s0;
    final Map<ConfigSet, DState> states = new java.util.concurrent.ConcurrentHashMap<>();

    Dfa(DState s0) { this.s0 = s0; }
  }

  /** adaptivePredict(input, decision, outerContext): the predicted alternative (1-based). */
  int adaptivePredict(CommonTokenStream input, Atn.State decisionState, ParserRuleContext outerContext) {
    int index = input.index();
    try {
      Dfa dfa = dfas.computeIfAbsent(decisionState,
          d -> new Dfa(new DState(computeStartState(d, null, false), INVALID_ALT, false)));
      return execAtn(dfa, decisionState, input, index, outerContext);
    } finally {
      input.seek(index);
    }
  }

  private int execAtn(Dfa dfa, Atn.State decisionState, CommonTokenStream input, int startIndex,
      ParserRuleContext outerContext) {
    DState previous = dfa.s0;
    int t = input.LA(1);
    while (true) {
      DState d = previous.edges.get(t);
      if (d == null) {
        d = computeTargetState(dfa, previous, t);
        previous.edges.put(t, d);
      }
      if (d == ERROR) {
        NoViableAlt e = new NoViableAlt(input.get(startIndex), input.LT(1));
        input.seek(startIndex);
        int alt = getAltThatFinishedDecisionEntryRule(previous.configs);
        if (alt != INVALID_ALT) return alt;
        throw e;
      }
      if (d.prediction != INVALID_ALT) return d.prediction;
      if (d.requiresFullContext) {
        ConfigSet fullS0 = computeStartState(decisionState, outerContext, true);
        return execAtnWithFullContext(fullS0, input, startIndex, outerContext);
      }
      previous = d;
      if (t != Token.EOF) {
        input.consume();
        t = input.LA(1);
      }
    }
  }

  private DState computeTargetState(Dfa dfa, DState previous, int t) {
    ConfigSet reach = computeReachSet(previous.configs, t, false);
    if (reach == null) return ERROR;
    DState existing = dfa.states.get(reach);
    if (existing != null) return existing;
    int predictedAlt = getUniqueAlt(reach);
    DState d = predictedAlt != INVALID_ALT ? new DState(reach, predictedAlt, false)
        : new DState(reach, INVALID_ALT, hasSLLConflictTerminatingPrediction(reach));
    DState prior = dfa.states.putIfAbsent(reach, d);
    return prior != null ? prior : d;
  }

  private int execAtnWithFullContext(ConfigSet s0, CommonTokenStream input, int startIndex,
      ParserRuleContext outerContext) {
    ConfigSet reach;
    ConfigSet previous = s0;
    input.seek(startIndex);
    int t = input.LA(1);
    int predictedAlt;
    while (true) {
      reach = computeReachSet(previous, t, true);
      if (reach == null) {
        NoViableAlt e = new NoViableAlt(input.get(startIndex), input.LT(1));
        input.seek(startIndex);
        int alt = getAltThatFinishedDecisionEntryRule(previous);
        if (alt != INVALID_ALT) return alt;
        throw e;
      }
      List<BitSet> altSubSets = getConflictingAltSubsets(reach);
      reach.uniqueAlt = getUniqueAlt(reach);
      if (reach.uniqueAlt != INVALID_ALT) {
        predictedAlt = reach.uniqueAlt;
        break;
      }
      predictedAlt = getSingleViableAlt(altSubSets);
      if (predictedAlt != INVALID_ALT) break;
      previous = reach;
      if (t != Token.EOF) {
        input.consume();
        t = input.LA(1);
      }
    }
    return predictedAlt;
  }

  private ConfigSet computeReachSet(ConfigSet closure, int t, boolean fullCtx) {
    ConfigSet intermediate = new ConfigSet(fullCtx);
    List<Config> skippedStopStates = null;
    for (Config c : closure.configs) {
      if (c.state.type == Atn.StateType.RULE_STOP) {
        if (fullCtx || t == Token.EOF) {
          if (skippedStopStates == null) skippedStopStates = new ArrayList<>();
          skippedStopStates.add(c);
        }
        continue;
      }
      for (Atn.Transition trans : c.state.transitions) {
        if (trans.matches(t, atn.maxTokenType)) intermediate.add(new Config(trans.target, c));
      }
    }
    ConfigSet reach = null;
    if (skippedStopStates == null && t != Token.EOF) {
      if (intermediate.size() == 1) reach = intermediate;
      else if (getUniqueAlt(intermediate) != INVALID_ALT) reach = intermediate;
    }
    if (reach == null) {
      reach = new ConfigSet(fullCtx);
      java.util.Set<Config> closureBusy = new java.util.HashSet<>();
      boolean treatEofAsEpsilon = t == Token.EOF;
      for (Config c : intermediate.configs) closure(c, reach, closureBusy, fullCtx, treatEofAsEpsilon);
    }
    if (t == Token.EOF) reach = removeAllConfigsNotInRuleStopState(reach, reach == intermediate);
    if (skippedStopStates != null && (!fullCtx || !hasConfigInRuleStopState(reach))) {
      for (Config c : skippedStopStates) reach.add(c);
    }
    return reach.isEmpty() ? null : reach;
  }

  private ConfigSet removeAllConfigsNotInRuleStopState(ConfigSet configs, boolean lookToEndOfRule) {
    if (allConfigsInRuleStopStates(configs)) return configs;
    ConfigSet result = new ConfigSet(configs.fullCtx);
    for (Config config : configs.configs) {
      if (config.state.type == Atn.StateType.RULE_STOP) {
        result.add(config);
        continue;
      }
      if (lookToEndOfRule && config.state.epsilonOnly) {
        TokenSet nextTokens = atn.nextTokens(config.state);
        if (nextTokens.contains(Token.EPSILON)) {
          result.add(new Config(atn.ruleToStop[config.state.ruleIndex], config));
        }
      }
    }
    return result;
  }

  private ConfigSet computeStartState(Atn.State p, ParserRuleContext ctx, boolean fullCtx) {
    Ctx initialContext = fromRuleContext(atn, ctx);
    ConfigSet configs = new ConfigSet(fullCtx);
    for (int i = 0; i < p.size(); i++) {
      Config c = new Config(p.transition(i).target, i + 1, initialContext);
      closure(c, configs, new java.util.HashSet<>(), fullCtx, false);
    }
    return configs;
  }

  private static int getAltThatFinishedDecisionEntryRule(ConfigSet configs) {
    int min = Integer.MAX_VALUE;
    for (Config c : configs.configs) {
      if (c.reachesIntoOuterContext > 0
          || (c.state.type == Atn.StateType.RULE_STOP && c.context.hasEmptyPath())) {
        min = Math.min(min, c.alt);
      }
    }
    return min == Integer.MAX_VALUE ? INVALID_ALT : min;
  }

  private void closure(Config config, ConfigSet configs, java.util.Set<Config> closureBusy, boolean fullCtx,
      boolean treatEofAsEpsilon) {
    closureCheckingStopState(config, configs, closureBusy, fullCtx, 0, treatEofAsEpsilon);
  }

  private void closureCheckingStopState(Config config, ConfigSet configs, java.util.Set<Config> closureBusy,
      boolean fullCtx, int depth, boolean treatEofAsEpsilon) {
    if (config.state.type == Atn.StateType.RULE_STOP) {
      if (!config.context.isEmpty()) {
        for (int i = 0; i < config.context.size(); i++) {
          if (config.context.returnState(i) == EMPTY_RETURN_STATE) {
            if (fullCtx) {
              configs.add(new Config(config.state, EMPTY, config));
              continue;
            } else {
              closure_(config, configs, closureBusy, fullCtx, depth, treatEofAsEpsilon);
            }
            continue;
          }
          Atn.State returnState = atn.states.get(config.context.returnState(i));
          Ctx newContext = config.context.parent(i);
          Config c = new Config(returnState, config.alt, newContext);
          c.reachesIntoOuterContext = config.reachesIntoOuterContext;
          closureCheckingStopState(c, configs, closureBusy, fullCtx, depth - 1, treatEofAsEpsilon);
        }
        return;
      } else if (fullCtx) {
        configs.add(config);
        return;
      }
    }
    closure_(config, configs, closureBusy, fullCtx, depth, treatEofAsEpsilon);
  }

  private void closure_(Config config, ConfigSet configs, java.util.Set<Config> closureBusy, boolean fullCtx,
      int depth, boolean treatEofAsEpsilon) {
    Atn.State p = config.state;
    if (!p.epsilonOnly) configs.add(config);
    for (Atn.Transition t : p.transitions) {
      Config c = getEpsilonTarget(config, t, treatEofAsEpsilon);
      if (c != null) {
        int newDepth = depth;
        if (config.state.type == Atn.StateType.RULE_STOP) {
          c.reachesIntoOuterContext++;
          if (!closureBusy.add(c)) continue;
          configs.dipsIntoOuterContext = true;
          newDepth--;
        } else {
          if (!t.isEpsilon() && !closureBusy.add(c)) continue;
          if (t.type == Atn.TransitionType.RULE && newDepth >= 0) newDepth++;
        }
        closureCheckingStopState(c, configs, closureBusy, fullCtx, newDepth, treatEofAsEpsilon);
      }
    }
  }

  private Config getEpsilonTarget(Config config, Atn.Transition t, boolean treatEofAsEpsilon) {
    switch (t.type) {
      case RULE -> {
        Ctx newContext = Singleton.create(config.context, t.followState.number);
        return new Config(t.target, newContext, config);
      }
      case EPSILON -> {
        return new Config(t.target, config);
      }
      case ATOM, SET -> {
        // JS: t.matches(Token.EOF, 0, 1)
        if (treatEofAsEpsilon && (t.type == Atn.TransitionType.ATOM ? t.label == Token.EOF : t.set.contains(Token.EOF))) {
          return new Config(t.target, config);
        }
        return null;
      }
      default -> {
        return null;
      }
    }
  }

  private static int getUniqueAlt(ConfigSet configs) {
    int alt = INVALID_ALT;
    for (Config c : configs.configs) {
      if (alt == INVALID_ALT) alt = c.alt;
      else if (c.alt != alt) return INVALID_ALT;
    }
    return alt;
  }
}

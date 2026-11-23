package org.hl7.fhir.sushi.importer.parser;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// port of the ATN model, ParserATNFactory construction and LL1Analyzer of ANTLR 4.13 (tool + JS runtime 4.13.2)
/**
 * The augmented transition network of a parser grammar, built from {@link Grammar} the way the ANTLR tool builds it
 * (ParserATNFactory: element lists, blocks, optional/star/plus loops, tail-epsilon removal, rule follow links as the
 * runtime's ATN deserializer adds them). The parser needs ANTLR's exact network because ANTLR's error recovery and
 * adaptive prediction are defined on it: the expected-token sets in error messages, the resynchronisation sets, and
 * where prediction stops all depend on its states. {@code AtnIsomorphismTest} checks it state by state against the
 * ATN of the ANTLR-generated parser.
 */
final class Atn {
  enum StateType {
    BASIC, RULE_START, BLOCK_START, PLUS_BLOCK_START, STAR_BLOCK_START, TOKEN_START, RULE_STOP, BLOCK_END,
    STAR_LOOP_BACK, STAR_LOOP_ENTRY, PLUS_LOOP_BACK, LOOP_END
  }

  static final class State {
    int number;
    final StateType type;
    final int ruleIndex;
    final List<Transition> transitions = new ArrayList<>(2);
    boolean epsilonOnly;
    /** decision number, -1 if not a decision */
    int decision = -1;
    /** BLOCK_START*: its end; LOOP_END/STAR_LOOP_ENTRY: loopback; */
    State endState;
    State loopBackState;
    /** BLOCK_END: its start */
    State startState;
    /** RULE_START: its stop */
    State stopState;
    /** lazily computed nextTokens(s) (within rule, EPSILON if the rule end is reachable) */
    TokenSet next;

    State(int number, StateType type, int ruleIndex) {
      this.number = number;
      this.type = type;
      this.ruleIndex = ruleIndex;
    }

    void add(Transition t) {
      if (transitions.isEmpty()) epsilonOnly = t.isEpsilon();
      else if (epsilonOnly != t.isEpsilon()) epsilonOnly = false;
      transitions.add(t);
    }

    Transition transition(int i) { return transitions.get(i); }

    int size() { return transitions.size(); }

    boolean isDecision() { return decision >= 0; }

    @Override public String toString() { return number + ":" + type; }
  }

  enum TransitionType { EPSILON, ATOM, SET, NOT_SET, RULE }

  static final class Transition {
    final TransitionType type;
    State target;
    /** ATOM: the token type */
    final int label;
    /** SET/NOT_SET: the token types */
    final TokenSet set;
    /** RULE: follow state and invoked rule */
    State followState;
    final int ruleIndex;

    Transition(TransitionType type, State target, int label, TokenSet set, State followState, int ruleIndex) {
      this.type = type;
      this.target = target;
      this.label = label;
      this.set = set;
      this.followState = followState;
      this.ruleIndex = ruleIndex;
    }

    boolean isEpsilon() { return type == TransitionType.EPSILON || type == TransitionType.RULE; }

    boolean matches(int symbol, int maxTokenType) {
      return switch (type) {
        case ATOM -> symbol == label;
        case SET -> set.contains(symbol);
        case NOT_SET -> symbol >= 1 && symbol <= maxTokenType && !set.contains(symbol);
        default -> false;
      };
    }

    /**
     * The token types LL1Analyzer adds for this transition: NOT_SET is complemented over 1..maxTokenType, correctly
     * by the tool (which decides LL(1) codegen), with the JS runtime's IntervalSet at parse time
     * ({@link JsIntervalSet}).
     */
    TokenSet label(int maxTokenType, boolean runtime) {
      return switch (type) {
        case ATOM -> TokenSet.of(label);
        case SET -> set;
        case NOT_SET -> runtime ? JsIntervalSet.ofTypes(set, maxTokenType).complement(1, maxTokenType).members()
            : set.complement(maxTokenType);
        default -> null;
      };
    }
  }

  final List<State> states = new ArrayList<>();
  final List<State> decisionToState = new ArrayList<>();
  final State[] ruleToStart;
  final State[] ruleToStop;
  final int maxTokenType;
  final Grammar grammar;

  Atn(Grammar g) {
    this.grammar = g;
    this.maxTokenType = g.maxTokenType;
    int n = g.rules.size();
    ruleToStart = new State[n];
    ruleToStop = new State[n];
    for (int r = 0; r < n; r++) {
      ruleToStart[r] = newState(StateType.RULE_START, r);
      ruleToStop[r] = newState(StateType.RULE_STOP, r);
      ruleToStart[r].stopState = ruleToStop[r];
    }
    for (int r = 0; r < n; r++) {
      currentRule = r;
      Grammar.Rule rule = g.rules.get(r);
      Handle h = build(rule.body());
      epsilon(ruleToStart[r], h.left);
      epsilon(h.right, ruleToStop[r]);
    }
    // the tool's addEOFTransitionToStartRules target (its EOF edges from rule stop states are not serialized)
    newState(StateType.BASIC);
    // ATNOptimizer.optimizeStates: drop removed states and renumber in creation order
    for (State s : building) {
      if (removed.contains(s)) continue;
      s.number = states.size();
      states.add(s);
    }
    building = null;
    // the runtime's deserializer adds the follow links (stop state -> follow state of every invocation) in state order
    for (State s : states) {
      for (Transition t : s.transitions) {
        if (t.type == TransitionType.RULE) ruleToStop[t.target.ruleIndex].add(epsTrans(t.followState));
      }
    }
    // the deserializer links block ends back to their starts
    for (State s : states) {
      if (s.endState != null) s.endState.startState = s;
    }
  }

  private int currentRule;
  private List<State> building = new ArrayList<>();
  private final Set<State> removed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

  private State newState(StateType type, int rule) {
    State s = new State(building.size(), type, rule);
    building.add(s);
    return s;
  }

  private void removeState(State s) {
    removed.add(s);
  }

  private State newState(StateType type) {
    return newState(type, currentRule);
  }

  private static Transition epsTrans(State target) {
    return new Transition(TransitionType.EPSILON, target, 0, null, null, -1);
  }

  private static void epsilon(State a, State b) {
    a.add(epsTrans(b));
  }

  private void defineDecision(State s) {
    s.decision = decisionToState.size();
    decisionToState.add(s);
  }

  record Handle(State left, State right) {}

  /** Builds the sub-network of an element and records its states on the element (for the parser). */
  private Handle build(Grammar.Elem e) {
    if (e instanceof Grammar.Tok t) {
      State l = newState(StateType.BASIC), r = newState(StateType.BASIC);
      l.add(new Transition(TransitionType.ATOM, r, t.type, null, null, -1));
      t.state = l;
      return new Handle(l, r);
    }
    if (e instanceof Grammar.TokSet t) {
      State l = newState(StateType.BASIC), r = newState(StateType.BASIC);
      l.add(new Transition(t.invert ? TransitionType.NOT_SET : TransitionType.SET, r, 0, t.set, null, -1));
      t.state = l;
      return new Handle(l, r);
    }
    if (e instanceof Grammar.Ref ref) {
      State l = newState(StateType.BASIC), r = newState(StateType.BASIC);
      l.add(new Transition(TransitionType.RULE, ruleToStart[ref.ruleIndex], 0, null, r, ref.ruleIndex));
      ref.state = l;
      return new Handle(l, r);
    }
    if (e instanceof Grammar.Seq q) {
      List<Handle> els = new ArrayList<>();
      for (Grammar.Elem x : q.items) els.add(build(x));
      return elemList(els);
    }
    if (e instanceof Grammar.Block b) {
      List<Handle> alts = new ArrayList<>();
      for (Grammar.Elem x : b.alts) alts.add(build(x));
      switch (b.kind) {
        case PLAIN -> {
          if (alts.size() == 1) return alts.get(0);
          State start = newState(StateType.BLOCK_START);
          defineDecision(start);
          b.decisionState = start;
          return makeBlock(start, alts);
        }
        case OPTIONAL -> {
          State start = newState(StateType.BLOCK_START);
          defineDecision(start);
          Handle h = makeBlock(start, alts);
          epsilon(start, h.right);
          b.decisionState = start;
          return h;
        }
        case STAR -> {
          State blkStart = newState(StateType.STAR_BLOCK_START);
          if (alts.size() > 1) defineDecision(blkStart);
          Handle h = makeBlock(blkStart, alts);
          State entry = newState(StateType.STAR_LOOP_ENTRY);
          defineDecision(entry);
          State end = newState(StateType.LOOP_END);
          State loop = newState(StateType.STAR_LOOP_BACK);
          entry.loopBackState = loop;
          end.loopBackState = loop;
          epsilon(entry, blkStart);
          epsilon(entry, end);
          epsilon(h.right, loop);
          epsilon(loop, entry);
          b.blockStart = blkStart;
          b.decisionState = entry;
          b.loopBack = loop;
          return new Handle(entry, end);
        }
        case PLUS -> {
          State blkStart = newState(StateType.PLUS_BLOCK_START);
          if (alts.size() > 1) defineDecision(blkStart);
          Handle h = makeBlock(blkStart, alts);
          State loop = newState(StateType.PLUS_LOOP_BACK);
          defineDecision(loop);
          State end = newState(StateType.LOOP_END);
          blkStart.loopBackState = loop;
          end.loopBackState = loop;
          epsilon(h.right, loop);
          epsilon(loop, blkStart);
          epsilon(loop, end);
          b.blockStart = blkStart;
          b.decisionState = loop;
          b.loopBack = loop;
          return new Handle(blkStart, end);
        }
        default -> throw new IllegalStateException();
      }
    }
    throw new IllegalStateException("unknown element " + e);
  }

  private Handle makeBlock(State start, List<Handle> alts) {
    State end = newState(StateType.BLOCK_END);
    start.endState = end;
    for (Handle alt : alts) {
      epsilon(start, alt.left);
      epsilon(alt.right, end);
      removeTailEpsilons(alt.left, new HashSet<>());
    }
    return new Handle(start, end);
  }

  /** ANTLR's TailEpsilonRemover (an ATNVisitor from the alt's first state): p-x->q-eps->blockEnd => p-x->blockEnd. */
  private void removeTailEpsilons(State p, Set<Integer> visited) {
    if (!visited.add(p.number)) return;
    if (p.type == StateType.BASIC && p.size() == 1) {
      Transition pt = p.transition(0);
      State q = pt.type == TransitionType.RULE ? pt.followState : pt.target;
      if (q.type == StateType.BASIC && q.size() == 1 && q.transition(0).isEpsilon()
          && q.transition(0).type != TransitionType.RULE) {
        State blockEnd = q.transition(0).target;
        if (blockEnd.type == StateType.BLOCK_END) {
          removeState(q);
          if (pt.type == TransitionType.RULE) pt.followState = blockEnd;
          else pt.target = blockEnd;
        }
      }
    }
    for (int i = 0; i < p.size(); i++) removeTailEpsilons(p.transition(i).target, visited);
  }

  private Handle elemList(List<Handle> els) {
    int n = els.size();
    for (int i = 0; i < n - 1; i++) {
      Handle el = els.get(i);
      Transition tr = el.left.size() == 1 ? el.left.transition(0) : null;
      boolean isRule = tr != null && tr.type == TransitionType.RULE;
      if (el.left.type == StateType.BASIC && el.right.type == StateType.BASIC && tr != null
          && (isRule && tr.followState == el.right || tr.target == el.right)) {
        if (isRule) tr.followState = els.get(i + 1).left;
        else tr.target = els.get(i + 1).left;
        removeState(el.right);
      } else {
        epsilon(el.right, els.get(i + 1).left);
      }
    }
    return new Handle(els.get(0).left, els.get(n - 1).right);
  }

  // ---------------------------------------------------------------- LL1Analyzer

  /** atn.nextTokens(s): the tokens that can follow s within its rule; EPSILON if the rule's end is reachable. */
  TokenSet nextTokens(State s) {
    if (s.next == null) {
      TokenSet r = new TokenSet();
      look(s, null, LookCtx.NONE, r, new HashSet<>(), new BitSet(), false, true);
      s.next = r;
    }
    return s.next;
  }

  /** atn.nextTokens(s, ctx) = LL1Analyzer.LOOK(s, null, ctx) with the parser's actual rule context (addEOF). */
  TokenSet nextTokens(State s, ParserRuleContext ctx) {
    TokenSet r = new TokenSet();
    look(s, null, LookCtx.fromRuleContext(this, ctx), r, new HashSet<>(), new BitSet(), true, true);
    return r;
  }

  /**
   * atn.getExpectedTokens(state, ctx): nextTokens of the state, continued into the invoking rules while the end of
   * the current rule is reachable; EOF if the end of the start rule is reachable.
   */
  TokenSet getExpectedTokens(State s, ParserRuleContext context) {
    TokenSet following = nextTokens(s);
    if (!following.contains(Token.EPSILON)) return following;
    TokenSet expected = new TokenSet();
    expected.addAll(following);
    expected.remove(Token.EPSILON);
    while (context != null && context.invokingState >= 0 && following.contains(Token.EPSILON)) {
      State invokingState = states.get(context.invokingState);
      following = nextTokens(invokingState.transition(0).followState);
      expected.addAll(following);
      expected.remove(Token.EPSILON);
      context = context.parent;
    }
    if (following.contains(Token.EPSILON)) expected.add(Token.EOF);
    return expected;
  }

  /** LL1Analyzer.getDecisionLookahead: per alternative, the LL(1) lookahead with global FOLLOW at the rule end. */
  TokenSet[] decisionLookahead(State s) {
    TokenSet[] look = new TokenSet[s.size()];
    for (int alt = 0; alt < s.size(); alt++) {
      look[alt] = new TokenSet();
      look(s.transition(alt).target, null, LookCtx.EMPTY, look[alt], new HashSet<>(), new BitSet(), false, false);
      if (look[alt].isEmpty()) look[alt] = null;
    }
    return look;
  }

  /** The tool's AnalysisPipeline.disjoint: an LL(1) decision has pairwise disjoint, non-null lookahead sets. */
  static boolean disjoint(TokenSet[] look) {
    TokenSet combined = new TokenSet();
    boolean collision = false;
    for (TokenSet l : look) {
      if (l == null) return false;
      if (l.intersects(combined)) collision = true;
      combined.addAll(l);
    }
    return !collision;
  }

  /** The LOOK context: null (stop at rule end with EPSILON), EMPTY (follow global FOLLOW links), or a call stack. */
  static final class LookCtx {
    static final LookCtx NONE = null;
    static final LookCtx EMPTY = new LookCtx(null, -1);
    final LookCtx parent;
    final int returnState;

    LookCtx(LookCtx parent, int returnState) {
      this.parent = parent;
      this.returnState = returnState;
    }

    boolean isEmpty() { return this == EMPTY; }

    static LookCtx fromRuleContext(Atn atn, ParserRuleContext ctx) {
      if (ctx == null || ctx.parent == null) return EMPTY;
      LookCtx parent = fromRuleContext(atn, ctx.parent);
      State invoking = atn.states.get(ctx.invokingState);
      return new LookCtx(parent, invoking.transition(0).followState.number);
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof LookCtx l) || l == EMPTY || this == EMPTY) return false;
      return returnState == l.returnState && java.util.Objects.equals(parent, l.parent);
    }

    @Override public int hashCode() { return this == EMPTY ? 1 : 31 * java.util.Objects.hashCode(parent) + returnState; }
  }

  private record Busy(State s, LookCtx ctx) {}

  private void look(State s, State stopState, LookCtx ctx, TokenSet look, Set<Busy> busy, BitSet calledRuleStack,
      boolean addEOF, boolean runtime) {
    if (!busy.add(new Busy(s, ctx))) return;
    if (s == stopState) {
      if (ctx == null) {
        look.add(Token.EPSILON);
        return;
      } else if (ctx.isEmpty() && addEOF) {
        look.add(Token.EOF);
        return;
      }
    }
    if (s.type == StateType.RULE_STOP) {
      if (ctx == null) {
        look.add(Token.EPSILON);
        return;
      } else if (ctx.isEmpty() && addEOF) {
        look.add(Token.EOF);
        return;
      }
      if (!ctx.isEmpty()) {
        boolean removed = calledRuleStack.get(s.ruleIndex);
        try {
          calledRuleStack.clear(s.ruleIndex);
          look(states.get(ctx.returnState), stopState, ctx.parent, look, busy, calledRuleStack, addEOF, runtime);
        } finally {
          if (removed) calledRuleStack.set(s.ruleIndex);
        }
        return;
      }
    }
    for (Transition t : s.transitions) {
      if (t.type == TransitionType.RULE) {
        if (calledRuleStack.get(t.target.ruleIndex)) continue;
        LookCtx newContext = new LookCtx(ctx == null ? null : ctx, t.followState.number);
        // SingletonPredictionContext.create(null ctx, ...) is a context whose parent is null (not EMPTY)
        try {
          calledRuleStack.set(t.target.ruleIndex);
          look(t.target, stopState, newContext, look, busy, calledRuleStack, addEOF, runtime);
        } finally {
          calledRuleStack.clear(t.target.ruleIndex);
        }
      } else if (t.isEpsilon()) {
        look(t.target, stopState, ctx, look, busy, calledRuleStack, addEOF, runtime);
      } else {
        look.addAll(t.label(maxTokenType, runtime));
      }
    }
  }
}

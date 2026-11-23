package org.hl7.fhir.sushi.importer.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

// port of the generated-parser semantics of ANTLR 4.13 (JavaScript target) + antlr4 JS runtime 4.13.2 Parser and
// DefaultErrorStrategy (as used by SUSHI 3.21.1)
/**
 * Recursive-descent parser driven by a {@link Grammar}: each rule invocation enters a rule context, walks the rule's
 * elements exactly as ANTLR's generated code does (match tokens, invoke rules, sync before decisions, LL(1) decisions
 * by lookahead set and the others by {@link Prediction}), and recovers from syntax errors with ANTLR's default error
 * strategy (single-token deletion/insertion, resynchronisation to the follow sets of the active rules), so that the
 * tree, the error messages and the recovered parse are those of SUSHI's ANTLR parser.
 */
final class ParserEngine implements Recognizer {
  interface ContextFactory {
    ParserRuleContext create(int ruleIndex, ParserRuleContext parent, int invokingState);
  }

  private final Grammar g;
  private final Atn atn;
  private final Prediction prediction;
  private final CommonTokenStream input;
  private final ErrorListener listener;
  private final ContextFactory factory;
  private final IntFunction<String> missingTokenName;

  private ParserRuleContext ctx;
  private int state = -1;

  // DefaultErrorStrategy state
  private boolean errorRecoveryMode;
  private int lastErrorIndex = -1;
  private List<Integer> lastErrorStates;

  ParserEngine(Compiled c, CommonTokenStream input, ErrorListener listener, ContextFactory factory) {
    this.g = c.grammar;
    this.atn = c.atn;
    this.prediction = c.prediction;
    this.input = input;
    this.listener = listener;
    this.factory = factory;
    String[] lits = g.literalNames;
    // JS getMissingSymbol: "<missing " + literalNames[type] + ">" (null -> "null", out of range -> "undefined")
    this.missingTokenName = t -> t >= 0 && t < lits.length ? String.valueOf(lits[t]) : "undefined";
  }

  /** A grammar with its ATN and decision analysis, shared by all parses. */
  static final class Compiled {
    final Grammar grammar;
    final Atn atn;
    final Prediction prediction;

    Compiled(Grammar grammar) {
      this.grammar = grammar;
      this.atn = new Atn(grammar);
      this.prediction = new Prediction(atn);
      for (Atn.State s : atn.states) atn.nextTokens(s); // precompute (shared across threads)
      for (Grammar.Rule r : grammar.rules) analyse(r.body());
    }

    /** Like the ANTLR tool: a decision whose alternatives' LL(1) lookahead sets are disjoint is coded as LL(1). */
    private void analyse(Grammar.Elem e) {
      if (e instanceof Grammar.Seq s) {
        s.items.forEach(this::analyse);
      } else if (e instanceof Grammar.Block b) {
        TokenSet[] look = atn.decisionLookahead(b.decisionState);
        b.ll1 = Atn.disjoint(look) ? look : null;
        if ((b.kind == Grammar.Kind.STAR || b.kind == Grammar.Kind.PLUS) && b.alts.size() > 1) {
          TokenSet[] inner = atn.decisionLookahead(b.blockStart);
          b.innerLl1 = Atn.disjoint(inner) ? inner : null;
        }
        b.alts.forEach(this::analyse);
      }
    }
  }

  @Override public List<Token> getTokens() { return input.getTokens(); }

  // ---------------------------------------------------------------- Parser

  ParserRuleContext parse(int ruleIndex) {
    return rule(ruleIndex);
  }

  private ParserRuleContext rule(int ruleIndex) {
    ParserRuleContext local = factory.create(ruleIndex, ctx, state);
    // enterRule
    state = atn.ruleToStart[ruleIndex].number;
    ctx = local;
    local.start = input.LT(1);
    if (local.parent != null) local.parent.addChild(local);
    try {
      exec(g.rules.get(ruleIndex).body());
    } catch (RecognitionException re) {
      local.exception = re;
      reportError(re);
      recover();
    } finally {
      // exitRule
      local.stop = input.LT(-1);
      state = local.invokingState;
      ctx = local.parent;
    }
    return local;
  }

  private Token consume() {
    Token o = input.LT(1);
    if (o.getType() != Token.EOF) input.consume();
    if (errorRecoveryMode) ctx.addErrorNode(o);
    else ctx.addTokenNode(o);
    return o;
  }

  private void match(int ttype) {
    Token t = input.LT(1);
    if (t.getType() == ttype) {
      endErrorCondition();
      consume();
    } else {
      t = recoverInline();
      if (t.getTokenIndex() == -1) ctx.addErrorNode(t);
    }
  }

  private void matchSet(Grammar.TokSet s) {
    int la = input.LA(1);
    boolean ok = s.invert ? la > 0 && !s.set.contains(la) : s.set.contains(la);
    if (!ok) {
      recoverInline(); // generated code ignores the result (a conjured token is not added to the tree)
    } else {
      endErrorCondition();
      consume();
    }
  }

  private RecognitionException noViableAlt() {
    Token t = input.LT(1);
    return new RecognitionException(RecognitionException.Kind.NO_VIABLE_ALT, t, t, state, ctx);
  }

  private int adaptivePredict(Atn.State decisionState) {
    try {
      return prediction.adaptivePredict(input, decisionState, ctx);
    } catch (Prediction.NoViableAlt e) {
      throw new RecognitionException(RecognitionException.Kind.NO_VIABLE_ALT, e.offendingToken, e.startToken, state,
          ctx);
    }
  }

  /** The 1-based alternative of an LL(1) decision whose lookahead contains LA(1), or 0. */
  private int ll1Alt(TokenSet[] look, int count) {
    int la = input.LA(1);
    for (int i = 0; i < count; i++) if (look[i].contains(la)) return i + 1;
    return 0;
  }

  private void exec(Grammar.Elem e) {
    if (e instanceof Grammar.Tok t) {
      state = t.state.number;
      match(t.type);
    } else if (e instanceof Grammar.TokSet s) {
      state = s.state.number;
      matchSet(s);
    } else if (e instanceof Grammar.Ref r) {
      state = r.state.number;
      rule(r.ruleIndex);
    } else if (e instanceof Grammar.Seq s) {
      for (Grammar.Elem x : s.items) exec(x);
    } else if (e instanceof Grammar.Block b) {
      block(b);
    } else {
      throw new IllegalStateException("unknown element " + e);
    }
  }

  private void block(Grammar.Block b) {
    switch (b.kind) {
      case PLAIN -> {
        state = b.decisionState.number;
        sync();
        int alt;
        if (b.ll1 != null) {
          alt = ll1Alt(b.ll1, b.alts.size());
          if (alt == 0) throw noViableAlt();
        } else {
          alt = adaptivePredict(b.decisionState);
        }
        exec(b.alts.get(alt - 1));
      }
      case OPTIONAL -> {
        state = b.decisionState.number;
        sync();
        int alt = b.ll1 != null ? ll1Alt(b.ll1, b.alts.size()) : adaptivePredict(b.decisionState);
        if (alt >= 1 && alt <= b.alts.size()) exec(b.alts.get(alt - 1));
      }
      case STAR -> {
        state = b.decisionState.number;
        sync();
        if (b.ll1 != null) {
          while (b.ll1[0].contains(input.LA(1))) {
            iteration(b);
            state = b.loopBack.number;
            sync();
          }
        } else {
          int alt = adaptivePredict(b.decisionState);
          while (alt != 2 && alt != Prediction.INVALID_ALT) {
            if (alt == 1) iteration(b);
            state = b.loopBack.number;
            sync();
            alt = adaptivePredict(b.decisionState);
          }
        }
      }
      case PLUS -> {
        state = b.blockStart.number;
        sync();
        if (b.ll1 != null) {
          do {
            iteration(b);
            state = b.loopBack.number;
            sync();
          } while (b.ll1[0].contains(input.LA(1)));
        } else {
          int alt = 1;
          do {
            if (alt != 1) throw noViableAlt();
            iteration(b);
            state = b.loopBack.number;
            sync();
            alt = adaptivePredict(b.decisionState);
          } while (alt != 2 && alt != Prediction.INVALID_ALT);
        }
      }
    }
  }

  /** One iteration of a loop's block: its single alternative, or a decision between its alternatives. */
  private void iteration(Grammar.Block b) {
    if (b.alts.size() == 1) {
      exec(b.alts.get(0));
      return;
    }
    state = b.blockStart.number;
    sync();
    int alt;
    if (b.innerLl1 != null) {
      alt = ll1Alt(b.innerLl1, b.alts.size());
      if (alt == 0) throw noViableAlt();
    } else {
      alt = adaptivePredict(b.blockStart);
    }
    exec(b.alts.get(alt - 1));
  }

  // ---------------------------------------------------------------- DefaultErrorStrategy (JS runtime)

  private void beginErrorCondition() { errorRecoveryMode = true; }

  private void endErrorCondition() {
    errorRecoveryMode = false;
    lastErrorStates = null;
    lastErrorIndex = -1;
  }

  private void reportError(RecognitionException e) {
    if (errorRecoveryMode) return; // don't report spurious errors
    beginErrorCondition();
    if (e.getKind() == RecognitionException.Kind.NO_VIABLE_ALT) {
      String in;
      if (e.startToken.getType() == Token.EOF) in = "<EOF>";
      else in = input.getText(e.startToken.getTokenIndex(), e.getOffendingToken().getTokenIndex());
      notifyErrorListeners("no viable alternative at input " + escapeWSAndQuote(in), e.getOffendingToken());
    } else {
      TokenSet expected = atn.getExpectedTokens(atn.states.get(e.offendingState), e.ctx);
      notifyErrorListeners("mismatched input " + getTokenErrorDisplay(e.getOffendingToken()) + " expecting "
          + expected.toString(this::displayName), e.getOffendingToken());
    }
  }

  private String displayName(int t) {
    String[] lit = g.literalNames, sym = g.symbolicNames;
    String l = t >= 0 && t < lit.length ? lit[t] : null;
    if (l != null) return l;
    return t >= 0 && t < sym.length ? sym[t] : null;
  }

  private void recover() {
    if (lastErrorIndex == input.index() && lastErrorStates != null && lastErrorStates.contains(state)) {
      consume();
    }
    lastErrorIndex = input.index();
    if (lastErrorStates == null) lastErrorStates = new ArrayList<>();
    lastErrorStates.add(state);
    consumeUntil(getErrorRecoverySet());
  }

  private void sync() {
    if (errorRecoveryMode) return;
    Atn.State s = atn.states.get(state);
    int la = input.LA(1);
    TokenSet nextTokens = atn.nextTokens(s);
    if (nextTokens.contains(la)) return;
    if (nextTokens.contains(Token.EPSILON)) return;
    switch (s.type) {
      case BLOCK_START, STAR_BLOCK_START, PLUS_BLOCK_START, STAR_LOOP_ENTRY -> {
        if (singleTokenDeletion() != null) return;
        throw inputMismatch();
      }
      case PLUS_LOOP_BACK, STAR_LOOP_BACK -> {
        reportUnwantedToken();
        TokenSet expecting = getExpectedTokens().copy();
        expecting.addAll(getErrorRecoverySet());
        consumeUntil(expecting);
      }
      default -> {}
    }
  }

  private RecognitionException inputMismatch() {
    return new RecognitionException(RecognitionException.Kind.INPUT_MISMATCH, input.LT(1), input.LT(1), state, ctx);
  }

  private TokenSet getExpectedTokens() {
    return atn.getExpectedTokens(atn.states.get(state), ctx);
  }

  private void reportUnwantedToken() {
    if (errorRecoveryMode) return;
    beginErrorCondition();
    Token t = input.LT(1);
    notifyErrorListeners("extraneous input " + getTokenErrorDisplay(t) + " expecting "
        + getExpectedTokens().toString(this::displayName), t);
  }

  private void reportMissingToken() {
    if (errorRecoveryMode) return;
    beginErrorCondition();
    Token t = input.LT(1);
    notifyErrorListeners("missing " + getExpectedTokens().toString(this::displayName) + " at "
        + getTokenErrorDisplay(t), t);
  }

  private Token recoverInline() {
    Token matchedSymbol = singleTokenDeletion();
    if (matchedSymbol != null) {
      consume();
      return matchedSymbol;
    }
    if (singleTokenInsertion()) return getMissingSymbol();
    throw inputMismatch();
  }

  private boolean singleTokenInsertion() {
    int currentSymbolType = input.LA(1);
    Atn.State next = atn.states.get(state).transition(0).target;
    TokenSet expectingAtLL2 = atn.nextTokens(next, ctx);
    if (expectingAtLL2.contains(currentSymbolType)) {
      reportMissingToken();
      return true;
    }
    return false;
  }

  private Token singleTokenDeletion() {
    int nextTokenType = input.LA(2);
    TokenSet expecting = getExpectedTokens();
    if (expecting.contains(nextTokenType)) {
      reportUnwantedToken();
      consume(); // simply delete extra token
      Token matchedSymbol = input.LT(1);
      endErrorCondition(); // we know current token is correct
      return matchedSymbol;
    }
    return null;
  }

  private Token getMissingSymbol() {
    Token currentSymbol = input.LT(1);
    TokenSet expecting = getExpectedTokens();
    int expectedTokenType = expecting.min();
    String tokenText = expectedTokenType == Token.EOF ? "<missing EOF>"
        : "<missing " + missingTokenName.apply(expectedTokenType) + ">";
    Token current = currentSymbol;
    Token lookback = input.LT(-1);
    if (current.getType() == Token.EOF && lookback != null) current = lookback;
    return new Token(current.getInputStream(), expectedTokenType, tokenText, Token.DEFAULT_CHANNEL, -1, -1,
        current.getLine(), current.getCharPositionInLine());
  }

  private static String getTokenErrorDisplay(Token t) {
    if (t == null) return "<no token>";
    String s = t.getText();
    if (s == null) s = t.getType() == Token.EOF ? "<EOF>" : "<" + t.getType() + ">";
    return escapeWSAndQuote(s);
  }

  private static String escapeWSAndQuote(String s) {
    return "'" + s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "'";
  }

  private TokenSet getErrorRecoverySet() {
    ParserRuleContext c = ctx;
    TokenSet recoverSet = new TokenSet();
    while (c != null && c.invokingState >= 0) {
      Atn.State invokingState = atn.states.get(c.invokingState);
      recoverSet.addAll(atn.nextTokens(invokingState.transition(0).followState));
      c = c.parent;
    }
    recoverSet.remove(Token.EPSILON);
    return recoverSet;
  }

  private void consumeUntil(TokenSet set) {
    int ttype = input.LA(1);
    while (ttype != Token.EOF && !set.contains(ttype)) {
      consume();
      ttype = input.LA(1);
    }
  }

  private void notifyErrorListeners(String msg, Token offendingToken) {
    if (offendingToken == null) offendingToken = input.LT(1);
    listener.syntaxError(this, offendingToken, offendingToken.getLine(), offendingToken.getCharPositionInLine(), msg);
  }
}

package org.hl7.fhir.sushi.importer.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.DecisionState;
import org.antlr.v4.runtime.atn.RuleTransition;
import org.antlr.v4.runtime.atn.Transition;
import org.junit.jupiter.api.Test;

/**
 * The hand-built ATN (Atn from FshGrammar) is the ANTLR tool's ATN, state by state: same numbering, types, rule
 * indices, decisions, transitions (kind, target, label, rule follow state), in the same order; and the decisions the
 * hand-written parser predicts adaptively are exactly those the ANTLR-generated parser predicts adaptively.
 */
class AtnEquivalenceTest {
  static String dumpAntlr(ATN atn) {
    StringBuilder sb = new StringBuilder();
    for (ATNState s : atn.states) {
      sb.append(s.stateNumber).append(' ').append(ATNState.serializationNames.get(s.getStateType()))
          .append(" r=").append(s.ruleIndex);
      if (s instanceof DecisionState d && d.decision >= 0) sb.append(" d=").append(d.decision);
      sb.append(" :");
      for (int i = 0; i < s.getNumberOfTransitions(); i++) {
        Transition t = s.transition(i);
        String kind = Transition.serializationNames.get(t.getSerializationType());
        sb.append(' ').append(kind).append("->").append(t.target.stateNumber);
        if (kind.equals("ATOM")) sb.append('[').append(t.label().getMinElement()).append(']');
        if (kind.equals("SET") || kind.equals("NOT_SET")) {
          // the oracle patches NOT_SET labels (JS quirk); compare the real set
          org.antlr.v4.runtime.misc.IntervalSet set = t instanceof AntlrOracle.JsNotSet js ? js.real
              : ((org.antlr.v4.runtime.atn.SetTransition) t).set;
          sb.append(new TreeSet<>(set.toList()));
        }
        if (t instanceof RuleTransition rt) sb.append("(follow ").append(rt.followState.stateNumber).append(')');
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  static String dumpMine(Atn atn) {
    StringBuilder sb = new StringBuilder();
    for (Atn.State s : atn.states) {
      sb.append(s.number).append(' ').append(s.type).append(" r=").append(s.ruleIndex);
      if (s.decision >= 0) sb.append(" d=").append(s.decision);
      sb.append(" :");
      for (Atn.Transition t : s.transitions) {
        sb.append(' ').append(t.type).append("->").append(t.target.number);
        if (t.type == Atn.TransitionType.ATOM) sb.append('[').append(t.label).append(']');
        if (t.type == Atn.TransitionType.SET || t.type == Atn.TransitionType.NOT_SET) {
          Set<Integer> members = new TreeSet<>();
          for (int x = -2; x <= atn.maxTokenType; x++) if (t.set.contains(x)) members.add(x);
          sb.append(members);
        }
        if (t.type == Atn.TransitionType.RULE) sb.append("(follow ").append(t.followState.number).append(')');
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  static List<Integer> adaptiveDecisions(ParserEngine.Compiled c) {
    List<Integer> out = new ArrayList<>();
    collect(c, out);
    return out;
  }

  private static void collect(ParserEngine.Compiled c, List<Integer> out) {
    for (Grammar.Rule r : c.grammar.rules) walk(r.body(), out);
    out.sort(null);
  }

  private static void walk(Grammar.Elem e, List<Integer> out) {
    if (e instanceof Grammar.Seq s) {
      s.items.forEach(x -> walk(x, out));
    } else if (e instanceof Grammar.Block b) {
      if (b.ll1 == null) out.add(b.decisionState.decision);
      if (b.alts.size() > 1 && (b.kind == Grammar.Kind.STAR || b.kind == Grammar.Kind.PLUS) && b.innerLl1 == null) {
        out.add(b.blockStart.decision);
      }
      b.alts.forEach(x -> walk(x, out));
    }
  }

  @Test void fshAtnIsAntlrs() {
    // AntlrOracle patches NOT_SET labels; load it first so both dumps see the same ATN object state
    AntlrOracle.parseDoc("", (r, t, l, c, m) -> {});
    String expected = dumpAntlr(org.hl7.fhir.sushi.importer.antlr.FSHParser._ATN);
    String actual = dumpMine(HandParserBackend.FSH.atn).replace("RULE_START", "RULE_START")
        .replace(" EPSILON->", " EPSILON->");
    assertEquals(normalize(expected), normalize(actual));
  }

  @Test void miniAtnIsAntlrs() {
    String expected = dumpAntlr(org.hl7.fhir.sushi.importer.antlr.MiniFSHParser._ATN);
    assertEquals(normalize(expected), normalize(dumpMine(HandParserBackend.MINI.atn)));
  }

  /** The decisions the generated FSHParser/MiniFSHParser call adaptivePredict for (the rest are LL(1) switches). */
  @Test void adaptiveDecisionsAreAntlrs() {
    assertEquals(List.of(1, 13, 14, 18, 22, 26, 30, 32, 38, 48, 61, 71, 77, 82, 87),
        adaptiveDecisions(HandParserBackend.FSH));
    assertEquals(List.of(), adaptiveDecisions(HandParserBackend.MINI));
  }

  private static String normalize(String s) {
    // ANTLR serialization names vs our enum names
    return s.replace("BASIC", "BASIC").replace("STAR_BLOCK_START", "STAR_BLOCK_START");
  }
}

package org.hl7.fhir.sushi.importer.parser;

import java.nio.file.Files;
import java.nio.file.Path;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.DecisionState;
import org.antlr.v4.runtime.atn.RuleTransition;
import org.antlr.v4.runtime.atn.Transition;
import org.junit.jupiter.api.Test;

/** Dev aid: prints the ANTLR parser ATN (-Dwp1.atnDump=<file>). */
class AntlrAtnDumpTest {
  @Test void dump() throws Exception {
    String out = System.getProperty("wp1.atnDump");
    if (out == null) return;
    StringBuilder sb = new StringBuilder();
    for (ATN atn : new ATN[] {org.hl7.fhir.sushi.importer.antlr.FSHParser._ATN, org.hl7.fhir.sushi.importer.antlr.MiniFSHParser._ATN}) {
      sb.append("=== ATN\n");
      for (ATNState s : atn.states) {
        if (s == null) { sb.append("null\n"); continue; }
        sb.append(s.stateNumber).append(' ').append(ATNState.serializationNames.get(s.getStateType()))
            .append(" r=").append(s.ruleIndex);
        if (s instanceof DecisionState d) sb.append(" d=").append(d.decision).append(d.nonGreedy ? " nongreedy" : "");
        sb.append(" :");
        for (int i = 0; i < s.getNumberOfTransitions(); i++) {
          Transition t = s.transition(i);
          sb.append(' ').append(Transition.serializationNames.get(t.getSerializationType())).append("->").append(t.target.stateNumber);
          if (t.label() != null) sb.append('[').append(t.label().toString(org.hl7.fhir.sushi.importer.antlr.FSHParser.VOCABULARY)).append(']');
          if (t instanceof RuleTransition rt) sb.append("(follow ").append(rt.followState.stateNumber).append(')');
        }
        sb.append('\n');
      }
    }
    Files.writeString(Path.of(out), sb.toString());
  }
}

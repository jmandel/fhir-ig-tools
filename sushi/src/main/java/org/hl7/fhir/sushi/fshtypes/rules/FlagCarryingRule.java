package org.hl7.fhir.sushi.fshtypes.rules;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.Common;

// port of sushi/src/fshtypes/rules/FlagCarryingRule.ts (SUSHI 3.21.1)
/** Common base of FlagRule and AddElementRule (TS originally used a mixin). Flags are optional booleans. */
public abstract class FlagCarryingRule extends Rule {
  public Boolean mustSupport;
  public Boolean summary;
  public Boolean modifier;
  public Boolean trialUse;
  public Boolean normative;
  public Boolean draft;

  protected FlagCarryingRule(String path) {
    super(path);
  }

  /** FSH flag tokens in canonical order: MS, ?!, SU, then one of D / TU / N (draft wins, then trialUse). */
  public List<String> flags() {
    List<String> flags = new ArrayList<>();
    if (Common.truthy(mustSupport)) flags.add("MS");
    if (Common.truthy(modifier)) flags.add("?!");
    if (Common.truthy(summary)) flags.add("SU");
    if (Common.truthy(draft)) flags.add("D");
    else if (Common.truthy(trialUse)) flags.add("TU");
    else if (Common.truthy(normative)) flags.add("N");
    return flags;
  }
}

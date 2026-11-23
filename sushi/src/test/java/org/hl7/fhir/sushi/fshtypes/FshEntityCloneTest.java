package org.hl7.fhir.sushi.fshtypes;

import static org.junit.jupiter.api.Assertions.*;

import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.junit.jupiter.api.Test;

class FshEntityCloneTest {
  @Test void deepClonesRulesWithValuesAndSourceInfo() {
    AssignmentRule r = new AssignmentRule("code").withFile("a.fsh").withLocation(1, 2, 3, 4);
    r.value = new FshCode("x", "http://s").withFile("a.fsh");
    AssignmentRule c = r.cloneDeep();
    assertNotSame(r, c);
    assertNotSame(r.sourceInfo, c.sourceInfo);
    assertEquals(r.sourceInfo, c.sourceInfo);
    assertNotSame(r.value, c.value);
    assertEquals(((FshCode) r.value).code, ((FshCode) c.value).code);
    c.sourceInfo.location.startLine = 9;
    assertEquals(1, r.sourceInfo.location.startLine);
  }
}

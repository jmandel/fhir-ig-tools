package org.hl7.fhir.sushi.fshtypes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.junit.jupiter.api.Test;

/** Hand-ported cases from sushi/test/fshtypes (FshCode, FshQuantity, CardRule, FlagRule, InsertRule, Profile). */
class FshTypesSmokeTest {
  // FshCode.test.ts #toString
  @Test void fshCodeToString() {
    assertEquals("#my-code", new FshCode("my-code").toString());
    assertEquals("http://foo.com#my-code", new FshCode("my-code", "http://foo.com").toString());
    assertEquals("#my-code \"Display\"", new FshCode("my-code", null, "Display").toString());
    assertEquals("http://foo.com#my-code \"Display\"", new FshCode("my-code", "http://foo.com", "Display").toString());
    assertEquals("#\"my spacey code\"", new FshCode("my spacey code").toString());
    assertEquals("#\"my\\ttabby\\tcode\"", new FshCode("my\ttabby\tcode").toString());
    assertEquals("#\"\\\"my-code\"", new FshCode("\"my-code").toString());
    assertEquals("#\"strange\\\\ \\\"code\\\"\"", new FshCode("strange\\ \"code\"").toString());
    assertEquals("#strange-display \"very \\\"strange\\\"\\\\display\"",
        new FshCode("strange-display", null, "very \"strange\"\\display").toString());
  }

  @Test void fshCodeToFHIRCodingSplitsVersionAndKeepsKeyOrder() {
    var c = new FshCode("c", "http://sys|1.0|x", "D").toFHIRCoding();
    assertEquals(List.of("code", "system", "version", "display"), List.copyOf(c.keySet()));
    assertEquals("http://sys", c.get("system"));
    assertEquals("1.0|x", c.get("version"));
  }

  // FshQuantity.test.ts #toString
  @Test void fshQuantityToString() {
    assertEquals("100", new FshQuantity(100.0).toString());
    assertEquals("100 #mm", new FshQuantity(100.0, new FshCode("mm")).toString());
    assertEquals("#mm", new FshQuantity(null, new FshCode("mm")).toString());
    assertEquals("100 'mm'", new FshQuantity(100.0, new FshCode("mm", "http://unitsofmeasure.org")).toString());
    assertEquals("100 http://foo.com#bar", new FshQuantity(100.0, new FshCode("bar", "http://foo.com")).toString());
    assertEquals("100 'mm' \"Display\"",
        new FshQuantity(100.0, new FshCode("mm", "http://unitsofmeasure.org", "Display")).toString());
    assertEquals("100 #\"milli meters\"", new FshQuantity(100.0, new FshCode("milli meters")).toString());
    assertEquals("100 'mm' \"strange\\\" display\\\\\"",
        new FshQuantity(100.0, new FshCode("mm", "http://unitsofmeasure.org", "strange\" display\\")).toString());
    // JS number formatting (not Java's "1.5E-7")
    assertEquals("1.5e-7", new FshQuantity(1.5e-7).toString());
    assertEquals("0.1", new FshQuantity(0.1).toString());
  }

  // CardRule.test.ts
  @Test void cardRule() {
    CardRule c = new CardRule("component.code");
    assertNull(c.min);
    assertNull(c.max);
    CardRule r = new CardRule("name");
    r.min = 2.0;
    r.max = "8";
    assertEquals("* name 2..8", r.toFSH());
    r = new CardRule("photo");
    r.min = 3.0;
    assertEquals("* photo 3..", r.toFSH());
    r = new CardRule("contact");
    r.min = Double.NaN;
    r.max = "5";
    assertEquals("* contact ..5", r.toFSH());
  }

  // FlagRule.test.ts
  @Test void flagRule() {
    FlagRule r = new FlagRule("name");
    r.mustSupport = true;
    assertEquals("* name MS", r.toFSH());
    r = new FlagRule("name");
    r.modifier = true;
    assertEquals("* name ?!", r.toFSH());
    r = new FlagRule("name");
    r.mustSupport = true;
    r.summary = true;
    r.trialUse = true;
    assertEquals("* name MS SU TU", r.toFSH());
    r = new FlagRule("name");
    r.draft = true;
    r.trialUse = true;
    r.normative = true;
    assertEquals("* name D", r.toFSH());
    r = new FlagRule("name");
    r.trialUse = true;
    r.normative = true;
    assertEquals("* name TU", r.toFSH());
  }

  // InsertRule.test.ts
  @Test void insertRule() {
    InsertRule i = new InsertRule("foo");
    assertNull(i.ruleSet);
    assertTrue(i.params.isEmpty());
    InsertRule r = new InsertRule("");
    r.ruleSet = "MyRuleSet";
    assertEquals("* insert MyRuleSet", r.toFSH());
    r.params = List.of("this\\that", "oh, no", "(big)", "more regular", " less regular ", "[[a]], ([[b]]), [[c]]");
    assertEquals("* insert MyRuleSet(this\\\\that, [[oh, no]], [[(big)]], more regular, [[ less regular ]], "
        + "[[[[a]]\\, ([[b]]\\), [[c]]]])", r.toFSH());
    r = new InsertRule("");
    r.ruleSet = "MyRuleSet";
    r.pathArray = List.of("pizza", "bagelpizza");
    assertEquals("* #pizza #bagelpizza insert MyRuleSet", r.toFSH());
  }

  // Profile/Instance: id derived from caret/assignment rules; fluent source info; AllowedRules
  @Test void idFromRulesAndAllowedRules() {
    Profile p = new Profile("Foo").withFile("a.fsh").withLocation(1, 1, 3, 5);
    assertEquals("Foo", p.getId());
    assertEquals(new TextLocation(1, 1, 3, 5), p.sourceInfo.location);
    CaretValueRule idRule = new CaretValueRule("");
    idRule.caretPath = "id";
    idRule.value = "foo-id";
    p.rules.add(idRule);
    assertEquals("foo-id", p.getId());
    assertEquals("Profile: Foo\nId: foo-id\n* ^id = \"foo-id\"", p.toFSH());
    assertTrue(AllowedRules.isAllowedRule(p, idRule));
    assertFalse(AllowedRules.isAllowedRule(p, new InsertRule("")));
    assertTrue(AllowedRules.isAllowedRule(new FshValueSet("V"), new ValueSetConceptComponentRule(true)));
    assertFalse(AllowedRules.isAllowedRule(new Logical("L"), new ContainsRule("x")));

    Instance inst = new Instance("ex");
    inst.instanceOf = "Patient";
    AssignmentRule a = new AssignmentRule("id");
    a.value = "ex-id";
    inst.rules.add(a);
    assertEquals("ex-id", inst.getId());
    assertEquals("Instance: ex\nInstanceOf: Patient\nUsage: #example\n* id = \"ex-id\"", inst.toFSH());
  }

  @Test void paramRuleSetUnusedParameters() {
    ParamRuleSet prs = new ParamRuleSet("P");
    prs.parameters.addAll(List.of("a", "b.c", "d"));
    prs.contents = "* foo = { a }\n* bar = {b.c}";
    assertEquals(List.of("d"), prs.getUnusedParameters());
  }
}

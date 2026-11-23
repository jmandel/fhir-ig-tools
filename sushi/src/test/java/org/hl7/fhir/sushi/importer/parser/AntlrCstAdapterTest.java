package org.hl7.fhir.sushi.importer.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** The ANTLR-oracle CST adapter produces the CST shape WP-2 visits. */
class AntlrCstAdapterTest {
  static final String FSH = """
      Alias: LNC = http://loinc.org
      Profile: MyObs
      Parent: Observation
      Id: my-obs
      Title: "My Observation"
      * status MS
      * code = LNC#1234-5 "Thing"
      * value[x] only Quantity
      * component contains a 0..1 and b 1..*
      Instance: ex1
      InstanceOf: MyObs
      Usage: #example
      * status = #final
      * valueQuantity = 5.0 'mg' "milligram"
      RuleSet: Params(a, [[b, c]])
      * code = {a}
      """;

  @Test void buildsTypedCst() {
    CstDump.Recorder rec = new CstDump.Recorder();
    FSHParser.DocContext doc = new AntlrCstBackend().parseDoc(FSH, rec);
    assertEquals("", rec.out.toString());
    assertEquals(4, doc.entity().size());
    FSHParser.AliasContext alias = doc.entity(0).alias();
    assertEquals("LNC", alias.name().getText());
    assertEquals("http://loinc.org", alias.SEQUENCE().getText());
    assertNull(alias.CODE());
    FSHParser.ProfileContext p = doc.entity(1).profile();
    assertNull(doc.entity(1).alias());
    assertEquals("MyObs", p.name().getText());
    assertEquals(3, p.sdMetadata().size());
    assertEquals("Observation", p.sdMetadata(0).parent().name().getText());
    assertEquals(4, p.sdRule().size());
    FSHParser.FlagRuleContext flag = p.sdRule(0).flagRule();
    assertEquals("\n* ", flag.STAR().getText());
    // the STAR token starts with the preceding newline (TS: STAR().symbol.line + 1)
    assertEquals(5, flag.STAR().getSymbol().getLine());
    assertEquals("status", flag.path(0).getText());
    assertEquals(1, flag.flag().size());
    FSHParser.FixedValueRuleContext fv = p.sdRule(1).fixedValueRule();
    assertEquals("LNC#1234-5", fv.value().code().CODE().getText());
    assertEquals("\"Thing\"", fv.value().code().STRING().getText());
    FSHParser.ContainsRuleContext cr = p.sdRule(3).containsRule();
    assertEquals(2, cr.item().size());
    assertEquals("1..*", cr.item(1).CARD().getText());
    assertSame(cr, cr.item(1).getParent());
    FSHParser.InstanceContext inst = doc.entity(2).instance();
    FSHParser.QuantityContext q = inst.instanceRule(1).fixedValueRule().value().quantity();
    assertEquals("5.0", q.NUMBER().getText());
    assertEquals("'mg'", q.UNIT().getText());
    FSHParser.ParamRuleSetContext prs = doc.entity(3).paramRuleSet();
    assertEquals(" Params(", prs.paramRuleSetRef().PARAM_RULESET_REFERENCE().getText());
    assertEquals(1, prs.paramRuleSetRef().parameter().size());
    assertEquals(" [[b, c]])", prs.paramRuleSetRef().lastParameter().getText());
    FSHParser.ParamRuleSetContentContext content = prs.paramRuleSetContent();
    assertEquals("\n* code = {a}",
        content.start.getInputStream().getText(content.start.getStartIndex(), content.stop.getStopIndex()));
    // locations: stop token columns are UTF-16
    assertEquals(16, content.stop.getLine());
    assertNotNull(doc.EOF());
    assertEquals("<EOF>", doc.EOF().getText());
  }

  @Test void reportsErrorsWithJsRuntimeText() {
    CstDump.Recorder rec = new CstDump.Recorder();
    FSHParser.DocContext doc = new AntlrCstBackend().parseDoc("Profile: Foo\nParent Observation\n", rec);
    assertTrue(rec.count > 0, rec.out.toString());
    assertNotNull(doc);
  }

  @Test void conjuredTokenTextLikeJs() {
    // single-token insertion of SEQUENCE-less tokens: JS text is "<missing " + literalNames[t] + ">"
    CstDump.Recorder rec = new CstDump.Recorder();
    FSHParser.DocContext doc = new AntlrCstBackend().parseDoc("Alias: $A= http://x.org\n", rec);
    assertEquals("1:11 4 missing \\'=\\' at \\'http://x.org\\'\n", rec.out.toString());
    assertEquals("<missing '='>", doc.entity(0).alias().EQUAL().getText());
    assertInstanceOf(ErrorNode.class, doc.entity(0).alias().EQUAL());
  }

  @Test void errorListenerRewritesKnownPatterns() {
    List<String> msgs = new ArrayList<>();
    List<TextLocation> locs = new ArrayList<>();
    FSHErrorListener l = new FSHErrorListener("Invalid.fsh") {
      @Override public void syntaxError(Recognizer r, Token t, int line, int col, String msg) {
        ErrorMessage m = buildErrorMessage(r, t, line, col, msg);
        msgs.add(m.message());
        locs.add(m.location());
      }
    };
    new AntlrCstBackend().parseDoc("\nAlias: $OrgType= http://terminology.hl7.org/CodeSystem/organization-type\n", l);
    assertEquals(List.of("Alias declarations must include at least one space both before and after the '=' sign"), msgs);
    assertEquals(new TextLocation(2, 8, 2, 16), locs.get(0));
  }

  @Test void miniFsh() {
    CstDump.Recorder rec = new CstDump.Recorder();
    MiniFSHParser.RuleSetContext rs = new AntlrCstBackend().parseMiniFsh("\n* code = {a}\n  * insert Foo({b})", rec);
    assertEquals("", rec.out.toString());
    assertEquals(2, rs.someRule().size());
    assertEquals("insert", rs.someRule(1).rulePart(0).getText());
  }

  /** All FSH fixtures of the TS checkout and a corpus IG convert without inconsistencies (skipped when absent). */
  @Test void convertsRealFshFiles() throws IOException {
    // SUSHI's test fixtures (vendored) and, opt-in, a corpus IG (-Dsushi.vmdata)
    Path corpusIg = org.hl7.fhir.sushi.testhelpers.OptInData.dir("sushi.genomics.fsh", "sushi-spike/src/HL7__genomics-reporting/input/fsh");
    List<Path> roots = Stream.concat(Stream.of(Paths.get(org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.tsDir(), "test")),
            corpusIg == null ? Stream.<Path>empty() : Stream.of(corpusIg))
        .filter(Files::isDirectory).toList();
    Assumptions.assumeFalse(roots.isEmpty(), "no FSH sources on this machine");
    int files = 0;
    for (Path root : roots) {
      try (Stream<Path> s = Files.walk(root)) {
        for (Path p : s.filter(f -> f.toString().endsWith(".fsh")).sorted().toList()) {
          String text = Files.readString(p, StandardCharsets.UTF_8);
          AntlrOracle.Result<FSHParser.DocContext> r = AntlrOracle.parseDoc(text, new CstDump.Recorder());
          assertNotNull(CstDump.tree(r.tree(), FSHParser.ruleNames));
          files++;
        }
      }
    }
    assertTrue(files > 0);
  }
}

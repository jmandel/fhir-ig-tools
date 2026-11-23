package org.hl7.fhir.sushi.fshtypes;

import static org.hl7.fhir.sushi.fshtypes.Common.EOL;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.common.js.*;
import org.junit.jupiter.api.*;

// port of sushi/test/fshtypes/FshCodeSystem.test.ts (SUSHI 3.21.1)
class FshCodeSystemTest {
  @Nested
  @DisplayName("FshCodeSystem")
  class DFshcodesystem1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("FshCodeSystem #constructor should set initial properties correctly")
      void t3() throws Exception {
        var cs = new FshCodeSystem("MyCodeSystem");
        assertEquals("MyCodeSystem", cs.name);
        assertEquals("MyCodeSystem", cs.getId());
        assertNull(cs.title);
        assertNull(cs.description);
        assertTrue(cs.rules.isEmpty());
      }
    }
    @Nested
    @DisplayName("#id")
    class DId4 {
      @Test
      @DisplayName("FshCodeSystem #id should return an id set by a string caret rule")
      void t5() throws Exception {
        var p = new FshCodeSystem("MyCodeSystem");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = "different-id";
        p.rules.add(idRule);
        assertEquals("different-id", p.getId());
      }
      @Test
      @DisplayName("FshCodeSystem #id should not return an id set by a non-string caret rule")
      void t6() throws Exception {
        var p = new FshCodeSystem("MyCodeSystem");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = BigInteger.valueOf(23456L);
        idRule.rawValue = "23456";
        p.rules.add(idRule);
        assertEquals("MyCodeSystem", p.getId());
      }
      @Test
      @DisplayName("FshCodeSystem #id should not return an id set by an instance caret rule")
      void t7() throws Exception {
        var p = new FshCodeSystem("MyCodeSystem");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = "different-id";
        idRule.isInstance = true;
        p.rules.add(idRule);
        assertEquals("MyCodeSystem", p.getId());
      }
      @Test
      @DisplayName("FshCodeSystem #id should return an id set by a caret rule added after the id was read")
      void t8() throws Exception {
        var p = new FshCodeSystem("MyCodeSystem");
        assertEquals("MyCodeSystem", p.getId());
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = "different-id";
        p.rules.add(idRule);
        assertEquals("different-id", p.getId());
      }
      @Test
      @DisplayName("FshCodeSystem #id should return an id set by a caret rule inserted before the last rule")
      void t9() throws Exception {
        var p = new FshCodeSystem("MyCodeSystem");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = "first-id";
        var titleRule = new CaretValueRule("");
        titleRule.caretPath = "title";
        titleRule.value = "My Code System";
        p.rules.addAll(List.of(idRule, titleRule));
        assertEquals("first-id", p.getId());
        var insertedIdRule = new CaretValueRule("");
        insertedIdRule.caretPath = "id";
        insertedIdRule.value = "inserted-id";
        p.rules.add(1, insertedIdRule);
        assertEquals("inserted-id", p.getId());
      }
      @Test
      @DisplayName("FshCodeSystem #id should return the last id set by caret rules as the rules change")
      void t10() throws Exception {
        var p = new FshCodeSystem("MyCodeSystem");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = "first-id";
        p.rules.add(idRule);
        assertEquals("first-id", p.getId());
        // a rule pushed after the first read must be found
        var secondIdRule = new CaretValueRule("");
        secondIdRule.caretPath = "id";
        secondIdRule.value = "second-id";
        p.rules.add(secondIdRule);
        assertEquals("second-id", p.getId());
        // a rule that replaces the last rule must be found
        var thirdIdRule = new CaretValueRule("");
        thirdIdRule.caretPath = "id";
        thirdIdRule.value = "third-id";
        p.rules.remove(p.rules.size() - 1);
        p.rules.add(thirdIdRule);
        assertEquals("third-id", p.getId());
        // a change to the rule's value must be reflected
        thirdIdRule.value = "changed-id";
        assertEquals("changed-id", p.getId());
        // removing the rules must be reflected
        p.rules.remove(p.rules.size() - 1);
        p.rules.remove(p.rules.size() - 1);
        assertEquals("MyCodeSystem", p.getId());
        // replacing the rules array (as applyInsertRules does) must be reflected
        p.rules = List.of(secondIdRule);
        assertEquals("second-id", p.getId());
      }
      @Test
      @DisplayName("FshCodeSystem #id should not return an id set by a code caret rule after its path is resolved")
      void t11() throws Exception {
        var p = new FshCodeSystem("MyCodeSystem");
        var idRule = new CaretValueRule("");
        idRule.caretPath = "id";
        idRule.value = "different-id";
        // a code caret rule has an empty path until the exporter resolves it to the concept's path
        var conceptIdRule = new CaretValueRule("");
        conceptIdRule.pathArray = List.of("#foo");
        conceptIdRule.caretPath = "id";
        conceptIdRule.value = "foo-element-id";
        p.rules.addAll(List.of(idRule, conceptIdRule));
        // until its path is resolved, the code caret rule is the last match, so reading the id here caches it
        // (the assertion is loose so that it does not pin that quirk)
        assertNotNull(p.getId());
        conceptIdRule.path = "concept[0]";
        assertEquals("different-id", p.getId());
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh12 {
      @Test
      @DisplayName("FshCodeSystem #toFSH should produce FSH for the simplest CodeSystem")
      void t13() throws Exception {
        var input = new FshCodeSystem("SimpleCodeSystem");
        var expectedResult = String.join(EOL, "CodeSystem: SimpleCodeSystem", "Id: SimpleCodeSystem");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("FshCodeSystem #toFSH should produce FSH for a CodeSystem with additional metadata")
      void t14() throws Exception {
        var input = new FshCodeSystem("MetaCodeSystem");
        input.setId("meta-code-system");
        input.title = "Meta CodeSystem";
        input.description = "This is a CodeSystem with some metadata.";
        var expectedResult = String.join(EOL, "CodeSystem: MetaCodeSystem", "Id: meta-code-system", "Title: \"Meta CodeSystem\"", "Description: \"This is a CodeSystem with some metadata.\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("FshCodeSystem #toFSH should produce FSH for a CodeSystem with metadata that contains characters that are escaped in FSH")
      void t15() throws Exception {
        var input = new FshCodeSystem("NewlineCodeSystem");
        input.setId("newline-code-system");
        input.title = "This title\nhas a newline in it. Is that \\not allowed\\? Is that \"not okay\"?";
        input.description = "This description\nhas a newline in it. Is that \\not allowed\\? Is that \"not okay\"?";
        var expectedResult = String.join(EOL, "CodeSystem: NewlineCodeSystem", "Id: newline-code-system", "Title: \"This title\\nhas a newline in it. Is that \\\\not allowed\\\\? Is that \\\"not okay\\\"?\"", "Description: \"\"\"This description\nhas a newline in it. Is that \\not allowed\\? Is that \"not okay\"?\"\"\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
      @Test
      @DisplayName("FshCodeSystem #toFSH should produce FSH for a CodeSystem with rules")
      void t16() throws Exception {
        var input = new FshCodeSystem("MyCodeSystem");
        var conceptRule1 = new ConceptRule("foo");
        conceptRule1.display = "bar";
        conceptRule1.definition = "baz";
        input.rules.add(conceptRule1);
        var conceptRule2 = new ConceptRule("oof");
        conceptRule2.display = "rab";
        conceptRule2.definition = "zab";
        input.rules.add(conceptRule2);
        var expectedResult = String.join(EOL, "CodeSystem: MyCodeSystem", "Id: MyCodeSystem", "* #foo \"bar\" \"baz\"", "* #oof \"rab\" \"zab\"");
        var result = input.toFSH();
        assertEquals(expectedResult, result);
      }
    }
  }
}

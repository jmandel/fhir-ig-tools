package org.hl7.fhir.sushi.js;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** FPL SafeMode.FREEZE: fished resources are deeply frozen; readers unaffected, mutation throws, clones are mutable. */
class FrozenTest {
  @SuppressWarnings("unchecked")
  @Test void deepFreezeBlocksMutationButNotReadingOrCloning() {
    Object src = Json.parse("{\"resourceType\":\"StructureDefinition\",\"snapshot\":{\"element\":[{\"id\":\"a\"}]},\"2\":1}");
    Map<String, Object> f = (Map<String, Object>) Frozen.deepFreeze(src);
    assertTrue(f instanceof JsObject);
    assertEquals(Json.stringify(src), Json.stringify(f));
    assertTrue(Js.isEqual(src, f));
    List<Object> els = (List<Object>) ((Map<String, Object>) f.get("snapshot")).get("element");
    Map<String, Object> el = (Map<String, Object>) els.get(0);
    assertEquals("Cannot assign to read only property 'id' of object '#<Object>'",
        assertThrows(Frozen.FrozenMutationError.class, () -> el.put("id", "b")).getMessage());
    assertThrows(Frozen.FrozenMutationError.class, () -> el.put("path", "x"));
    assertThrows(Frozen.FrozenMutationError.class, () -> el.remove("id"));
    assertThrows(Frozen.FrozenMutationError.class, () -> els.add(Map.of()));
    assertThrows(UnsupportedOperationException.class, () -> f.entrySet().iterator().next().setValue(1));
    assertThrows(UnsupportedOperationException.class, () -> { var it = els.iterator(); it.next(); it.remove(); });
    Map<String, Object> copy = Js.cloneDeep(f);
    assertFalse(Frozen.isFrozen(copy));
    copy.put("id", "x");
    ((List<Object>) ((Map<String, Object>) copy.get("snapshot")).get("element")).add(new JsObject());
    assertSame(f, Frozen.deepFreeze(f));
  }
}

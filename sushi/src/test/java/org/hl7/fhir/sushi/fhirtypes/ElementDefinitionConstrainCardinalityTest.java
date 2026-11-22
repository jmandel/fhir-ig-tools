package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertSameState;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.assertThrowsMatching;
import static org.hl7.fhir.sushi.fhirtypes.EdTestSupport.el;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/ElementDefinition.constrainCardinality.test.ts (SUSHI 3.21.1)
@DisplayName("ElementDefinition")
class ElementDefinitionConstrainCardinalityTest {
  static TestFisher fisher;
  StructureDefinition observation;
  StructureDefinition respRate;

  @BeforeAll
  static void beforeAll() {
    fisher = new TestFisher().withFHIR(EdTestSupport.r4Defs());
  }

  @BeforeEach
  void beforeEach() {
    loggerSpy.reset();
    observation = fisher.fishForStructureDefinition("Observation");
    respRate = fisher.fishForStructureDefinition("resprate");
  }

  @Nested
  @DisplayName("#constrainCardinality()")
  class ConstrainCardinality {
    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should allow cardinality to be constrained to same cardinality")
    void sameCardinality() {
      ElementDefinition identifier = el(observation, "Observation.identifier");
      identifier.constrainCardinality(0, "*");
      assertEquals(0.0, identifier.getMin());
      assertEquals("*", identifier.getMax());
      ElementDefinition issued = el(observation, "Observation.issued");
      issued.constrainCardinality(0, "1");
      assertEquals(0.0, issued.getMin());
      assertEquals("1", issued.getMax());
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should allow cardinality to be constrained to higher min")
    void higherMin() {
      ElementDefinition identifier = el(observation, "Observation.identifier");
      // constrain 0..* to 1..*
      identifier.constrainCardinality(1, "*");
      assertEquals(1.0, identifier.getMin());
      assertEquals("*", identifier.getMax());
      ElementDefinition issued = el(observation, "Observation.issued");
      // constrain 0..1 to 1..1
      issued.constrainCardinality(1, "1");
      assertEquals(1.0, issued.getMin());
      assertEquals("1", issued.getMax());
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should allow cardinality to be constrained to lower max")
    void lowerMax() {
      ElementDefinition identifier = el(observation, "Observation.identifier");
      // constrain 0..* to 0..5
      identifier.constrainCardinality(0, "5");
      assertEquals(0.0, identifier.getMin());
      assertEquals("5", identifier.getMax());
      ElementDefinition issued = el(observation, "Observation.issued");
      // constrain 0..1 to 0..0
      issued.constrainCardinality(0, "0");
      assertEquals(0.0, issued.getMin());
      assertEquals("0", issued.getMax());
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should allow cardinality to be constrained to higher min and lower max at same time")
    void higherMinLowerMax() {
      ElementDefinition identifier = el(observation, "Observation.identifier");
      // constrain 0..* to 1..2
      identifier.constrainCardinality(1, "2");
      assertEquals(1.0, identifier.getMin());
      assertEquals("2", identifier.getMax());
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should use elements current cardinality if no min specified")
    void noMin() {
      ElementDefinition identifier = el(observation, "Observation.identifier");
      // constrain 0..* to ..5
      identifier.constrainCardinality(Double.NaN, "5"); // Unspecified min
      assertEquals(0.0, identifier.getMin());
      assertEquals("5", identifier.getMax());
      ElementDefinition issued = el(observation, "Observation.issued");
      // constrain 0..1 to ..0
      issued.constrainCardinality(Double.NaN, "0"); // Unspecified min
      assertEquals(0.0, issued.getMin());
      assertEquals("0", issued.getMax());
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should use elements current cardinality if no max specified")
    void noMax() {
      ElementDefinition identifier = el(observation, "Observation.identifier");
      // constrain 0..* to 2..
      identifier.constrainCardinality(2, ""); // Unspecified max
      assertEquals(2.0, identifier.getMin());
      assertEquals("*", identifier.getMax());
      ElementDefinition issued = el(observation, "Observation.issued");
      // constrain 0..1 to 1..
      issued.constrainCardinality(1, ""); // Unspecified max
      assertEquals(1.0, issued.getMin());
      assertEquals("1", issued.getMax());
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should use elements current cardinality if no min and no max specified")
    void noMinNoMax() {
      ElementDefinition identifier = el(observation, "Observation.identifier");
      // constrain 0..* to ..
      identifier.constrainCardinality(Double.NaN, ""); // Unspecified min and max
      assertEquals(0.0, identifier.getMin());
      assertEquals("*", identifier.getMax());
      ElementDefinition issued = el(observation, "Observation.issued");
      // constrain 0..1 to ..0
      issued.constrainCardinality(Double.NaN, ""); // Unspecified min and max
      assertEquals(0.0, issued.getMin());
      assertEquals("1", issued.getMax());
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should throw InvalidCardinalityError when min > max")
    void minGtMax() {
      ElementDefinition identifier = el(observation, "Observation.identifier");
      ElementDefinition clone = identifier.clone(false);
      assertThrowsMatching("min 2 is > max 1\\.", () -> clone.constrainCardinality(2, "1"));
      assertSameState(identifier, clone);
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should throw ConstrainingCardinalityError when min < original min")
    void minLtOriginalMin() {
      ElementDefinition status = el(observation, "Observation.status");
      ElementDefinition clone = status.clone(false);
      // constrain 1..1 to 0..1
      assertThrowsMatching("0..1, as it does not fit within the original 1..1", () -> clone.constrainCardinality(0, "1"));
      assertSameState(status, clone);
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should throw ConstrainingCardinalityError when max > original max")
    void maxGtOriginalMax() {
      ElementDefinition status = el(observation, "Observation.status");
      ElementDefinition clone = status.clone(false);
      // constrain 1..1 to 1..2
      assertThrowsMatching("1..2, as it does not fit within the original 1..1", () -> clone.constrainCardinality(1, "2"));
      assertSameState(status, clone);
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should throw ConstrainingCardinalityError when min < original min and max > original max at the same time")
    void bothWider() {
      ElementDefinition status = el(observation, "Observation.status");
      ElementDefinition clone = status.clone(false);
      // constrain 1..1 to 0..2
      assertThrowsMatching("0..2, as it does not fit within the original 1..1", () -> clone.constrainCardinality(0, "2"));
      assertSameState(status, clone);
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should throw ConstrainingCardinalityError when max is * and original max is not *")
    void starWider() {
      ElementDefinition status = el(observation, "Observation.status");
      ElementDefinition clone = status.clone(false);
      // constrain 1..1 to 1..*
      assertThrowsMatching("1..\\*, as it does not fit within the original 1..1", () -> clone.constrainCardinality(1, "*"));
      assertSameState(status, clone);
    }

    // Slice Handling
    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should update sliced element min when sum of slice mins is constrainted greater than it")
    void sliceMinSum() {
      ElementDefinition category = el(respRate, "Observation.category");
      ElementDefinition fooSlice = category.addSlice("FooSlice");
      fooSlice.constrainCardinality(2, "2");
      assertEquals(2.0, fooSlice.getMin());
      assertEquals("2", fooSlice.getMax());
      assertEquals(3.0, category.getMin());
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should not change the sliced element min when a slice is resliced with the same min")
    void resliceSameMin() {
      ElementDefinition category = el(respRate, "Observation.category");
      ElementDefinition vsCat = el(respRate, "Observation.category:VSCat");
      // apply slicing to vsCat
      vsCat.sliceIt("value", "coding.code");
      // * category[VSCat] contains FooSlice 1..1
      ElementDefinition fooSlice = vsCat.addSlice("FooSlice");
      fooSlice.constrainCardinality(1, "1");
      assertEquals(1.0, category.getMin());
      assertEquals(1.0, vsCat.getMin());
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should update the min for the sliced element and the parent slice when the sum of reslice mins is constrained greater than them")
    void resliceMinSum() {
      ElementDefinition category = el(respRate, "Observation.category");
      ElementDefinition parentSlice = category.addSlice("ParentSlice");
      parentSlice.constrainCardinality(1, "*");
      parentSlice.sliceIt("value", "coding.code");
      ElementDefinition childSlice = parentSlice.addSlice("ChildSlice");
      childSlice.constrainCardinality(2, "2");
      assertEquals(2.0, parentSlice.getMin()); // contains 2 ChildSlice
      assertEquals(3.0, category.getMin()); // contains 2 ParentSlice/ChildSlice + 1 VSCat
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should update the min for the sliced element and ancestor slices, but not other slices")
    void ancestorSlices() {
      ElementDefinition category = el(respRate, "Observation.category");
      ElementDefinition parentOne = category.addSlice("ParentOne");
      parentOne.sliceIt("value", "coding.code");
      ElementDefinition childOne = parentOne.addSlice("ChildOne");
      ElementDefinition childTwo = parentOne.addSlice("ChildTwo");
      ElementDefinition parentTwo = category.addSlice("ParentTwo");
      parentTwo.sliceIt("value", "coding.code");
      ElementDefinition childThree = parentTwo.addSlice("ChildThree");
      ElementDefinition childFour = parentTwo.addSlice("ChildFour");

      childOne.constrainCardinality(1, "*");
      childTwo.constrainCardinality(2, "*");
      childThree.constrainCardinality(3, "*");
      childFour.constrainCardinality(4, "*");

      // the children should be unchanged from the values that were set
      assertEquals(1.0, childOne.getMin());
      assertEquals(2.0, childTwo.getMin());
      assertEquals(3.0, childThree.getMin());
      assertEquals(4.0, childFour.getMin());
      // the parents should be the sums of only their children, regardless of other slices
      assertEquals(3.0, parentOne.getMin());
      assertEquals(7.0, parentTwo.getMin());
      // the sliced element should be the sum of VSCat + ParentOne + ParentTwo = 1 + 3 + 7 = 11
      assertEquals(11.0, category.getMin());
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should throw InvalidSumOfSliceMinsError when sliced element max is constrained less than sum of slice mins")
    void slicedMaxLtSum() {
      ElementDefinition category = el(respRate, "Observation.category");
      ElementDefinition fooSlice = category.addSlice("FooSlice");
      fooSlice.setMin(1.0);
      ElementDefinition clone = category.clone(false);
      assertThrowsMatching("\\(2\\) > max \\(1\\) of Observation.category\\.",
          () -> category.constrainCardinality(1, "1"));
      assertSameState(clone, category);
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should log a warning and reduce slice cardinality when sliced element max is constrained less than any individual slice max")
    void reduceSliceMax() {
      ElementDefinition category = el(respRate, "Observation.category");
      ElementDefinition fooSlice = category.addSlice("FooSlice");
      fooSlice.setMax("2");
      category.constrainCardinality(1, "1");
      assertTrue(loggerSpy.getAllMessages("warn").contains(
          "At least one slice of Observation.category has a max greater than the overall element max. The max of the following slice(s) has been reduced to match the max of Observation.category: FooSlice"));
      assertEquals("1", category.getMax());
      assertEquals(1.0, category.getMin());
      assertEquals("1", fooSlice.getMax());
    }

    @Test
    @DisplayName("ElementDefinition #constrainCardinality() should throw InvalidSumOfSliceMinsError when sum of slice mins is constrained greater than sliced element max")
    void sliceSumGtMax() {
      ElementDefinition category = el(respRate, "Observation.category");
      ElementDefinition fooSlice = category.addSlice("FooSlice");
      category.setMax("2");
      ElementDefinition clone = fooSlice.clone(false);
      assertThrowsMatching("\\(3\\) > max \\(2\\) of Observation.category\\.",
          () -> fooSlice.constrainCardinality(2, "2"));
      assertSameState(clone, fooSlice);
    }
  }
}

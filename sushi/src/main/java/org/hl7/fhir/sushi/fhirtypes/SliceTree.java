package org.hl7.fhir.sushi.fhirtypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// port of sushi/src/fhirtypes/sliceTree.ts (SUSHI 3.21.1); only consumer is Common.setImpliedPropertiesOnInstance
/**
 * When a sliced element has a minimum greater than 0, some or all of those required elements may exist in the form
 * of reslices; the "effective minimum" of the sliced element is then lower than its actual minimum. The slice tree
 * organizes the slices (or reslices) of an element: each node is an ElementDefinition, its children its slices.
 */
public final class SliceTree {
  private SliceTree() {}

  public static final class SliceNode {
    public final ElementDefinition element;
    public final List<SliceNode> children = new ArrayList<>();
    /** JS number: NaN when the element has no min. */
    public double count;

    SliceNode(ElementDefinition element) {
      this.element = element;
    }
  }

  public static SliceNode buildSliceTree(ElementDefinition parent) {
    SliceNode root = new SliceNode(parent);
    for (ElementDefinition slice : parent.getSlices()) {
      insertIntoSliceTree(root, slice);
    }
    return root;
  }

  private static void insertIntoSliceTree(SliceNode parent, ElementDefinition elementToAdd) {
    SliceNode nextParent = null;
    for (SliceNode child : parent.children) {
      if (elementToAdd.getSliceName().startsWith(child.element.getSliceName() + "/")) {
        nextParent = child;
        break;
      }
    }
    if (nextParent != null) {
      insertIntoSliceTree(nextParent, elementToAdd);
    } else {
      parent.children.add(new SliceNode(elementToAdd));
    }
  }

  public static void calculateSliceTreeCounts(SliceNode node, Map<String, Integer> knownSlices, String keyStart) {
    for (SliceNode child : node.children) calculateSliceTreeCounts(child, knownSlices, keyStart);
    double sum = 0;
    for (SliceNode child : node.children) sum += getSliceTreeSum(child);
    Double min = node.element.getMin();
    double elementMin = (min == null ? Double.NaN : min) - sum;
    String[] idParts = node.element.getId().split("\\.", -1);
    String slicePath = keyStart
        + idParts[idParts.length - 1].replaceFirst(":(.*)$", "[$1]").replace("/", "][");
    double sliceMin = knownSlices.containsKey(slicePath) ? knownSlices.get(slicePath) : 0;
    // Math.max: NaN if either is NaN
    node.count = Double.isNaN(elementMin) || Double.isNaN(sliceMin) ? Double.NaN : Math.max(elementMin, sliceMin);
  }

  private static double getSliceTreeSum(SliceNode node) {
    double sum = node.count;
    for (SliceNode child : node.children) sum += getSliceTreeSum(child);
    return sum;
  }
}

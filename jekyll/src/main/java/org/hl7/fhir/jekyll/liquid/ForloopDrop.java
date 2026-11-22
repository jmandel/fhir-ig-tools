package org.hl7.fhir.jekyll.liquid;

/** Port of liquid-4.0.4/lib/liquid/forloop_drop.rb. */
public class ForloopDrop extends LiquidDrop {
  private final String name;
  private final long length;
  private final Object parentloop;
  private long index;

  public ForloopDrop(String name, long length, Object parentloop) {
    this.name = name;
    this.length = length;
    this.parentloop = parentloop;
    this.index = 0;
  }

  @Override
  protected Object invokeMethod(String m) {
    switch (m) {
      case "name": return name;
      case "length": return length;
      case "parentloop": return parentloop;
      case "index": return index + 1;
      case "index0": return index;
      case "rindex": return length - index;
      case "rindex0": return length - index - 1;
      case "first": return index == 0;
      case "last": return index == length - 1;
      default: return NO_METHOD;
    }
  }

  public void increment() {
    index += 1;
  }
}

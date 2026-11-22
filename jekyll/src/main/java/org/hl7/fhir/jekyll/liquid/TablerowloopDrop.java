package org.hl7.fhir.jekyll.liquid;

/** Port of liquid-4.0.4/lib/liquid/tablerowloop_drop.rb. */
public class TablerowloopDrop extends LiquidDrop {
  private final long length;
  private long row = 1;
  private long col = 1;
  private final long cols;
  private long index = 0;

  public TablerowloopDrop(long length, long cols) {
    this.length = length;
    this.cols = cols;
  }

  public long col() {
    return col;
  }

  public long row() {
    return row;
  }

  public boolean colLast() {
    return col == cols;
  }

  public boolean last() {
    return index == length - 1;
  }

  @Override
  protected Object invokeMethod(String m) {
    switch (m) {
      case "length": return length;
      case "col": return col;
      case "row": return row;
      case "index": return index + 1;
      case "index0": return index;
      case "col0": return col - 1;
      case "rindex": return length - index;
      case "rindex0": return length - index - 1;
      case "first": return index == 0;
      case "last": return index == length - 1;
      case "col_first": return col == 1;
      case "col_last": return col == cols;
      default: return NO_METHOD;
    }
  }

  public void increment() {
    index += 1;
    if (col == cols) {
      col = 1;
      row += 1;
    } else {
      col += 1;
    }
  }
}

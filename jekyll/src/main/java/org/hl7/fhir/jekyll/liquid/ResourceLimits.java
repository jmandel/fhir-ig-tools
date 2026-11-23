package org.hl7.fhir.jekyll.liquid;

/** Port of liquid-4.0.4/lib/liquid/resource_limits.rb. Limits are null (off) by default. */
public class ResourceLimits {
  public long renderLength;
  public long renderScore;
  public long assignScore;
  public Long renderLengthLimit;
  public Long renderScoreLimit;
  public Long assignScoreLimit;

  public ResourceLimits() {
    reset();
  }

  public boolean reached() {
    return (renderLengthLimit != null && renderLength > renderLengthLimit)
        || (renderScoreLimit != null && renderScore > renderScoreLimit)
        || (assignScoreLimit != null && assignScore > assignScoreLimit);
  }

  public final void reset() {
    renderLength = renderScore = assignScore = 0;
  }
}

package org.hl7.fhir.jekyll.liquid.tags;

import org.hl7.fhir.jekyll.liquid.Block;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.Tokenizer;

/** Port of liquid-4.0.4/lib/liquid/tags/comment.rb. */
public class Comment extends Block {
  public Comment(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
  }

  @Override
  public Object render(Context context) {
    return "";
  }

  @Override
  public void unknownTag(String tag, String markup, Tokenizer tokens) {}

  @Override
  public boolean isBlank() {
    return true;
  }
}

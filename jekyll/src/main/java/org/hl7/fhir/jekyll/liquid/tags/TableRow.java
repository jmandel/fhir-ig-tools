package org.hl7.fhir.jekyll.liquid.tags;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.liquid.Block;
import org.hl7.fhir.jekyll.liquid.Context;
import org.hl7.fhir.jekyll.liquid.Expression;
import org.hl7.fhir.jekyll.liquid.Liquid;
import org.hl7.fhir.jekyll.liquid.LiquidException;
import org.hl7.fhir.jekyll.liquid.ParseContext;
import org.hl7.fhir.jekyll.liquid.TablerowloopDrop;
import org.hl7.fhir.jekyll.liquid.Utils;
import org.hl7.fhir.jekyll.liquid.Values;

/** Port of liquid-4.0.4/lib/liquid/tags/table_row.rb. */
public class TableRow extends Block {
  static final Pattern Syntax = Pattern.compile("(\\w+)\\s+in\\s+(" + "(?:" + Liquid.QUOTED_FRAGMENT + ")" + "+)", Pattern.UNIX_LINES);

  private final String variableName;
  private final Object collectionName;
  private final Map<String, Object> attributes = new LinkedHashMap<>();

  public TableRow(String tagName, String markup, ParseContext options) {
    super(tagName, markup, options);
    Matcher m = Syntax.matcher(markup);
    if (m.find()) {
      variableName = m.group(1);
      collectionName = Expression.parse(m.group(2));
      for (String[] kv : Liquid.scanGroups(Liquid.TagAttributes, markup)) {
        attributes.put(kv[0], Expression.parse(kv[1]));
      }
    } else {
      throw LiquidException.syntax(options.locale().t("errors.syntax.table_row"));
    }
  }

  @Override
  public Object render(Context context) {
    Object collection = context.evaluate(collectionName);
    if (!Values.truthy(collection)) {
      return "";
    }
    long from = attributes.containsKey("offset") ? Values.toLong(context.evaluate(attributes.get("offset"))) : 0;
    Long to = attributes.containsKey("limit") ? from + Values.toLong(context.evaluate(attributes.get("limit"))) : null;
    List<Object> items = Utils.sliceCollection(collection, from, to);
    int length = items.size();
    long cols = Values.toLong(context.evaluate(attributes.get("cols")));
    StringBuilder result = new StringBuilder("<tr class=\"row1\">\n");
    context.stack(() -> {
      TablerowloopDrop tablerowloop = new TablerowloopDrop(length, cols);
      context.set("tablerowloop", tablerowloop);
      for (Object item : items) {
        context.set(variableName, item);
        result.append("<td class=\"col").append(tablerowloop.col()).append("\">").append(Values.toS(super.render(context))).append("</td>");
        if (tablerowloop.colLast() && !tablerowloop.last()) {
          result.append("</tr>\n<tr class=\"row").append(tablerowloop.row() + 1).append("\">");
        }
        tablerowloop.increment();
      }
      return null;
    });
    result.append("</tr>\n");
    return result.toString();
  }
}

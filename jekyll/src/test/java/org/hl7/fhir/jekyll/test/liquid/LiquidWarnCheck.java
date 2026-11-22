package org.hl7.fhir.jekyll.test.liquid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.util.Json;

/**
 * Extra checks DiffRunner doesn't do: warn-mode warnings (non-empty exactly when Ruby's are, and
 * the exact texts), and exact error class/message text.
 * usage: LiquidWarnCheck cases.jsonl expected.jsonl [--show N]
 */
public final class LiquidWarnCheck {
  private LiquidWarnCheck() {}

  @SuppressWarnings("unchecked")
  public static void main(String[] args) throws IOException {
    int show = args.length > 3 ? Integer.parseInt(args[3]) : 20;
    Map<String, Map<String, Object>> exp = new LinkedHashMap<>();
    for (String line : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
      if (!line.isBlank()) {
        Map<String, Object> e = (Map<String, Object>) Json.parse(line);
        exp.put((String) e.get("id"), e);
      }
    }
    int warnPresence = 0, warnPresenceFail = 0, warnText = 0, warnTextFail = 0, errText = 0, errTextFail = 0, renderErr = 0, renderErrFail = 0;
    int shown = 0;
    for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      Map<String, Object> c = (Map<String, Object>) Json.parse(line);
      if (!"liquid".equals(c.get("type"))) {
        continue;
      }
      Map<String, Object> want = exp.get(c.get("id"));
      Map<String, Object> got = LiquidCases.run(c);
      boolean ww = want.get("warnings") != null, gw = got.get("warnings") != null;
      if (ww == gw) {
        warnPresence++;
      } else {
        warnPresenceFail++;
      }
      List<Object> wl = (List<Object>) want.get("warnings"), gl = (List<Object>) got.get("warnings");
      boolean textOk = ww == gw && (!ww || wl.equals(gl));
      if (textOk) {
        warnText++;
      } else {
        warnTextFail++;
      }
      boolean errOk = String.valueOf(want.get("error")).equals(String.valueOf(got.get("error")));
      if (errOk) {
        errText++;
      } else {
        errTextFail++;
      }
      boolean reOk = String.valueOf(want.get("render_errors")).equals(String.valueOf(got.get("render_errors")));
      if (reOk) {
        renderErr++;
      } else {
        renderErrFail++;
      }
      if ((!textOk || !errOk || !reOk) && shown++ < show) {
        System.out.println("DIFF " + c.get("id") + (ww != gw ? " [WARNING PRESENCE]" : ""));
        if (!textOk) {
          System.out.println("  want warnings: " + wl);
          System.out.println("  got  warnings: " + gl);
        }
        if (!errOk) {
          System.out.println("  want error: " + want.get("error"));
          System.out.println("  got  error: " + got.get("error"));
        }
        if (!reOk) {
          System.out.println("  want render_errors: " + want.get("render_errors"));
          System.out.println("  got  render_errors: " + got.get("render_errors"));
        }
      }
    }
    System.out.printf("warnings presence: %d ok, %d fail; warning text: %d ok, %d fail; error text: %d ok, %d fail; render_errors text: %d ok, %d fail%n",
        warnPresence, warnPresenceFail, warnText, warnTextFail, errText, errTextFail, renderErr, renderErrFail);
    System.exit(warnPresenceFail == 0 ? 0 : 1);
  }
}

package org.hl7.fhir.sushi.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

// port of sushi/src/utils/puns.ts (SUSHI 3.21.1). The lists are generated verbatim from the TS source.
/** The summary-box pun. Random by design (lodash sample); differential runs drop the pun line (PORTING.md D7). */
public final class Puns {
  private Puns() {}

  /** Source of randomness for {@link #getRandomPun}; tests may replace it. */
  public static RandomGenerator random = RandomGenerator.getDefault();

  static final List<String> CLEAN_RUN_PUNS = List.of(
      "That went swimmingly!",
      "O-fish-ally error free!",
      "Cool and So-fish-ticated!",
      "Well hooked and landed!",
      "You earned a PhD in Ichthyology!",
      "You rock, lobster!",
      "Everything is ship-shape!",
      "Ex-clam-ation point!",
      "Ac-clam-ations!",
      "Fin-tastic job!",
      "You are dolphinitely doing great!",
      "It doesn't get any betta than this!",
      "You're piranha roll now!",
      "Valedictorian of the School of FSH!",
      "FSHing for compliments? Super job!",
      "Tank you very much!",
      "Why pun? Just for for the halibut.",
      "You've proven any-fin is possible.",
      "You swim with the sharks!",
      "You've found your sea legs.",
      "You really made a splash!",
      "You've earned a seal of approval.",
      "You're swimming in success!",
      "You're making waves now!",
      "You've turned the tide.",
      "You're catching on now!",
      "You're in the swim of things now!",
      "Catch of the day, right here!",
      "That was a big fish to fry!",
      "Nice job! Are you hooked yet?",
      "That was a sea breeze!",
      "A whale of a job!",
      "Just like a perfect pearl.",
      "You're sailing along smoothly.",
      "Swish! Nothing but fishnet.");

  static final List<String> WARNING_PUNS = List.of(
      "Not bad, but you cod do batter!",
      "Something smells fishy...",
      "Warnings... Water those about?",
      "A bit pitchy, but tuna-ble.",
      "Do you sea the problem?",
      "You are skating on fin ice.",
      "You should mullet over.",
      "You might need some Vitamin Sea",
      "Maybe you should scale back",
      "Whale, whale, what happened here?",
      "Did you do this on porpoise?",
      "Let minnow if you need some help",
      "You're kraken me up.",
      "Don't be koi about asking for help.",
      "You seem a bit rudderless.",
      "Keep swimming, Dory.",
      "You should gut it out.",
      "Don't get stuck in the doldrums.",
      "Are you in a no-wind situation?",
      "Sailors, take warning!",
      "You seem to be casting about.",
      "This looks a bit fishy.");

  static final List<String> ERROR_PUNS = List.of(
      "Ick! Errors!",
      "Some-fin went wrong...",
      "Unfor-tuna-tely, there are errors.",
      "That really smelt.",
      "You've spawned some errors.",
      "This is the one that got away.",
      "The docs might be bene-fish-al.",
      "This was a turtle disaster.",
      "Something went eely wrong there.",
      "Documentation may be kelp-ful.",
      "You should do some sole searching.",
      "Call a FSH sturgeon!",
      "This is giving me a haddock.",
      "You whaley need to turn this around.",
      "Swim back to School of FSH",
      "You're krilling me!",
      "You're drowning here.",
      "Having a sinking feeling?",
      "That was 'crab-tastic'.",
      "Looks like you're floundering",
      "Don't clam up, ask for help.",
      "Are you lost at sea?",
      "That was a shipwreck!",
      "You are off course, sailor!",
      "You're sailing rough seas.",
      "Don't be shell-shocked.",
      "You're up to urchin in mistakes.",
      "Is that squall you got?",
      "You're in deep water now.",
      "Don't carp about your mistakes.",
      "This run went belly up.",
      "Seas the errors and fix them!",
      "Dolphinately not the desired outcome.",
      "Looks like you've caught a bugfish.");

  public static String getRandomPun(int numErrors, int numWarnings) {
    List<String> puns = numErrors > 0 ? ERROR_PUNS : numWarnings > 0 ? WARNING_PUNS : CLEAN_RUN_PUNS;
    return puns.get(random.nextInt(puns.size()));
  }

  public static String getRandomPun() {
    return getRandomPun(0, 0);
  }

  public static List<String> getPuns(boolean includeErrors, boolean includeWarnings, boolean includeClean) {
    List<String> puns = new ArrayList<>();
    if (includeErrors) puns.addAll(ERROR_PUNS);
    if (includeWarnings) puns.addAll(WARNING_PUNS);
    if (includeClean) puns.addAll(CLEAN_RUN_PUNS);
    return puns;
  }

  public static List<String> getPuns() {
    return getPuns(true, true, true);
  }
}

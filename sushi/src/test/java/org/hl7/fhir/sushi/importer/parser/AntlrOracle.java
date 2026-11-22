package org.hl7.fhir.sushi.importer.parser;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The test-scope oracle: the ANTLR 4.13.2 Java parser generated from SUSHI's unmodified grammar
 * ({@code importer.antlr}), configured to behave like the antlr4 JS runtime 4.13.2 that SUSHI runs on, and converted
 * to this package's CST. JS-runtime behaviours reproduced here (each checked against the JS sources):
 * <ul>
 * <li>UTF-16 input indexing (JS InputStream without code point decoding) = ANTLRInputStream, not CharStreams;</li>
 * <li>lexer "token recognition error at: '...'" shows the raw text (JS getErrorDisplay does not escape \n\t\r);</li>
 * <li>recoverInline throws a plain InputMismatchException (Java 4.13 reports the expected tokens of the last
 *     sync'd state instead);</li>
 * <li>a token conjured by single-token insertion has the text {@code "<missing " + literalNames[type] + ">"}, i.e.
 *     {@code <missing null>} for tokens without a literal (Java: the display name);</li>
 * <li>exitRule always sets {@code ctx.stop = LT(-1)} (Java uses the EOF token once EOF was matched);</li>
 * <li>BufferedTokenStream.getText(interval) lexes the whole remaining input first (JS fill(); Java syncs to stop),
 *     which decides where lexer errors appear among parser errors.</li>
 * </ul>
 */
public final class AntlrOracle {
  private AntlrOracle() {}

  /** One parse: the CST plus all tokens (all channels) in stream order. */
  public record Result<T extends ParserRuleContext>(T tree, List<Token> tokens) {}

  public static Result<FSHParser.DocContext> parseDoc(String input, ErrorListener listener) {
    InputText text = new InputText(input);
    CharStream chars = new ANTLRInputStream(input);
    org.hl7.fhir.sushi.importer.antlr.FSHLexer lexer = new org.hl7.fhir.sushi.importer.antlr.FSHLexer(chars) {
      @Override public String getErrorDisplay(String s) { return s; }
    };
    JsTokenStream tokens = new JsTokenStream(lexer);
    org.hl7.fhir.sushi.importer.antlr.FSHParser parser = new org.hl7.fhir.sushi.importer.antlr.FSHParser(tokens) {
      @Override public void exitRule() {
        matchedEOF = false;
        super.exitRule();
      }
    };
    Converter conv = new Converter(text, tokens);
    wire(lexer, parser, listener, conv);
    org.antlr.v4.runtime.ParserRuleContext tree = parser.doc();
    return new Result<>((FSHParser.DocContext) conv.convertTree(tree, null, FSHParser::newContext), conv.allTokens());
  }

  public static Result<MiniFSHParser.RuleSetContext> parseMini(String input, ErrorListener listener) {
    InputText text = new InputText(input);
    CharStream chars = new ANTLRInputStream(input);
    org.hl7.fhir.sushi.importer.antlr.MiniFSHLexer lexer = new org.hl7.fhir.sushi.importer.antlr.MiniFSHLexer(chars) {
      @Override public String getErrorDisplay(String s) { return s; }
    };
    JsTokenStream tokens = new JsTokenStream(lexer);
    org.hl7.fhir.sushi.importer.antlr.MiniFSHParser parser =
        new org.hl7.fhir.sushi.importer.antlr.MiniFSHParser(tokens) {
          @Override public void exitRule() {
            matchedEOF = false;
            super.exitRule();
          }
        };
    Converter conv = new Converter(text, tokens);
    wire(lexer, parser, listener, conv);
    org.antlr.v4.runtime.ParserRuleContext tree = parser.ruleSet();
    return new Result<>((MiniFSHParser.RuleSetContext) conv.convertTree(tree, null, MiniFSHParser::newContext),
        conv.allTokens());
  }

  private static void wire(org.antlr.v4.runtime.Lexer lexer, Parser parser, ErrorListener listener, Converter conv) {
    parser.setErrorHandler(new JsErrorStrategy());
    lexer.removeErrorListeners();
    parser.removeErrorListeners();
    Recognizer lexerRec = List::of;
    Recognizer parserRec = conv::fetchedTokens;
    BaseErrorListener bridge = new BaseErrorListener() {
      @Override
      public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> r, Object sym, int line, int col, String msg,
          RecognitionException e) {
        if (r instanceof org.antlr.v4.runtime.Lexer) {
          listener.syntaxError(lexerRec, null, line, col, msg);
        } else {
          listener.syntaxError(parserRec, conv.token((org.antlr.v4.runtime.Token) sym), line, col, msg);
        }
      }
    };
    lexer.addErrorListener(bridge);
    parser.addErrorListener(bridge);
  }

  /** JS BufferedTokenStream.getText(interval) calls fill() first. */
  static final class JsTokenStream extends CommonTokenStream {
    JsTokenStream(TokenSource src) { super(src); }

    @Override public String getText(Interval interval) {
      lazyInit();
      fill();
      return super.getText(interval);
    }
  }

  /** The JS runtime's DefaultErrorStrategy where it differs from Java's. */
  static final class JsErrorStrategy extends DefaultErrorStrategy {
    @Override public org.antlr.v4.runtime.Token recoverInline(Parser recognizer) throws RecognitionException {
      org.antlr.v4.runtime.Token matchedSymbol = singleTokenDeletion(recognizer);
      if (matchedSymbol != null) {
        recognizer.consume();
        return matchedSymbol;
      }
      if (singleTokenInsertion(recognizer)) {
        return getMissingSymbol(recognizer);
      }
      throw new InputMismatchException(recognizer);
    }

    @Override protected org.antlr.v4.runtime.Token getMissingSymbol(Parser recognizer) {
      org.antlr.v4.runtime.Token currentSymbol = recognizer.getCurrentToken();
      IntervalSet expecting = getExpectedTokens(recognizer);
      int expectedTokenType = expecting.isNil() ? org.antlr.v4.runtime.Token.INVALID_TYPE : expecting.getMinElement();
      String tokenText;
      if (expectedTokenType == org.antlr.v4.runtime.Token.EOF) {
        tokenText = "<missing EOF>";
      } else {
        // JS: literalNames[type] -- null inside the array, undefined past its end
        String[] lits = recognizer instanceof org.hl7.fhir.sushi.importer.antlr.MiniFSHParser
            ? MiniFSHParser.literalNames : FSHParser.literalNames;
        String name = expectedTokenType >= 0 && expectedTokenType < lits.length ? String.valueOf(lits[expectedTokenType])
            : "undefined";
        tokenText = "<missing " + name + ">";
      }
      org.antlr.v4.runtime.Token current = currentSymbol;
      org.antlr.v4.runtime.Token lookback = recognizer.getInputStream().LT(-1);
      if (current.getType() == org.antlr.v4.runtime.Token.EOF && lookback != null) {
        current = lookback;
      }
      return recognizer.getTokenFactory().create(
          new Pair<>(current.getTokenSource(), current.getTokenSource().getInputStream()), expectedTokenType,
          tokenText, org.antlr.v4.runtime.Token.DEFAULT_CHANNEL, -1, -1, current.getLine(),
          current.getCharPositionInLine());
    }
  }

  interface ContextFactory {
    ParserRuleContext create(int ruleIndex, ParserRuleContext parent, int invokingState);
  }

  /** Converts ANTLR tokens/trees to this package's CST, preserving token identity. */
  static final class Converter {
    private final InputText text;
    private final CommonTokenStream stream;
    private final List<Token> converted = new ArrayList<>();
    private final Map<org.antlr.v4.runtime.Token, Token> conjured = new IdentityHashMap<>();

    Converter(InputText text, CommonTokenStream stream) {
      this.text = text;
      this.stream = stream;
    }

    private void syncConverted() {
      List<org.antlr.v4.runtime.Token> all = stream.getTokens();
      for (int i = converted.size(); i < all.size(); i++) converted.add(convertNew(all.get(i)));
    }

    private Token convertNew(org.antlr.v4.runtime.Token t) {
      // the text is computed from the input like JS CommonToken.text (Java's would agree; this checks it)
      Token mine = new Token(text, t.getType(), t.getTokenIndex() < 0 ? t.getText() : null, t.getChannel(),
          t.getStartIndex(), t.getStopIndex(), t.getLine(), t.getCharPositionInLine());
      mine.setTokenIndex(t.getTokenIndex());
      if (t.getTokenIndex() >= 0 && !mine.getText().equals(t.getText())) {
        throw new IllegalStateException("token text mismatch " + t + " vs " + mine);
      }
      return mine;
    }

    List<Token> fetchedTokens() {
      syncConverted();
      return converted;
    }

    List<Token> allTokens() {
      syncConverted();
      return List.copyOf(converted);
    }

    Token token(org.antlr.v4.runtime.Token t) {
      if (t == null) return null;
      if (t.getTokenIndex() < 0 || !(t instanceof CommonToken)) {
        return conjured.computeIfAbsent(t, this::convertNew);
      }
      syncConverted();
      return converted.get(t.getTokenIndex());
    }

    ParserRuleContext convertTree(org.antlr.v4.runtime.ParserRuleContext in, ParserRuleContext parent,
        ContextFactory factory) {
      ParserRuleContext out = factory.create(in.getRuleIndex(), parent, in.invokingState);
      out.start = token(in.start);
      out.stop = token(in.stop);
      if (in.exception != null) {
        org.hl7.fhir.sushi.importer.parser.RecognitionException.Kind k = in.exception instanceof NoViableAltException
            ? org.hl7.fhir.sushi.importer.parser.RecognitionException.Kind.NO_VIABLE_ALT
            : org.hl7.fhir.sushi.importer.parser.RecognitionException.Kind.INPUT_MISMATCH;
        out.exception = new org.hl7.fhir.sushi.importer.parser.RecognitionException(k,
            token(in.exception.getOffendingToken()));
      }
      if (in.children != null) {
        for (ParseTree c : in.children) {
          if (c instanceof ErrorNode e) {
            out.addErrorNode(token(e.getSymbol()));
          } else if (c instanceof TerminalNode t) {
            out.addTokenNode(token(t.getSymbol()));
          } else {
            out.addChild(convertTree((org.antlr.v4.runtime.ParserRuleContext) c, out, factory));
          }
        }
      }
      return out;
    }
  }
}

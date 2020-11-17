package net.alagris.thrax;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.LexerSkipAction;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.ibm.icu.impl.locale.UnicodeLocaleExtension;

import net.alagris.ThraxGrammarListener;
import net.alagris.ThraxGrammarParser;
import net.alagris.LexUnicodeSpecification.E;
import net.alagris.LexUnicodeSpecification.P;
import net.alagris.CompilationError;
import net.alagris.GrammarLexer;
import net.alagris.GrammarParser;
import net.alagris.IntSeq;
import net.alagris.IntermediateGraph;
import net.alagris.LexUnicodeSpecification;
import net.alagris.Pos;
import net.alagris.Specification;
import net.alagris.Specification.NullTermIter;
import net.alagris.Specification.Range;
import net.alagris.ThraxGrammarLexer;
import net.alagris.ThraxGrammarParser.Atomic_objContext;
import net.alagris.ThraxGrammarParser.DQuoteStringContext;
import net.alagris.ThraxGrammarParser.FstWithCompositionContext;
import net.alagris.ThraxGrammarParser.FstWithConcatContext;
import net.alagris.ThraxGrammarParser.FstWithDiffContext;
import net.alagris.ThraxGrammarParser.FstWithKleeneContext;
import net.alagris.ThraxGrammarParser.FstWithOutputContext;
import net.alagris.ThraxGrammarParser.FstWithRangeContext;
import net.alagris.ThraxGrammarParser.FstWithUnionContext;
import net.alagris.ThraxGrammarParser.FstWithWeightContext;
import net.alagris.ThraxGrammarParser.FstWithoutCompositionContext;
import net.alagris.ThraxGrammarParser.FstWithoutConcatContext;
import net.alagris.ThraxGrammarParser.FstWithoutDiffContext;
import net.alagris.ThraxGrammarParser.FstWithoutOutputContext;
import net.alagris.ThraxGrammarParser.FstWithoutUnionContext;
import net.alagris.ThraxGrammarParser.FstWithoutWeightContext;
import net.alagris.ThraxGrammarParser.FuncCallContext;
import net.alagris.ThraxGrammarParser.Func_argumentsContext;
import net.alagris.ThraxGrammarParser.Funccall_argumentsContext;
import net.alagris.ThraxGrammarParser.NestedContext;
import net.alagris.ThraxGrammarParser.SQuoteStringContext;
import net.alagris.ThraxGrammarParser.StartContext;
import net.alagris.ThraxGrammarParser.StmtFuncContext;
import net.alagris.ThraxGrammarParser.StmtImportContext;
import net.alagris.ThraxGrammarParser.StmtReturnContext;
import net.alagris.ThraxGrammarParser.StmtVarDefContext;
import net.alagris.ThraxGrammarParser.Stmt_listContext;
import net.alagris.ThraxGrammarParser.VarContext;

public class ThraxParser<N, G extends IntermediateGraph<Pos, E, P, N>> implements ThraxGrammarListener {

	final LexUnicodeSpecification<N, G> specs;
	final Str EPSILON = new StrImpl(IntSeq.Epsilon);
	final Var NONDETERMINISM_ERROR = new Var("NONDETERMINISM_ERROR");
	final LinkedHashMap<String, RE> vars = new LinkedHashMap<>();
	final Stack<RE> res = new Stack<>();
	/** id of the variable currently being built */
	public String id;

	public int numberOfSubVariablesCreated = 0;

	public ThraxParser(LexUnicodeSpecification<N, G> specs) {
		this.specs = specs;
	}

	static class SerializationContext {
		final HashMap<String, Integer> consumedUsages;

		public SerializationContext(HashMap<String, Integer> usages) {
			consumedUsages = new HashMap<>(usages);
		}
	}

	public HashMap<String, Integer> countUsages() {
		final HashMap<String, Integer> usages = new HashMap<>(vars.size());
		for (RE re : vars.values()) {
			re.countUsages(usages);
		}
		return usages;
	}

	public String toSolomonoff() {
		final StringBuilder sb = new StringBuilder();
		toSolomonoff(sb, new SerializationContext(countUsages()));
		return sb.toString();
	}

	public void toSolomonoff(StringBuilder sb, SerializationContext ctx) {
		for (Entry<String, RE> e : vars.entrySet()) {
			sb.append(e.getKey()).append(" = ");
			e.getValue().toSolomonoff(sb, ctx);
			sb.append("\n");
		}
	}

	static interface RE {
		void toSolomonoff(StringBuilder sb, SerializationContext ctx);

		int precedenceLevel();

		void countUsages(HashMap<String, Integer> usages);
	}

	static class Union implements RE {
		final RE lhs, rhs;

		public Union(RE lhs, RE rhs) {
			this.lhs = lhs;
			this.rhs = rhs;
		}

		@Override
		public int precedenceLevel() {
			return 4;
		}

		@Override
		public void toSolomonoff(StringBuilder sb, SerializationContext ctx) {
			lhs.toSolomonoff(sb, ctx);
			sb.append("|");
			rhs.toSolomonoff(sb, ctx);
		}

		@Override
		public void countUsages(HashMap<String, Integer> usages) {
			lhs.countUsages(usages);
			rhs.countUsages(usages);
		}
	}

	static class Concat implements RE {
		final RE lhs, rhs;

		public Concat(RE lhs, RE rhs) {
			this.lhs = lhs;
			this.rhs = rhs;
		}

		@Override
		public void toSolomonoff(StringBuilder sb, SerializationContext ctx) {
			if (lhs.precedenceLevel() > precedenceLevel()) {
				sb.append("(");
				lhs.toSolomonoff(sb, ctx);
				sb.append(")");
			} else {
				lhs.toSolomonoff(sb, ctx);
			}
			sb.append(" ");
			if (rhs.precedenceLevel() > precedenceLevel()) {
				sb.append("(");
				rhs.toSolomonoff(sb, ctx);
				sb.append(")");
			} else {
				rhs.toSolomonoff(sb, ctx);
			}
		}

		@Override
		public int precedenceLevel() {
			return 3;
		}

		@Override
		public void countUsages(HashMap<String, Integer> usages) {
			lhs.countUsages(usages);
			rhs.countUsages(usages);
		}
	}

	static class Kleene implements RE {
		final RE re;
		static final char ZERO_OR_MORE = '*';
		static final char ONE_OR_MORE = '+';
		static final char ZERO_OR_ONE = '?';
		final char type;

		public Kleene(RE re, char type) {
			this.re = re;
			this.type = type;
		}

		@Override
		public int precedenceLevel() {
			return 2;
		}

		@Override
		public void toSolomonoff(StringBuilder sb, SerializationContext ctx) {
			if (re.precedenceLevel() > precedenceLevel()) {
				sb.append("(");
				re.toSolomonoff(sb, ctx);
				sb.append(")");
			} else {
				re.toSolomonoff(sb, ctx);
			}
			sb.append(type);
		}

		@Override
		public void countUsages(HashMap<String, Integer> usages) {
			re.countUsages(usages);
		}
	}

	static class Output implements RE {
		final RE re;
		final IntSeq out;

		public Output(RE re, IntSeq out) {
			this.re = re;
			this.out = out;
		}

		@Override
		public int precedenceLevel() {
			return 1;
		}

		@Override
		public void toSolomonoff(StringBuilder sb, SerializationContext ctx) {
			if (re.precedenceLevel() > precedenceLevel()) {
				sb.append("(");
				re.toSolomonoff(sb, ctx);
				sb.append(")");
			} else {
				re.toSolomonoff(sb, ctx);
			}
			sb.append(":").append(out.toStringLiteral());
		}

		@Override
		public void countUsages(HashMap<String, Integer> usages) {
			re.countUsages(usages);
		}
	}

	static class Compose implements RE {
		final RE lhs, rhs;

		public Compose(RE lhs, RE rhs) {
			this.lhs = lhs;
			this.rhs = rhs;
		}

		@Override
		public int precedenceLevel() {
			return 0;
		}

		@Override
		public void toSolomonoff(StringBuilder sb, SerializationContext ctx) {
			sb.append("compose[");
			lhs.toSolomonoff(sb, ctx);
			sb.append(",");
			rhs.toSolomonoff(sb, ctx);
			sb.append("]");
		}

		@Override
		public void countUsages(HashMap<String, Integer> usages) {
			lhs.countUsages(usages);
			rhs.countUsages(usages);
		}
	}

	static class Diff implements RE {
		final RE lhs, rhs;

		public Diff(RE lhs, RE rhs) {
			this.lhs = lhs;
			this.rhs = rhs;
		}

		@Override
		public int precedenceLevel() {
			return 0;
		}

		@Override
		public void toSolomonoff(StringBuilder sb, SerializationContext ctx) {
			sb.append("subtract[");
			lhs.toSolomonoff(sb, ctx);
			sb.append(",");
			rhs.toSolomonoff(sb, ctx);
			sb.append("]");
		}

		@Override
		public void countUsages(HashMap<String, Integer> usages) {
			lhs.countUsages(usages);
			rhs.countUsages(usages);
		}
	}

	class Char implements Set, Str {
		final int character;

		public Char(int character) {
			this.character = character;
		}

		@Override
		public IntSeq str() {
			return new IntSeq(character);
		}

		@Override
		public ArrayList<Range<Integer, Boolean>> ranges() {
			return specs.makeSingletonRanges(true, false, character - 1, character);
		}

		@Override
		public int precedenceLevel() {
			return 0;
		}

		@Override
		public void toSolomonoff(StringBuilder sb, SerializationContext ctx) {
			sb.append(str().toStringLiteral());
		}

		@Override
		public void countUsages(HashMap<String, Integer> usages) {
		}
	}

	static interface Set extends RE {
		ArrayList<Range<Integer, Boolean>> ranges();
	}

	class SetImpl implements Set {
		final ArrayList<Range<Integer, Boolean>> ranges;

		public SetImpl() {
			this(specs.makeEmptyRanges(false));
		}

		public SetImpl(ArrayList<Range<Integer, Boolean>> ranges) {
			this.ranges = ranges;
		}

		public SetImpl(ArrayList<Range<Integer, Boolean>> lhs, ArrayList<Range<Integer, Boolean>> rhs,
				BiFunction<Boolean, Boolean, Boolean> f) {
			final NullTermIter<Specification.RangeImpl<Integer, Boolean>> i = specs.zipTransitionRanges(
					Specification.fromIterable(lhs), Specification.fromIterable(rhs),
					(from,to, lhsTran, rhsTran) -> new Specification.RangeImpl<>(to, f.apply(lhsTran, rhsTran)));
			this.ranges = new ArrayList<>(lhs.size() + rhs.size());
			Specification.RangeImpl<Integer, Boolean> next;
			Specification.RangeImpl<Integer, Boolean> prev = null;
			while ((next = i.next()) != null) {
				if (prev != null && !prev.edges().equals(next.edges())) {
					ranges.add(prev);
				}
				prev = next;
			}
			assert prev != null;
			assert prev.input().equals(specs.maximal()) : prev.input() + "==" + specs.maximal();
			ranges.add(prev);
		}

		@Override
		public ArrayList<Range<Integer, Boolean>> ranges() {
			return ranges;
		}

		@Override
		public int precedenceLevel() {
			return 0;
		}

		@Override
		public void toSolomonoff(StringBuilder sb, SerializationContext ctx) {
			int prev;
			int i;
			if (ranges.get(0).edges()) {
				prev = specs.minimal();
				i = 0;
			} else {
				prev = ranges.get(0).input();
				i = 1;
			}
			boolean hadFirst = false;
			for (; i < ranges.size(); i++) {
				final Range<Integer, Boolean> included = ranges.get(i);
				assert included.edges() : ranges.toString() + " " + i;
				if (hadFirst) {
					sb.append("|");
				} else {
					hadFirst = true;
				}
				final int fromInclusive = specs.successor(prev);
				final int toInclusive = included.input();
				if (IntSeq.isPrintableChar(fromInclusive) && IntSeq.isPrintableChar(toInclusive)) {
					if (fromInclusive == toInclusive) {
						sb.append("'");
						IntSeq.appendPrintableChar(sb, fromInclusive);
						sb.append("'");
					} else {
						sb.append("[");
						IntSeq.appendPrintableChar(sb, fromInclusive);
						sb.append("-");
						IntSeq.appendPrintableChar(sb, toInclusive);
						sb.append("]");
					}
				} else {
					if (fromInclusive == toInclusive) {
						sb.append("<");
						sb.append(fromInclusive);
						sb.append(">");
					} else {
						sb.append("<");
						sb.append(fromInclusive);
						sb.append("-");
						sb.append(toInclusive);
						sb.append(">");
					}
				}

				i++;
				if (i < ranges.size()) {
					final Range<Integer, Boolean> excluded = ranges.get(i);
					assert !excluded.edges() : ranges.toString();
					prev = excluded.input();
				} else {
					break;
				}
			}

		}

		@Override
		public void countUsages(HashMap<String, Integer> usages) {
		}
	}

	static interface Str extends RE {
		IntSeq str();
	}

	public RE parseLiteral(String str) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 1; i < str.length() - 1; i++) {// first and last are " characters
			final char c = str.charAt(i);
			if (c == '[') { // parse Thrax's special codes like "[32][0x20][040]"
				final int beginNum;
				final int base;
				if (str.charAt(i + 1) == '0') {
					if (str.charAt(i + 2) == 'x') {
						base = 16;
						beginNum = i + 3;// hex notation
					} else if (str.charAt(i + 2) == ']') {// special case of [0]
						base = 10;// decimal zero
						beginNum = i + 1;
					} else {
						base = 8;// oct notation
						beginNum = i + 2;
					}
				} else {
					base = 10;// decimal notation
					beginNum = i + 1;
				}
				int endNum = beginNum + 1;
				while (endNum < str.length() - 1 && str.charAt(endNum) != ']')
					endNum++;
				sb.append((char) Integer.parseInt(str.substring(beginNum, endNum), base));
				i = endNum;
			} else if (c == '\\') {
				i++;
				sb.append(str.charAt(i));
			} else {
				sb.append(c);
			}
		}
		if (sb.codePointCount(0, sb.length()) == 1) {
			return new Char(sb.codePointAt(0));
		} else {
			return new StrImpl(new IntSeq(sb.toString()));
		}
	}

	static class StrImpl implements Str {
		final IntSeq str;

		public StrImpl(IntSeq str) {
			this.str = str;
		}

		@Override
		public IntSeq str() {
			return str;
		}

		@Override
		public void toSolomonoff(StringBuilder sb, SerializationContext ctx) {
			sb.append(str.toStringLiteral());
		}

		@Override
		public int precedenceLevel() {
			return 0;
		}

		@Override
		public void countUsages(HashMap<String, Integer> usages) {
		}
	}

	static class Var implements RE {
		final String id;

		public Var(String id) {
			this.id = id;
		}

		@Override
		public void toSolomonoff(StringBuilder sb, SerializationContext ctx) {
			final int usagesLeft = ctx.consumedUsages.computeIfPresent(id, (k, v) -> v - 1);
			if (usagesLeft > 0)
				sb.append("!!");
			sb.append(id);
		}

		@Override
		public int precedenceLevel() {
			return 0;
		}

		@Override
		public void countUsages(HashMap<String, Integer> usages) {
			usages.compute(id, (k, v) -> v == null ? 1 : v + 1);
		}
	}

	@Override
	public void visitTerminal(TerminalNode node) {

	}

	@Override
	public void visitErrorNode(ErrorNode node) {

	}

	@Override
	public void enterEveryRule(ParserRuleContext ctx) {

	}

	@Override
	public void exitEveryRule(ParserRuleContext ctx) {
		// pass
	}

	@Override
	public void enterStmtReturn(StmtReturnContext ctx) {

	}

	@Override
	public void exitStmtReturn(StmtReturnContext ctx) {
		// TODO
	}

	@Override
	public void enterFstWithRange(FstWithRangeContext ctx) {

	}

	@Override
	public void exitFstWithRange(FstWithRangeContext ctx) {
		final RE re = res.pop();
		final int from, to;
		if (ctx.from == null) {
			from = to = Integer.parseInt(ctx.times.getText());
		} else {
			from = Integer.parseInt(ctx.from.getText());
			to = Integer.parseInt(ctx.to.getText());
		}
		if (to == 0 || from > to) {
			res.push(EPSILON);
		} else {
			final RE repeating;
			if (re instanceof Str) {
				repeating = re;
			} else {
				final String subID = "__" + id + "__" + (numberOfSubVariablesCreated++);
				vars.put(subID, re);
				repeating = new Var(subID);
			}
			RE repeated = repeating;
			for (int i = 1; i < from; i++) {
				repeated = concat(repeating, repeated);
			}
			for (int i = from; i < to; i++) {
				repeated = new Concat(repeated,new Kleene(repeating, Kleene.ZERO_OR_ONE));
			}
			
			res.push(repeated);
		}
	}

	@Override
	public void enterFstWithOutput(FstWithOutputContext ctx) {

	}

	@Override
	public void exitFstWithOutput(FstWithOutputContext ctx) {
		final RE r = res.pop();
		final RE l = res.pop();
		if (r instanceof Str) {
			res.push(new Output(l, ((Str) r).str()));
		} else {
			res.push(NONDETERMINISM_ERROR);
		}
	}

	@Override
	public void enterFstWithoutUnion(FstWithoutUnionContext ctx) {

	}

	@Override
	public void exitFstWithoutUnion(FstWithoutUnionContext ctx) {
		// pass
	}

	@Override
	public void enterFstWithUnion(FstWithUnionContext ctx) {

	}

	@Override
	public void exitFstWithUnion(FstWithUnionContext ctx) {
		final RE r = res.pop();
		final RE l = res.pop();
		if (l instanceof Set && r instanceof Set) {
			res.push(new SetImpl(((Set) l).ranges(), ((Set) r).ranges(), (a, b) -> a || b));
		} else {
			res.push(new Union(l, r));
		}
	}

	@Override
	public void enterFstWithoutComposition(FstWithoutCompositionContext ctx) {

	}

	@Override
	public void exitFstWithoutComposition(FstWithoutCompositionContext ctx) {
		// pass
	}

	@Override
	public void enterStmtImport(StmtImportContext ctx) {

	}

	@Override
	public void exitStmtImport(StmtImportContext ctx) {
		// TODO
	}

	@Override
	public void enterStart(StartContext ctx) {

	}

	@Override
	public void exitStart(StartContext ctx) {
		// pass
	}

	@Override
	public void enterFunc_arguments(Func_argumentsContext ctx) {

	}

	@Override
	public void exitFunc_arguments(Func_argumentsContext ctx) {
		// TODO
	}

	@Override
	public void enterFstWithWeight(FstWithWeightContext ctx) {

	}

	@Override
	public void exitFstWithWeight(FstWithWeightContext ctx) {
		final RE l = res.pop();
		res.push(new Var("WEIGHT_NOT_SUPPORTED"));
	}

	@Override
	public void enterFstWithoutOutput(FstWithoutOutputContext ctx) {

	}

	@Override
	public void exitFstWithoutOutput(FstWithoutOutputContext ctx) {
		// pass
	}

	@Override
	public void enterFstWithoutConcat(FstWithoutConcatContext ctx) {

	}

	@Override
	public void exitFstWithoutConcat(FstWithoutConcatContext ctx) {
		// pass
	}

	@Override
	public void enterFstWithKleene(FstWithKleeneContext ctx) {

	}

	@Override
	public void exitFstWithKleene(FstWithKleeneContext ctx) {
		if (ctx.closure != null) {
			final char KLEENE_TYPE;
			switch (ctx.closure.getText()) {
			case "*":
				KLEENE_TYPE = Kleene.ZERO_OR_MORE;
				break;
			case "+":
				KLEENE_TYPE = Kleene.ONE_OR_MORE;
				break;
			case "?":
				KLEENE_TYPE = Kleene.ZERO_OR_ONE;
				break;
			default:
				throw new IllegalStateException(ctx.closure.getText());
			}
			res.push(new Kleene(res.pop(), KLEENE_TYPE));
		}

	}

	@Override
	public void enterStmtFunc(StmtFuncContext ctx) {

	}

	@Override
	public void exitStmtFunc(StmtFuncContext ctx) {
		// TODO
	}

	@Override
	public void enterFstWithoutDiff(FstWithoutDiffContext ctx) {

	}

	@Override
	public void exitFstWithoutDiff(FstWithoutDiffContext ctx) {
		// pass
	}

	@Override
	public void enterFstWithoutWeight(FstWithoutWeightContext ctx) {

	}

	@Override
	public void exitFstWithoutWeight(FstWithoutWeightContext ctx) {
		// pass
	}

	@Override
	public void enterFunccall_arguments(Funccall_argumentsContext ctx) {

	}

	@Override
	public void exitFunccall_arguments(Funccall_argumentsContext ctx) {
		// TODO
	}

	@Override
	public void enterStmtVarDef(StmtVarDefContext ctx) {
		id = ctx.ID().getText();
		numberOfSubVariablesCreated = 0;
	}

	@Override
	public void exitStmtVarDef(StmtVarDefContext ctx) {
		assert ctx.ID().getText().equals(id) : ctx.ID().getText() + " " + id;
		vars.put(ctx.ID().getText(), res.pop());
		assert res.isEmpty() : res.toString();
	}

	@Override
	public void enterStmt_list(Stmt_listContext ctx) {

	}

	@Override
	public void exitStmt_list(Stmt_listContext ctx) {
		// TODO
	}

	@Override
	public void enterFstWithComposition(FstWithCompositionContext ctx) {

	}

	@Override
	public void exitFstWithComposition(FstWithCompositionContext ctx) {
		final RE rhs = res.pop();
		final RE lhs = res.pop();
		res.push(new Compose(lhs, rhs));
	}

	@Override
	public void enterFstWithDiff(FstWithDiffContext ctx) {

	}

	@Override
	public void exitFstWithDiff(FstWithDiffContext ctx) {
		final RE rhs = res.pop();
		final RE lhs = res.pop();
		if (lhs instanceof Set && rhs instanceof Set) {
			res.push(new SetImpl(((Set) lhs).ranges(), ((Set) rhs).ranges(), (a, b) -> a && !b));
		} else {
			res.push(new Diff(lhs, rhs));
		}
	}

	@Override
	public void enterFstWithConcat(FstWithConcatContext ctx) {

	}

	@Override
	public void exitFstWithConcat(FstWithConcatContext ctx) {
		final RE rhs = res.pop();
		final RE lhs = res.pop();
		res.push(concat(lhs, rhs));
	}

	public RE concat(RE lhs, RE rhs) {
		if (rhs instanceof Str && lhs instanceof Str) {
			final IntSeq seq = ((Str) lhs).str().concat(((Str) rhs).str());
			if (seq.size() == 1) {
				return new Char(seq.get(0));
			} else {
				return new StrImpl(seq);
			}
		} else {
			return new Concat(lhs, rhs);
		}
	}

	@Override
	public void enterVar(VarContext ctx) {

	}

	@Override
	public void exitVar(VarContext ctx) {
		final String id = ctx.ID().getText();
		final RE re = vars.get(id);
		if (re instanceof Set || re instanceof Str) {
			res.push(re);
		} else {
			res.push(new Var(id));
		}
	}

	@Override
	public void enterNested(NestedContext ctx) {

	}

	@Override
	public void exitNested(NestedContext ctx) {
		// pass
	}

	@Override
	public void enterSQuoteString(SQuoteStringContext ctx) {

	}

	@Override
	public void exitSQuoteString(SQuoteStringContext ctx) {
		// TODO
	}

	@Override
	public void enterDQuoteString(DQuoteStringContext ctx) {

	}

	@Override
	public void exitDQuoteString(DQuoteStringContext ctx) {
		res.push(parseLiteral(ctx.DStringLiteral().getText()));
	}

	@Override
	public void enterFuncCall(FuncCallContext ctx) {

	}

	@Override
	public void exitFuncCall(FuncCallContext ctx) {
		// TODO
	}

	public static <N, G extends IntermediateGraph<Pos, E, P, N>> ThraxParser<N, G> parse(CharStream source,
			LexUnicodeSpecification<N, G> specs) throws CompilationError {
		final ThraxGrammarLexer lexer = new ThraxGrammarLexer(source);
		final ThraxGrammarParser parser = new ThraxGrammarParser(new CommonTokenStream(lexer));
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				System.err.println("line " + line + ":" + charPositionInLine + " " + msg + " " + e);
			}
		});
		try {
			ThraxParser<N, G> listener = new ThraxParser<N, G>(specs);
			ParseTreeWalker.DEFAULT.walk(listener, parser.start());
			return listener;
		} catch (RuntimeException e) {
			if (e.getCause() instanceof CompilationError) {
				throw (CompilationError) e.getCause();
			} else {
				throw e;
			}
		}

	}

}
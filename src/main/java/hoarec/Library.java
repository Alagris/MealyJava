/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package hoarec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.PrimitiveIterator.OfInt;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import hoarec.GrammarParser.AtomicLiteralContext;
import hoarec.GrammarParser.AtomicNestedContext;
import hoarec.GrammarParser.AtomicVarIDContext;
import hoarec.GrammarParser.EndConcatContext;
import hoarec.GrammarParser.EndFuncsContext;
import hoarec.GrammarParser.EndParamsContext;
import hoarec.GrammarParser.EndUnionContext;
import hoarec.GrammarParser.EpsilonProductContext;
import hoarec.GrammarParser.Func_defContext;
import hoarec.GrammarParser.KleeneClosureContext;
import hoarec.GrammarParser.MoreConcatContext;
import hoarec.GrammarParser.MoreFuncsContext;
import hoarec.GrammarParser.MoreParamsContext;
import hoarec.GrammarParser.MoreUnionContext;
import hoarec.GrammarParser.NoKleeneClosureContext;
import hoarec.GrammarParser.ProductContext;
import hoarec.GrammarParser.StartContext;
import hoarec.Regex.R;
import hoarec.Simple.A;

public class Library {
    static void repeat(StringBuilder sb, String s, int times) {
        while (times-- > 0) {
            sb.append(s);
        }
    }

    static void ind(StringBuilder sb, int indent) {
        repeat(sb, "    ", indent);
    }

    interface AST {

    }

    static class Params implements AST {
        final ArrayList<String> params = new ArrayList<>();
    }

    static class Funcs implements AST {
        final ArrayList<Func> funcs = new ArrayList<>();

    }

    static class Func implements AST {
        final String name;
        final String[] vars;
        final Regex body;

        public Func(String name, String[] vars, Regex body) {
            this.name = name;
            this.vars = vars;
            this.body = body;
        }

    }

    private static class GrammarVisitor extends GrammarBaseVisitor<AST> {

        @Override
        public AST visitEndParams(EndParamsContext ctx) {
            return new Params();
        }

        @Override
        public AST visitMoreParams(MoreParamsContext ctx) {
            Params params = (Params) visit(ctx.params());
            params.params.add(ctx.ID().getText());
            return params;
        }

        @Override
        public AST visitEndFuncs(EndFuncsContext ctx) {
            return new Funcs();
        }

        @Override
        public AST visitMoreFuncs(MoreFuncsContext ctx) {
            Funcs funcs = (Funcs) visit(ctx.funcs());
            funcs.funcs.add((Func) visit(ctx.func_def()));
            return funcs;
        }

        @Override
        public AST visitFunc_def(Func_defContext ctx) {
            ArrayList<String> params = ((Params) visit(ctx.params())).params;
            return new Func(ctx.ID().getText(), params.toArray(new String[0]), (Regex) visit(ctx.mealy_union()));
        }

        @Override
        public AST visitEpsilonProduct(EpsilonProductContext ctx) {
            return visit(ctx.mealy_atomic());
        }
        
        @Override
        public AST visitAtomicVarID(AtomicVarIDContext ctx) {
            return new Simple.Var(ctx.ID().getText());
        }

        @Override
        public AST visitAtomicNested(AtomicNestedContext ctx) {
            return visit(ctx.mealy_union());
        }

        @Override
        public AST visitProduct(ProductContext ctx) {
            return new Simple.Product((A) visit(ctx.mealy_atomic()), ctx.StringLiteral().getText());
        }

        @Override
        public AST visitAtomicLiteral(AtomicLiteralContext ctx) {
            return new Simple.Atomic(ctx.StringLiteral().getText());
        }

        @Override
        public AST visitNoKleeneClosure(NoKleeneClosureContext ctx) {
            return visit(ctx.mealy_prod());
        }

        @Override
        public AST visitKleeneClosure(KleeneClosureContext ctx) {
            return new Simple.Kleene((A) visit(ctx.mealy_prod()));
        }

        @Override
        public AST visitEndConcat(EndConcatContext ctx) {
            return visit(ctx.mealy_Kleene_closure());
        }

        @Override
        public AST visitMoreConcat(MoreConcatContext ctx) {
            AST lhs = visit(ctx.mealy_Kleene_closure());
            AST rhs = visit(ctx.mealy_concat());
            return new Simple.Concat((A) lhs, (A) rhs);
        }

        @Override
        public AST visitMoreUnion(MoreUnionContext ctx) {
            AST lhs = visit(ctx.mealy_concat());
            AST rhs = visit(ctx.mealy_union());
            return new Simple.Union((A) lhs, (A) rhs);
        }

        @Override
        public AST visitEndUnion(EndUnionContext ctx) {
            return visit(ctx.mealy_concat());
        }

        @Override
        public AST visitStart(StartContext ctx) {
            return super.visitStart(ctx);
        }
    }

    private static final String source = "";

    public static void main(String[] args) throws IOException, InterruptedException {

        CharStream inputStream = CharStreams.fromString(source);
        GrammarLexer lexer = new GrammarLexer(inputStream);
        GrammarParser parser = new GrammarParser(new CommonTokenStream(lexer));
        GrammarVisitor visitor = new GrammarVisitor();
        AST out = (AST) visitor.visit(parser.start());

    }
}

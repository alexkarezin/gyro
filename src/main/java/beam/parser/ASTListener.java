package beam.parser;

import beam.parser.antlr4.BeamBaseListener;
import beam.parser.antlr4.BeamParser;
import beam.parser.ast.ASTBeamRoot;

public class ASTListener extends BeamBaseListener {

    private ASTBeamRoot root;

    public ASTBeamRoot getRoot() {
        return root;
    }

    public void setRoot(ASTBeamRoot root) {
        this.root = root;
    }

    @Override
    public void enterBeamRoot(BeamParser.BeamRootContext ctx) {

    }

    @Override
    public void enterGlobalScope(BeamParser.GlobalScopeContext ctx) {
        super.enterGlobalScope(ctx);
    }

    }

    @Override
    public void enterKey(BeamParser.KeyContext ctx) {
        super.enterKey(ctx);
    }

    @Override
    public void enterResourceScope(BeamParser.ResourceScopeContext ctx) {
        super.enterResourceScope(ctx);
    }

    @Override
    public void enterProviderBlock(BeamParser.ProviderBlockContext ctx) {
        super.enterProviderBlock(ctx);
    }

    @Override
    public void enterValue(BeamParser.ValueContext ctx) {
        super.enterValue(ctx);
    }

    @Override
    public void enterKeyValueBlock(BeamParser.KeyValueBlockContext ctx) {
        super.enterKeyValueBlock(ctx);
    }

    @Override
    public void enterMethod(BeamParser.MethodContext ctx) {
        super.enterMethod(ctx);
    }

    @Override
    public void enterMethodArguments(BeamParser.MethodArgumentsContext ctx) {
        super.enterMethodArguments(ctx);
    }

    @Override
    public void enterMethodNamedArgument(BeamParser.MethodNamedArgumentContext ctx) {
        super.enterMethodNamedArgument(ctx);
    }
}

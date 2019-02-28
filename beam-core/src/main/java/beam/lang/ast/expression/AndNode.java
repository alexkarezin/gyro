package beam.lang.ast.expression;

import beam.lang.ast.scope.Scope;
import beam.parser.antlr4.BeamParser;

public class AndNode extends ExpressionNode {

    public AndNode(BeamParser.ExpressionContext context) {
        super(context);
    }

    @Override
    public Object evaluate(Scope scope) throws Exception {
        Boolean leftValue = toBoolean(getLeftNode().evaluate(scope));
        Boolean rightValue = toBoolean(getRightNode().evaluate(scope));

        return leftValue && rightValue;
    }

    @Override
    public void buildString(StringBuilder builder, int indentDepth) {
        builder.append(getLeftNode());
        builder.append(" and ");
        builder.append(getRightNode());
    }

}
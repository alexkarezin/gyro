package beam.lang.ast.value;

import beam.lang.ast.Node;
import beam.lang.ast.scope.Scope;
import beam.parser.antlr4.BeamParser;
import org.apache.commons.lang.math.NumberUtils;

public class NumberNode extends Node {

    private final Number value;

    public NumberNode(Number value) {
        this.value = value;
    }

    public NumberNode(BeamParser.NumberValueContext context) {
        value = NumberUtils.createNumber(context.getText());
    }

    @Override
    public Object evaluate(Scope scope) {
        return value;
    }

    @Override
    public void buildString(StringBuilder builder, int indentDepth) {
        builder.append(value);
    }
}

package gyro.lang.ast.block;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import gyro.lang.ast.NodeVisitor;
import gyro.lang.ast.Node;
import gyro.parser.antlr4.GyroParser;

public class ResourceNode extends BlockNode {

    private final String type;
    private final Node name;

    public ResourceNode(String type, Node name, List<Node> body) {
        super(body);

        this.type = Preconditions.checkNotNull(type);
        this.name = Preconditions.checkNotNull(name);
    }

    public ResourceNode(GyroParser.ResourceContext context) {
        this(
            Preconditions.checkNotNull(context).resourceType().getText(),
            Node.create(context.resourceName().getChild(0)),
            context.blockBody()
                .blockStatement()
                .stream()
                .map(c -> Node.create(c.getChild(0)))
                .collect(Collectors.toList()));
    }

    public String getType() {
        return type;
    }

    public Node getName() {
        return name;
    }

    @Override
    public <C, R> R accept(NodeVisitor<C, R> visitor, C context) {
        return visitor.visitResource(this, context);
    }

}
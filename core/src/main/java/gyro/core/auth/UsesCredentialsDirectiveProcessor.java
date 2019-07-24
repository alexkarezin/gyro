package gyro.core.auth;

import java.util.List;

import gyro.core.directive.DirectiveProcessor;
import gyro.core.scope.DiffableScope;
import gyro.lang.ast.block.DirectiveNode;

public class UsesCredentialsDirectiveProcessor extends DirectiveProcessor<DiffableScope> {

    @Override
    public String getName() {
        return "uses-credentials";
    }

    @Override
    public void process(DiffableScope scope, DirectiveNode node) {
        List<Object> arguments = evaluateArguments(scope, node, 1, 1);
        
        scope.getStateNodes().add(node);
        scope.getSettings(CredentialsSettings.class).setUseCredentials((String) arguments.get(0));
    }

}
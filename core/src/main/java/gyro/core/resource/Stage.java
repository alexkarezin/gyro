package gyro.core.resource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import gyro.core.GyroUI;
import gyro.lang.ast.Node;
import gyro.lang.ast.block.KeyBlockNode;
import gyro.lang.ast.block.ResourceNode;

public class Stage {

    private final String name;
    private final boolean confirmDiff;
    private final String transitionPrompt;
    private final List<Node> changes = new ArrayList<>();
    private final List<KeyBlockNode> swaps = new ArrayList<>();
    private final List<Transition> transitions = new ArrayList<>();

    public Stage(Scope parent, ResourceNode node) {
        Scope scope = new Scope(parent);
        NodeEvaluator evaluator = scope.getRootScope().getEvaluator();

        for (Iterator<Node> i = node.getBody().iterator(); i.hasNext();) {
            Node item = i.next();

            if (item instanceof KeyBlockNode) {
                KeyBlockNode kb = (KeyBlockNode) item;
                String kbKey = kb.getKey();

                if (kbKey.equals("create")) {
                    changes.addAll(kb.getBody());
                    i.remove();
                    continue;

                } else if (kbKey.equals("delete")) {
                    changes.add(kb);
                    i.remove();
                    continue;

                } else if (kbKey.equals("swap")) {
                    swaps.add(kb);
                    i.remove();
                    continue;
                }

            } else if (item instanceof ResourceNode) {
                ResourceNode r = (ResourceNode) item;

                if (r.getType().equals("transition")) {
                    transitions.add(new Transition(parent, r));
                    i.remove();
                    continue;
                }
            }

            evaluator.visit(item, scope);
        }

        name = (String) evaluator.visit(node.getName(), parent);
        confirmDiff = Boolean.TRUE.equals(scope.get("confirm-diff"));
        transitionPrompt = (String) scope.get("transition-prompt");
    }

    public String getName() {
        return name;
    }

    public String execute(
            GyroUI ui,
            State state,
            Resource pendingResource,
            RootScope currentRootScope,
            RootScope pendingRootScope)
            throws Exception {

        Scope executeScope = new Scope(pendingRootScope);
        NodeEvaluator evaluator = executeScope.getRootScope().getEvaluator();

        executeScope.put("NAME", pendingResource.name);
        executeScope.put("PENDING", pendingResource.scope.resolve());

        for (Node change : changes) {
            evaluator.visit(change, executeScope);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deletes = (List<Map<String, Object>>) executeScope.get("delete");

        if (deletes != null) {
            for (Map<String, Object> delete : deletes) {
                pendingRootScope.remove(delete.get("type") + "::" + delete.get("name"));
            }
        }

        for (KeyBlockNode swap : swaps) {
            evaluator.visit(swap, executeScope);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> swaps = (List<Map<String, Object>>) executeScope.get("swap");

        if (swaps != null) {
            for (Map<String, Object> swap : swaps) {
                String type = (String) swap.get("type");
                String x = (String) swap.get("x");
                String y = (String) swap.get("y");

                ui.write("@|magenta ⤢ Swapping %s with %s|@\n", x, y);
                state.swap(currentRootScope, pendingRootScope, type, x, y);
            }
        }

        Set<String> diffFiles = state.getDiffFiles();

        Diff diff = new Diff(
            currentRootScope.findResourcesIn(diffFiles),
            pendingRootScope.findResourcesIn(diffFiles));

        diff.diff();

        if (confirmDiff && diff.write(ui)) {
            if (ui.readBoolean(Boolean.TRUE, "\nContinue with %s stage?", name)) {
                ui.write("\n");

            } else {
                throw new RuntimeException("Aborted!");
            }
        }

        diff.executeCreateOrUpdate(ui, state);
        diff.executeReplace(ui, state);
        diff.executeDelete(ui, state);

        if (transitions.isEmpty()) {
            return null;
        }

        Map<String, String> options = transitions.stream()
                .collect(Collectors.toMap(Transition::getName, Transition::getTo));

        while (true) {
            for (Transition transition : transitions) {
                ui.write("\n%s) %s", transition.getName(), transition.getDescription());
            }

            String selected = ui.readText("\n%s ", transitionPrompt != null ? transitionPrompt : "Next stage?");
            String selectedOption = options.get(selected);

            if (selectedOption != null) {
                return selectedOption;

            } else {
                ui.write("[%s] isn't valid! Try again.\n", selected);
            }
        }
    }

}
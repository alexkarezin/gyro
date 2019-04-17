package gyro.core.scope;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import gyro.core.GyroCore;
import gyro.core.GyroException;
import gyro.core.resource.Resource;
import gyro.core.resource.ResourceFinder;
import gyro.core.workflow.Workflow;
import gyro.lang.ast.block.VirtualResourceNode;

public class RootScope extends Scope {

    private final RootScope current;
    private final Map<String, Class<?>> resourceClasses = new HashMap<>();
    private final Map<String, Class<? extends ResourceFinder>> resourceFinderClasses = new HashMap<>();
    private final Map<String, VirtualResourceNode> virtualResourceNodes = new LinkedHashMap<>();
    private final List<Workflow> workflows = new ArrayList<>();
    private final List<FileScope> fileScopes = new ArrayList<>();
    private final FileScope initScope;
    private final Map<String, Resource> resources = new LinkedHashMap<>();
    private final Set<String> activePaths = new HashSet<>();

    public RootScope() {
        this(null, Collections.emptySet());
    }

    public RootScope(Set<String> activePaths) {
        this(null, activePaths);
    }

    public RootScope(RootScope current) {
        this(current, Collections.emptySet());
    }

    public RootScope(RootScope current, Set<String> activePaths) {
        super(null);
        this.current = current;


        try {
            initScope = new FileScope(this, GyroCore.findPluginPath().toString());
            if (current == null) {
                for (String path : activePaths) {
                    path += ".state";
                    Path rootDir = GyroCore.findPluginPath().getParent().getParent();
                    Path relative = rootDir.relativize(Paths.get(path).toAbsolutePath());
                    Path statePath = Paths.get(rootDir.toString(), ".gyro", "state", relative.toString());

                    if (statePath.toFile().getParentFile() != null && !statePath.toFile().getParentFile().exists()) {
                        statePath.toFile().getParentFile().mkdirs();
                    }

                    this.activePaths.add(statePath.toString());
                }
            } else {
                this.activePaths.addAll(activePaths);
            }

        } catch (IOException e) {
            throw new GyroException("Unable to create init scope!");
        }

        put("ENV", System.getenv());
    }

    public RootScope getCurrent() {
        return current;
    }

    public Map<String, Class<?>> getResourceClasses() {
        return resourceClasses;
    }

    public Map<String, Class<? extends ResourceFinder>> getResourceFinderClasses() {
        return resourceFinderClasses;
    }

    public Map<String, VirtualResourceNode> getVirtualResourceNodes() {
        return virtualResourceNodes;
    }

    public List<Workflow> getWorkflows() {
        return workflows;
    }

    public List<FileScope> getFileScopes() {
        return fileScopes;
    }

    public FileScope getInitScope() {
        return initScope;
    }

    public Map<String, Resource> getResources() {
        return resources;
    }

    public Set<String> getActivePaths() {
        return activePaths;
    }

    public List<Resource> findAllResources() {
        return new ArrayList<>(resources.values());
    }

    public List<Resource> findAllActiveResources() {

        if (getActivePaths().isEmpty()) {
            return findAllResources();
        }

        try {
            List<FileScope> activeFileScopes = new ArrayList<>();
            for (FileScope fileScope : getFileScopes()) {
                for (String path : activePaths) {
                    if (Files.isSameFile(Paths.get(fileScope.getFile()), Paths.get(path))) {
                        activeFileScopes.add(fileScope);
                    }
                }
            }

            return resources.values().stream()
                .filter(r -> activeFileScopes.contains(r.scope().getFileScope()))
                .collect(Collectors.toList());

        } catch (IOException e) {
            throw new GyroException(e.getMessage(), e);
        }
    }

    public Resource findResource(String name) {
        return resources.get(name);
    }

}

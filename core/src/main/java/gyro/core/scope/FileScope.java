package gyro.core.scope;

import java.util.ArrayList;
import java.util.List;

import gyro.core.LocalFileBackend;
import gyro.core.FileBackend;
import gyro.core.plugin.PluginLoader;

public class FileScope extends Scope {

    private static final FileBackend DEFAULT_FILE_BACKEND = new LocalFileBackend();

    private final String file;
    private FileBackend backend;
    private final List<FileScope> imports = new ArrayList<>();
    private final List<PluginLoader> pluginLoaders = new ArrayList<>();

    public FileScope(RootScope parent, String file) {
        super(parent);

        if (!file.endsWith(".gyro") && !file.endsWith(".gyro.state")) {
            file += ".gyro";
        }

        this.file = file;
    }

    public String getFile() {
        return file;
    }

    public FileBackend getBackend() {
        if (backend == null || !file.endsWith(".gyro.state")) {
            return DEFAULT_FILE_BACKEND;

        } else {
            return backend;
        }
    }

    public void setBackend(FileBackend backend) {
        this.backend = backend;
    }

    public List<FileScope> getImports() {
        return imports;
    }

    public List<PluginLoader> getPluginLoaders() {
        return pluginLoaders;
    }

}

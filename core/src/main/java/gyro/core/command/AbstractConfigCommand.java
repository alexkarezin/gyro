package gyro.core.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import gyro.core.GyroCore;
import gyro.core.GyroException;
import gyro.core.GyroUI;
import gyro.core.LocalFileBackend;
import gyro.core.auth.Credentials;
import gyro.core.auth.CredentialsSettings;
import gyro.core.resource.DiffableInternals;
import gyro.core.resource.DiffableType;
import gyro.core.scope.FileScope;
import gyro.core.resource.Resource;
import gyro.core.scope.RootScope;
import gyro.core.scope.State;
import io.airlift.airline.Arguments;
import io.airlift.airline.Option;

public abstract class AbstractConfigCommand extends AbstractCommand {

    @Option(name = { "--skip-refresh" })
    public boolean skipRefresh;

    @Option(name = { "--test" })
    private boolean test;

    @Arguments
    private List<String> files;

    private GyroCore core;

    public GyroCore core() {
        return core;
    }

    protected abstract void doExecute(RootScope current, RootScope pending, State state) throws Exception;

    @Override
    protected void doExecute() throws Exception {
        Path rootDir = GyroCore.getRootDirectory();

        if (rootDir == null) {
            throw new GyroException("Not a gyro project directory, use 'gyro init <plugins>...' to create one. See 'gyro help init' for detailed usage.");
        }

        Set<String> normalizedFiles;

        if (files == null) {
            normalizedFiles = null;

        } else {
            normalizedFiles = files.stream()
                .map(file -> {
                    file = file.endsWith(".gyro") ? file : file + ".gyro";
                    file = rootDir.relativize(Paths.get("").toAbsolutePath().resolve(file)).normalize().toString();

                    if (Files.exists(rootDir.resolve(file))) {
                        return file;

                    } else {
                        throw new GyroException(String.format("File not found! %s", file));
                    }
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        core = new GyroCore();

        RootScope current = new RootScope(
            "../../" + GyroCore.INIT_FILE,
            new LocalFileBackend(rootDir.resolve(".gyro/state")),
            null);

        current.evaluate(normalizedFiles);

        RootScope pending = new RootScope(
            GyroCore.INIT_FILE,
            new LocalFileBackend(rootDir),
            current);

        if (!test) {
            current.getSettings(CredentialsSettings.class)
                .getCredentialsByName()
                .values()
                .forEach(Credentials::refresh);

            if (!skipRefresh) {
                refreshResources(current);
            }
        }

        pending.evaluate(normalizedFiles);
        pending.validate();
        doExecute(current, pending, new State(current, pending, test, normalizedFiles));
    }

    private void refreshResources(RootScope scope) {
        ScheduledExecutorService messageService = Executors.newSingleThreadScheduledExecutor();
        GyroUI ui = GyroCore.ui();
        AtomicInteger started = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();

        messageService.scheduleAtFixedRate(() -> {
            ui.replace("@|magenta ⟳ Refreshing resources:|@ %s started, %s done", started.get(), done.get());
        }, 0, 100, TimeUnit.MILLISECONDS);

        ExecutorService refreshService = Executors.newCachedThreadPool();
        List<Refresh> refreshes = new ArrayList<>();

        for (FileScope fileScope : scope.getFileScopes()) {
            for (Object value : fileScope.values()) {
                if (!(value instanceof Resource)) {
                    continue;
                }

                Resource resource = (Resource) value;

                refreshes.add(new Refresh(resource, refreshService.submit(() -> {
                    started.incrementAndGet();
                    boolean keep = resource.refresh();
                    done.incrementAndGet();

                    if (keep) {
                        DiffableInternals.update(resource, true);
                        return false;

                    } else {
                        return true;
                    }
                })));
            }
        }

        refreshService.shutdown();

        for (Refresh refresh : refreshes) {
            Resource resource = refresh.resource;
            String typeName = DiffableType.getInstance(resource).getName();
            String name = DiffableInternals.getName(resource);

            try {
                if (refresh.future.get()) {
                    ui.replace("@|magenta - Removing from state:|@ %s %s\n", typeName, name);
                    scope.getFileScopes().forEach(s -> s.remove(resource.primaryKey()));
                }

            } catch (ExecutionException error) {
                ui.write("\n");

                throw new GyroException(
                    String.format("Can't refresh @|bold %s %s|@ resource!", typeName, name),
                    error.getCause());

            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        messageService.shutdown();
        ui.replace("@|magenta ⟳ Refreshed resources:|@ %s\n", refreshes.size());
    }

    private static class Refresh {

        public final Resource resource;
        public final Future<Boolean> future;

        public Refresh(Resource resource, Future<Boolean> future) {
            this.resource = resource;
            this.future = future;
        }

    }

}

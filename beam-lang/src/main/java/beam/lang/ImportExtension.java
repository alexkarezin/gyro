package beam.lang;

import com.psddev.dari.util.StringUtils;

import java.io.IOException;

public class ImportExtension extends BeamExtension {

    private boolean imported;

    @Override
    protected boolean resolve(BeamContext parent, BeamContext root) {
        boolean progress = false;
        BeamResolvable pathResolvable = getParams().get(0);
        BeamResolvable idResolvable = getParams().get(2);
        String path = pathResolvable.getValue().toString();
        String id = idResolvable.getValue().toString();

        String statePath = StringUtils.ensureEnd(path, ".state");
        BeamContextKey key = new BeamContextKey(String.format("%s %s %s", statePath, "as", id), getType());
        if (parent.hasKey(key)) {
            BeamConfig existingConfig = (BeamConfig) parent.getReferable(key);

            if (existingConfig.getClass() != this.getClass()) {
                parent.addReferable(key, this);
                progress = true;
            }
        } else {
            parent.addReferable(key, this);
            progress = true;
        }

        if (!imported) {
            try {
                BeamConfig importConfig = getLang().parse(path);
                importConfig.applyExtension(getLang());
                root.addReferable(new BeamContextKey(id), importConfig);
                imported = true;
                progress = true;
            } catch (IOException ioe) {
                throw new BeamLangException("Unable to import '" + path + "'.");
            }
        }

        return progress;
    }
}

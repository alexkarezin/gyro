package beam.lang;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapValue extends Value<Map> {

    private Map<String, Value> keyValues;

    public Map<String, Value> getKeyValues() {
        if (keyValues == null) {
            keyValues = new HashMap<>();
        }

        return keyValues;
    }

    public void put(String key, Value value) {
        value.parentNode(this);

        getKeyValues().put(key, value);
    }

    @Override
    public void parentNode(Node parentNode) {
        super.parentNode(parentNode);

        for (Value value : getKeyValues().values()) {
            value.parentNode(parentNode);
        }
    }

    @Override
    public Map getValue() {
        Map<String, Object> map = new HashMap<>();
        for (String key : getKeyValues().keySet()) {
            map.put(key, getKeyValues().get(key).getValue());
        }

        return map;
    }

    @Override
    public MapValue copy() {
        MapValue mapNode = new MapValue();

        for (String key : getKeyValues().keySet()) {
            Value value = getKeyValues().get(key).copy();
            mapNode.put(key, value);
        }

        return mapNode;
    }

    @Override
    public boolean resolve() {
        for (Value value : getKeyValues().values()) {
            boolean resolved = value.resolve();
            if (!resolved) {
                throw new BeamLanguageException("Unabled to resolve configuration.", value);
            }
        }

        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("{\n");

        List<String> out = new ArrayList<>();
        for (String key : getKeyValues().keySet()) {
            out.add(String.format("    %s: %s", key, getKeyValues().get(key).getValue()));
        }

        sb.append(StringUtils.join(out, ",\n"));
        sb.append("\n}\n");

        return sb.toString();
    }

}
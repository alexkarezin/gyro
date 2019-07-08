package gyro.core.finder;

import gyro.core.resource.Scope;

public class FilterContext {

    private final Scope scope;
    private final Object value;

    public FilterContext(Scope scope, Object value) {
        this.scope = scope;
        this.value = value;
    }

    public Scope getScope() {
        return scope;
    }

    public Object getValue() {
        return value;
    }

}
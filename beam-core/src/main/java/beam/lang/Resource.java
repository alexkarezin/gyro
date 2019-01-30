package beam.lang;

import beam.core.BeamException;
import beam.core.diff.ResourceChange;
import beam.core.diff.ResourceDiffProperty;
import beam.core.diff.ResourceDisplayDiff;
import beam.core.diff.ResourceName;
import beam.lang.ast.Scope;
import com.google.common.base.CaseFormat;
import com.google.common.base.Throwables;
import com.psddev.dari.util.ObjectUtils;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class Resource {

    private String type;
    private String name;
    private Scope scope;
    private Resource parent;

    // -- Internal

    private Set<Resource> dependencies;
    private Set<Resource> dependents;
    private Map<String, List<Resource>> subResources;
    private Credentials resourceCredentials;
    private ResourceChange change;

    // -- Resource Implementation API

    public abstract boolean refresh();

    public abstract void create();

    public abstract void update(Resource current, Set<String> changedProperties);

    public abstract void delete();

    public abstract String toDisplayString();

    public abstract Class resourceCredentialsClass();

    public Scope scope() {
        return scope;
    }

    public void scope(Scope scope) {
        this.scope = scope;
    }

    public Resource parent() {
        return parent;
    }

    public Resource parent(Resource parent) {
        this.parent = parent;
        return this;
    }

    public String resourceCredentialsName() {
        Class c = resourceCredentialsClass();

        try {
            Credentials credentials = (Credentials) c.newInstance();

            String resourceNamespace = credentials.getCloudName();
            String resourceName = c.getSimpleName();
            if (c.isAnnotationPresent(ResourceName.class)) {
                ResourceName name = (ResourceName) c.getAnnotation(ResourceName.class);
                resourceName = name.value();

                return String.format("%s::%s", resourceNamespace, resourceName);
            }
        } catch (Exception ex) {
            throw new BeamException("Unable to determine credentials resource name.", ex);
        }

        return c.getSimpleName();
    }

    public Credentials getResourceCredentials() {
        return resourceCredentials;
    }

    public void setResourceCredentials(Credentials resourceCredentials) {
        this.resourceCredentials = resourceCredentials;
    }

    // -- Diff Engine

    public String primaryKey() {
        return String.format("%s %s", resourceType(), resourceIdentifier());
    }

    public ResourceChange change() {
        return change;
    }

    public void change(ResourceChange change) {
        this.change = change;
    }

    public Set<Resource> dependencies() {
        if (dependencies == null) {
            dependencies = new LinkedHashSet<>();
        }

        return dependencies;
    }

    public Set<Resource> dependents() {
        if (dependents == null) {
            dependents = new LinkedHashSet<>();
        }

        return dependents;
    }

    public Map<String, Object> resolvedKeyValues() {
        Map<String, Object> copy = new HashMap<>(scope);

        try {
            for (PropertyDescriptor p : Introspector.getBeanInfo(getClass()).getPropertyDescriptors()) {
                Method reader = p.getReadMethod();

                if (reader != null) {
                    String key = keyFromFieldName(p.getDisplayName());
                    Object value = reader.invoke(this);

                    copy.put(key, value);
                }
            }
        } catch (IllegalAccessException | IntrospectionException error) {
            throw new IllegalStateException(error);
        } catch (InvocationTargetException error) {
            throw Throwables.propagate(error);
        }

        return copy;
    }

    public void diffOnCreate(ResourceChange change) throws Exception {
        Map<String, Object> pendingValues = resolvedKeyValues();

        for (String key : subresourceFields()) {
            Object pendingValue = pendingValues.get(key);

            if (pendingValue instanceof Collection) {
                change.create((List) pendingValue);
            } else {
                change.createOne((Resource) pendingValue);
            }
        }
    }

    public void diffOnUpdate(ResourceChange change, Resource current) throws Exception {
        Map<String, Object> currentValues = current.resolvedKeyValues();
        Map<String, Object> pendingValues = resolvedKeyValues();

        for (String key : subresourceFields()) {

            Object currentValue = currentValues.get(key);
            Object pendingValue = pendingValues.get(key);

            if (pendingValue instanceof Collection) {
                change.update((List) currentValue, (List) pendingValue);
            } else {
                change.updateOne((Resource) currentValue, (Resource) pendingValue);
            }
        }
    }

    public void diffOnDelete(ResourceChange change) throws Exception {
        Map<String, Object> pendingValues = resolvedKeyValues();

        for (String key : subresourceFields()) {
            Object pendingValue = pendingValues.get(key);

            if (pendingValue instanceof Collection) {
                change.delete((List) pendingValue);
            } else {
                change.deleteOne((Resource) pendingValue);
            }
        }
    }

    public ResourceDisplayDiff calculateFieldDiffs(Resource current) {
        boolean firstField = true;

        ResourceDisplayDiff displayDiff = new ResourceDisplayDiff();

        Map<String, Object> currentValues = current.resolvedKeyValues();
        Map<String, Object> pendingValues = resolvedKeyValues();

        for (String key : pendingValues.keySet()) {
            // If there is no getter for this method then skip this field since there can
            // be no ResourceDiffProperty annotation.
            Method reader = readerMethodForKey(key);
            if (reader == null) {
                continue;
            }

            // If no ResourceDiffProperty annotation or if this field has subresources then skip this field.
            ResourceDiffProperty propertyAnnotation = reader.getAnnotation(ResourceDiffProperty.class);
            if (propertyAnnotation == null || propertyAnnotation.subresource()) {
                continue;
            }
            boolean nullable = propertyAnnotation.nullable();

            Object currentValue = currentValues.get(key);
            Object pendingValue = pendingValues.get(key);

            if (pendingValue != null || nullable) {
                String fieldChangeOutput = null;
                if (pendingValue instanceof List) {
                    fieldChangeOutput = ResourceChange.processAsListValue(key, (List) currentValue, (List) pendingValue);
                } else if (pendingValue instanceof Map) {
                    fieldChangeOutput = ResourceChange.processAsMapValue(key, (Map) currentValue, (Map) pendingValue);
                } else {
                    fieldChangeOutput = ResourceChange.processAsScalarValue(key, currentValue, pendingValue);
                }

                if (!ObjectUtils.isBlank(fieldChangeOutput)) {
                    if (!firstField) {
                        displayDiff.getChangedDisplay().append(", ");
                    }

                    displayDiff.addChangedProperty(key);
                    displayDiff.getChangedDisplay().append(fieldChangeOutput);

                    if (!propertyAnnotation.updatable()) {
                        displayDiff.setReplace(true);
                    }

                    firstField = false;
                }
            }
        }

        return displayDiff;
    }

    // -- Base Resource

    public Object get(String key) {
        return scope.get(key);
    }

    /**
     * Return a list of fields that contain subresources (ResourceDiffProperty(subresource = true)).
     */
    private List<String> subresourceFields() {
        List<String> keys = new ArrayList<>();
        Map<String, Object> pendingValues = resolvedKeyValues();

        for (String key : pendingValues.keySet()) {
            // If there is no getter for this method then skip this field since there can
            // be no ResourceDiffProperty annotation.
            Method reader = readerMethodForKey(key);
            if (reader == null) {
                continue;
            }

            // If no ResourceDiffProperty annotation or if this field is not subresources then skip this field.
            ResourceDiffProperty propertyAnnotation = reader.getAnnotation(ResourceDiffProperty.class);
            if (propertyAnnotation == null || !propertyAnnotation.subresource()) {
                continue;
            }

            keys.add(key);
        }

        return keys;
    }

    public Map<String, List<Resource>> subResources() {
        if (subResources == null) {
            subResources = new HashMap<>();

            List<Resource> resources = (List<Resource>) scope.computeIfAbsent("_subresources", s -> new ArrayList<>());
            for (Resource resource : resources) {
                List<Resource> group = subResources.computeIfAbsent(resource.resourceType(), s -> new ArrayList<>());
                group.add(resource);
            }
        }

        return subResources;
    }

    public void putSubresource(String fieldName, Resource subresource) {
    }

    public void putSubresource(Resource subresource) {
    }

    public void removeSubresource(Resource subresource) {
        List<Resource> resources = (List<Resource>) scope.computeIfAbsent("_subresources", s -> new ArrayList<>());
        resources.remove(subresource);
    }

    public String resourceType() {
        if (type == null) {
            ResourceName name = getClass().getAnnotation(ResourceName.class);
            return name != null ? name.value() : null;
        }

        return type;
    }

    public void resourceType(String type) {
        this.type = type;
    }

    public String resourceIdentifier() {
        return name;
    }

    public void resourceIdentifier(String name) {
        this.name = name;
    }


    public Resource parentResource() {
        /*
        Node parent = parent();

        while (parent != null && !(parent instanceof Resource)) {
            parent = parent.parent();
        }
        */

        return null;
    }

    // -- Internal State

    public final void syncInternalToProperties() {
        for (String key : scope().keySet()) {
            if (key.startsWith("_")) {
                continue;
            }

            Object value = scope().get(key);

            try {
                String convertedKey = CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, key);

                if (!BeanUtils.describe(this).containsKey(convertedKey)) {
                    String message = String.format("invalid attribute '%s'", key);

                    throw new BeamException(message);
                }

                BeanUtils.setProperty(this, convertedKey, value);
            } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
                // Ignoring errors from setProperty
            }
        }

        for (String subResourceField : subResources().keySet()) {
            List<Resource> subResources = subResources().get(subResourceField);

            Method writer = null;
            try {
                writer = writerMethodForKey(subResourceField);
                if (writer == null) {
                    throw new BeamException("Not setter for subresource field: " + subResourceField);
                }

                writer.invoke(this, subResources);
            } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
                if (subResources.size() == 1) {
                    try {
                        writer.invoke(this, subResources.get(0));
                        continue;
                    } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException ee) {
                        // Exception is thrown below.
                    }
                }

                throw new BeamException("Unable to set subresource field: " + subResourceField);
            }
        }
    }

    /**
     * Copy internal values from source to this object. This is used to copy information
     * from the current state (i.e. a resource loaded from a state file) into a pending
     * state (i.e. a resource loaded from a config file).
     */
    public void syncPropertiesFromResource(Resource source) {
        syncPropertiesFromResource(source, false);
    }

    public void syncPropertiesFromResource(Resource source, boolean force) {
        if (source == null) {
            return;
        }

        try {
            for (PropertyDescriptor p : Introspector.getBeanInfo(getClass()).getPropertyDescriptors()) {

                Method reader = p.getReadMethod();

                if (reader != null) {
                    Method writer = p.getWriteMethod();

                    ResourceDiffProperty propertyAnnotation = reader.getAnnotation(ResourceDiffProperty.class);
                    boolean isNullable = false;
                    if (propertyAnnotation != null) {
                        isNullable = propertyAnnotation.nullable();
                    }

                    Object currentValue = reader.invoke(source);
                    Object pendingValue = reader.invoke(this);

                    boolean isNullOrEmpty = pendingValue == null;
                    isNullOrEmpty = pendingValue instanceof Collection && ((Collection) pendingValue).isEmpty() ? true : isNullOrEmpty;

                    if (writer != null && (currentValue != null && isNullOrEmpty && (!isNullable || force))) {
                        writer.invoke(this, reader.invoke(source));
                    }
                }
            }

        } catch (IllegalAccessException | IntrospectionException error) {
            throw new IllegalStateException(error);
        } catch (InvocationTargetException error) {
            throw Throwables.propagate(error);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Resource that = (Resource) o;

        return Objects.equals(primaryKey(), that.primaryKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(primaryKey());
    }

    public String serialize(int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append(indent(indent));
        sb.append(resourceType()).append(" ");

        if (resourceIdentifier() != null) {
            sb.append('\'');
            sb.append(resourceIdentifier());
            sb.append('\'');
        }

        sb.append("\n");

        for (String key : scope.keySet()) {
            if (key.startsWith("_")) {
                continue;
            }

            Object value = scope.get(key);
            if (value != null) {
                sb.append(indent(indent + 4));
                sb.append(key).append(": ");

                if (value instanceof List) {
                    List listValues = (List) value;

                    sb.append("[\n");

                    List<String> out = new ArrayList<>();
                    for (Object listValue : listValues) {
                        out.add(indent(indent + 8) + "'" + listValue.toString() + "'");
                    }

                    sb.append(StringUtils.join(out, ",\n"));
                    sb.append("\n").append(indent(indent + 4)).append("]\n");
                } else if (value instanceof Map) {
                    Map<String, Object> mapValues = (Map<String, Object>) value;

                    sb.append("{\n");

                    List<String> out = new ArrayList<>();
                    for (String k : mapValues.keySet()) {
                        out.add(String.format("%s%s: '%s'", indent(indent + 8), k, mapValues.get(k)));
                    }

                    sb.append(StringUtils.join(out, ",\n"));
                    sb.append("\n").append(indent(indent + 4)).append("}\n");
                } else if (value instanceof Number || value instanceof Boolean) {
                    sb.append(scope.get(key));
                } else {
                    sb.append("'").append(scope.get(key)).append("'");
                }

                sb.append("\n");
            }
        }

        for (String key : subResources().keySet()) {
            List<Resource> subresources = subResources().get(key);

            for (Resource subresource : subresources) {
                sb.append(subresource.serialize(indent + 4));
            }
        }

        sb.append(indent(indent));
        sb.append("end\n\n");

        return sb.toString();
    }

    @Override
    public String toString() {
        if (resourceIdentifier() == null) {
            return String.format("Resource[type: %s]", resourceType());
        }

        return String.format("Resource[type: %s, id: %s]", resourceType(), resourceIdentifier());
    }

    String fieldNameFromKey(String key) {
        return CaseFormat.LOWER_HYPHEN.to(CaseFormat.LOWER_CAMEL, key);
    }

    String keyFromFieldName(String field) {
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, field).replaceFirst("^get-", "");
    }

    Method readerMethodForKey(String key) {
        PropertyDescriptor p = propertyDescriptorForKey(key);
        if (p != null) {
            return p.getReadMethod();
        }

        return null;
    }

    Method writerMethodForKey(String key) {
        PropertyDescriptor p = propertyDescriptorForKey(key);
        if (p != null) {
            return p.getWriteMethod();
        }

        return null;
    }

    PropertyDescriptor propertyDescriptorForKey(String key) {
        String convertedKey = fieldNameFromKey(key);
        try {
            for (PropertyDescriptor p : Introspector.getBeanInfo(getClass()).getPropertyDescriptors()) {
                if (p.getDisplayName().equals(convertedKey)) {
                    return p;
                }
            }
        } catch (IntrospectionException ex) {
            // Ignoring introspection exceptions
        }

        return null;
    }

    protected String indent(int indent) {
        return StringUtils.repeat(" ", indent);
    }

}

package gyro.core.scope.converter;

import java.lang.reflect.Type;

import com.psddev.dari.util.ConversionFunction;
import com.psddev.dari.util.Converter;
import gyro.core.resource.Diffable;
import gyro.core.resource.DiffableType;
import gyro.core.scope.DiffableScope;

public class DiffableScopeToDiffable implements ConversionFunction<DiffableScope, Diffable> {

    @Override
    @SuppressWarnings("unchecked")
    public Diffable convert(Converter converter, Type returnType, DiffableScope scope) {
        DiffableType<Diffable> type = DiffableType.getInstance((Class<Diffable>) returnType);
        Diffable diffable = type.newInstance(scope);

        type.setValues(diffable, scope);

        return diffable;
    }

}
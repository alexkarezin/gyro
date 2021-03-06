/*
 * Copyright 2020, Perfect Sense, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gyro.core.validation;

import java.util.stream.Stream;

import com.psddev.dari.util.ObjectUtils;
import gyro.core.resource.Diffable;
import gyro.core.resource.DiffableField;
import gyro.core.resource.DiffableType;
import gyro.util.Bug;

/**
 * Validate if the fields that annotated field depends on are populated.
 *
 * <p>
 * e.g.
 *
 * <pre><code>
 *     public Parent getParent() {
 *         ...
 *     }
 *
 *     &#064;DependsOn({ "parent" })
 *     public Child getChild() {
 *         ...
 *     }
 * </code></pre>
 *
 * If 'child` field is provided but 'parent' field is not, validation will fail.
 * </p>
 */
public class DependsOnValidator implements Validator<DependsOn> {

    @Override
    public boolean isValid(Diffable diffable, DependsOn annotation, Object fieldValue) {
        if (ObjectUtils.isBlank(fieldValue)) {
            return true;
        }
        DiffableType<Diffable> diffableType = DiffableType.getInstance(diffable);

        return Stream.of(annotation.value())
            .allMatch(name -> {
                DiffableField field = diffableType.getField(name);

                if (field == null) {
                    throw new Bug(String.format(
                        "Invalid usage of '@DependsOn' validation annotation. The field @|bold '%s'|@ doesn't exist.",
                        name));
                }
                return !ObjectUtils.isBlank(field.getValue(diffable));
            });
    }

    @Override
    public String getMessage(DependsOn annotation) {
        return String.format(
            "Depends on the following field(s): @|bold ['%s']|@",
            String.join("', '", annotation.value()));
    }
}

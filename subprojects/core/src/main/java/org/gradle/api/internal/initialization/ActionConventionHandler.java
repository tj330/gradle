/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.initialization;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.api.internal.tasks.properties.InspectionScheme;
import org.gradle.internal.Cast;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;
import org.gradle.model.internal.type.ModelType;
import org.gradle.plugin.software.internal.SoftwareTypeConventionHandler;
import org.gradle.plugin.software.internal.SoftwareTypeImplementation;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class ActionConventionHandler implements SoftwareTypeConventionHandler {
    private final SoftwareTypeRegistry softwareTypeRegistry;
    private final InspectionScheme inspectionScheme;

    public ActionConventionHandler(SoftwareTypeRegistry softwareTypeRegistry, InspectionScheme inspectionScheme) {
        this.softwareTypeRegistry = softwareTypeRegistry;
        this.inspectionScheme = inspectionScheme;
    }

    @Override
    public void apply(Object target, String softwareTypeName, Plugin<?> plugin) {
        SoftwareTypeImplementation<?> softwareTypeImplementation = softwareTypeRegistry.getSoftwareTypeImplementations().get(softwareTypeName);

        DefaultTypeValidationContext typeValidationContext = DefaultTypeValidationContext.withRootType(plugin.getClass(), false);
        inspectionScheme.getPropertyWalker().visitProperties(
            plugin,
            typeValidationContext,
            new PropertyVisitor() {
                @Override
                public void visitSoftwareTypeProperty(String propertyName, PropertyValue value, Class<?> declaredPropertyType, SoftwareType softwareType) {
                    softwareTypeImplementation.getConventions().stream()
                        .filter(convention -> convention instanceof ActionConvention)
                        .map(convention -> (ActionConvention<?>) convention)
                        .forEach(convention -> convention.apply(Cast.uncheckedCast(executeActionReceiver(softwareTypeImplementation.getModelPublicType(), value.call()))));
                }
            }
        );

        if (!typeValidationContext.getProblems().isEmpty()) {
            throw new DefaultMultiCauseException(
                String.format(typeValidationContext.getProblems().size() == 1
                        ? "A problem was found with the %s plugin."
                        : "Some problems were found with the %s plugin.",
                    getPluginObjectDisplayName(plugin)),
                typeValidationContext.getProblems().stream()
                    .map(TypeValidationProblemRenderer::renderMinimalInformationAbout)
                    .sorted()
                    .map(InvalidUserDataException::new)
                    .collect(toImmutableList())
            );
        }
    }

    private static <T> ActionConventionReceiver<T> executeActionReceiver(Class<T> publicModelType, Object modelObject) {
        return convention -> convention.execute(Cast.uncheckedNonnullCast(modelObject));
    }

    private static String getPluginObjectDisplayName(Object parameterObject) {
        return ModelType.of(new DslObject(parameterObject).getDeclaredType()).getDisplayName();
    }

}

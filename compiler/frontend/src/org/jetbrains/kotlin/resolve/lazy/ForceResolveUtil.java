/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.resolve.lazy;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.*;
import org.jetbrains.kotlin.descriptors.annotations.AnnotationWithTarget;
import org.jetbrains.kotlin.descriptors.annotations.Annotations;
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl;
import org.jetbrains.kotlin.resolve.DescriptorUtils;
import org.jetbrains.kotlin.resolve.scopes.MemberScope;
import org.jetbrains.kotlin.types.FlexibleTypesKt;
import org.jetbrains.kotlin.types.KotlinType;
import org.jetbrains.kotlin.types.TypeConstructor;
import org.jetbrains.kotlin.types.TypeProjection;

import java.util.Collection;

public class ForceResolveUtil {
    private static final Logger LOG = Logger.getInstance(ForceResolveUtil.class);

    private ForceResolveUtil() {}

    public static <T> T forceResolveAllContents(@NotNull T descriptor) {
        return forceResolveAllContents(descriptor, true);
    }

    public static <T> T forceResolveAllContents(
            @NotNull T descriptor,
            boolean forceReturnType
    ) {
        doForceResolveAllContents(descriptor, forceReturnType);
        return descriptor;
    }

    public static void forceResolveAllContents(@NotNull MemberScope scope) {
        forceResolveAllContents(DescriptorUtils.getAllDescriptors(scope));
    }

    public static void forceResolveAllContents(@NotNull Iterable<? extends DeclarationDescriptor> descriptors) {
        forceResolveAllContents(descriptors, true);
    }

    public static void forceResolveAllContents(
            @NotNull Iterable<? extends DeclarationDescriptor> descriptors,
            boolean forceReturnType
    ) {
        for (DeclarationDescriptor descriptor : descriptors) {
            forceResolveAllContents(descriptor, forceReturnType);
        }
    }

    public static void forceResolveAllContents(@NotNull Collection<KotlinType> types) {
        for (KotlinType type : types) {
            forceResolveAllContents(type);
        }
    }

    public static void forceResolveAllContents(@NotNull TypeConstructor typeConstructor) {
        forceResolveAllContents(typeConstructor, true);
    }

    public static void forceResolveAllContents(
            @NotNull TypeConstructor typeConstructor,
            boolean forceReturnType
    ) {
        doForceResolveAllContents(typeConstructor, forceReturnType);
    }

    public static void forceResolveAllContents(@NotNull Annotations annotations) {
        forceResolveAllContents(annotations, true);
    }

    public static void forceResolveAllContents(
            @NotNull Annotations annotations,
            boolean forceReturnType
    ) {
        doForceResolveAllContents(annotations, forceReturnType);
        for (AnnotationWithTarget annotationWithTarget : annotations.getAllAnnotations()) {
            doForceResolveAllContents(annotationWithTarget.getAnnotation(), forceReturnType);
        }
    }

    private static void doForceResolveAllContents(Object object, boolean forceReturnType) {
        if (object instanceof LazyEntity) {
            LazyEntity lazyEntity = (LazyEntity) object;
            lazyEntity.forceResolveAllContents(forceReturnType);
        }
        else if (object instanceof ValueParameterDescriptorImpl.WithDestructuringDeclaration) {
            ((ValueParameterDescriptorImpl.WithDestructuringDeclaration) object).getDestructuringVariables();
        }
        else if (object instanceof CallableDescriptor) {
            CallableDescriptor callableDescriptor = (CallableDescriptor) object;
            ReceiverParameterDescriptor parameter = callableDescriptor.getExtensionReceiverParameter();
            if (parameter != null) {
                forceResolveAllContents(parameter.getType());
            }
            for (ValueParameterDescriptor parameterDescriptor : callableDescriptor.getValueParameters()) {
                forceResolveAllContents(parameterDescriptor);
            }
            for (TypeParameterDescriptor typeParameterDescriptor : callableDescriptor.getTypeParameters()) {
                forceResolveAllContents(typeParameterDescriptor.getUpperBounds());
            }

            if (forceReturnType) {
                forceResolveAllContents(callableDescriptor.getReturnType());
            }

            forceResolveAllContents(callableDescriptor.getAnnotations());
        }
        else if (object instanceof TypeAliasDescriptor) {
            TypeAliasDescriptor typeAliasDescriptor = (TypeAliasDescriptor) object;
            forceResolveAllContents(typeAliasDescriptor.getUnderlyingType());
        }
    }

    @Nullable
    public static KotlinType forceResolveAllContents(@Nullable KotlinType type) {
        if (type == null) return null;

        forceResolveAllContents(type.getAnnotations());
        if (FlexibleTypesKt.isFlexible(type)) {
            forceResolveAllContents(FlexibleTypesKt.asFlexibleType(type).getLowerBound());
            forceResolveAllContents(FlexibleTypesKt.asFlexibleType(type).getUpperBound());
        }
        else {
            forceResolveAllContents(type.getConstructor());
            for (TypeProjection projection : type.getArguments()) {
                if (!projection.isStarProjection()) {
                    forceResolveAllContents(projection.getType());
                }
            }
        }
        return type;
    }
}

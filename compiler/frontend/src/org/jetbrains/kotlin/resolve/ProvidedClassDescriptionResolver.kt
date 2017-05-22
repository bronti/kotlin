/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.util.OperatorNameConventions

object ProvidedClassDescriptionResolver {
    val GET_ONE_METHOD_NAME = Name.identifier("getOne")

    fun createGetOneFunctionDescriptor(classDescriptor: ClassDescriptor, trace: BindingTrace): SimpleFunctionDescriptor {
        val functionDescriptor = SimpleFunctionDescriptorImpl.create(
                classDescriptor,
                Annotations.EMPTY,
                GET_ONE_METHOD_NAME,
                CallableMemberDescriptor.Kind.SYNTHESIZED,
                classDescriptor.source
        )
        val parameterDescriptors = arrayListOf<ValueParameterDescriptor>()
        val returnType = classDescriptor.builtIns.getPrimitiveKotlinType(PrimitiveType.INT)

        functionDescriptor.initialize(
                null,
                classDescriptor.thisAsReceiverParameter,
                emptyList<TypeParameterDescriptor>(),
                parameterDescriptors,
                returnType,
                Modality.FINAL,
                Visibilities.PUBLIC
        )

        trace.record(BindingContext.PROVIDED_CLASS_COPY_FUNCTION, classDescriptor, functionDescriptor)
        return functionDescriptor
    }
}

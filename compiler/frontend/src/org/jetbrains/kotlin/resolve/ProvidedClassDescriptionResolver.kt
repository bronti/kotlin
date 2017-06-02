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

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.TextNode
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.annotations.isInlineOnly
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.util.OperatorNameConventions

object ProvidedClassDescriptionResolver {

//    fun createGetOneFunctionDescriptor(classDescriptor: ClassDescriptor, trace: BindingTrace): SimpleFunctionDescriptor {
//        val functionDescriptor = SimpleFunctionDescriptorImpl.create(
//                classDescriptor,
//                Annotations.EMPTY,
//                GET_ONE_METHOD_NAME,
//                CallableMemberDescriptor.Kind.SYNTHESIZED,
//                classDescriptor.source
//        )
//        val parameterDescriptors = arrayListOf<ValueParameterDescriptor>()
//        val returnType = classDescriptor.builtIns.getPrimitiveKotlinType(PrimitiveType.INT)
//
//        functionDescriptor.initialize(
//                null,
//                classDescriptor.thisAsReceiverParameter,
//                emptyList<TypeParameterDescriptor>(),
//                parameterDescriptors,
//                returnType,
//                Modality.FINAL,
//                Visibilities.PUBLIC
//        )
//
//        trace.record(BindingContext.PROVIDED_CLASS_GET_ONE_FUNCTION, classDescriptor, functionDescriptor)
//        return functionDescriptor
//    }

    private fun getJsonValueType(value: JsonNode, classDescriptor: ClassDescriptor) = when (value) {
        is TextNode -> classDescriptor.builtIns.stringType
        is IntNode -> classDescriptor.builtIns.intType
        is BooleanNode -> classDescriptor.builtIns.intType
        else -> throw AssertionError("JsonNode type inside JsonNode in property description generation")
    }.makeNullableAsSpecified(true) //todo: remove nullable

    fun createFromJsonPropertyDescriptor(name: Name, value: JsonNode, classDescriptor: ClassDescriptor, trace: BindingTrace): PropertyDescriptor {
        val propertyDescriptor = PropertyDescriptorImpl.create(
                classDescriptor,
                Annotations.EMPTY,
                Modality.FINAL,
                Visibilities.PUBLIC,
                /* isVar = */ true,
                name,
                CallableMemberDescriptor.Kind.SYNTHESIZED,
                classDescriptor.source,
                /* lateInit = */ false, /* isConst = */ false,
                /* isHeader = */ false, /* isImpl = */ false,
                /* isExternal = */ false, /* isDelegated = */ false
        )

        val type = getJsonValueType(value, classDescriptor)
        val typeParameters = emptyList<TypeParameterDescriptor>()

        val dispatchReceiverParameter = classDescriptor.thisAsReceiverParameter
        val extensionReceiverParameter: ReceiverParameterDescriptor? = null

        propertyDescriptor.setType(type, typeParameters, dispatchReceiverParameter, extensionReceiverParameter)

        val getter = DescriptorFactory.createDefaultGetter(propertyDescriptor, Annotations.EMPTY)
        getter.initialize(propertyDescriptor.type)
        val setter = DescriptorFactory.createDefaultSetter(propertyDescriptor, Annotations.EMPTY)
//        val setter: PropertySetterDescriptor? = null

        propertyDescriptor.initialize(getter, setter)

        trace.record(BindingContext.PROVIDED_FROM_JSON_PROPERTY, Pair(classDescriptor, name), propertyDescriptor)
        return propertyDescriptor
    }
}

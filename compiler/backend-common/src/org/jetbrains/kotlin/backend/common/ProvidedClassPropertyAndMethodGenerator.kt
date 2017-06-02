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

package org.jetbrains.kotlin.backend.common

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContextUtils
import org.jetbrains.kotlin.resolve.ProvidedClassDescriptionResolver
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassMemberScope

import com.fasterxml.jackson.databind.JsonNode


/**
 * Logic for generatind provided class.
 */
abstract class ProvidedClassPropertyAndMethodGenerator(private val declaration: KtClassOrObject, private val bindingContext: BindingContext) {

    protected val classDescriptor: ClassDescriptor = BindingContextUtils.getNotNull(bindingContext, BindingContext.CLASS, declaration)

    fun generate() {
        doGenerateFromJsonProperties()
    }

    protected abstract fun generateFunction(function: FunctionDescriptor)

    protected abstract fun generateProperty(property: PropertyDescriptor, name: Name)

//    private fun doGenerateGetOneFunction() {
//        val function = bindingContext.get(BindingContext.PROVIDED_CLASS_GET_ONE_FUNCTION, classDescriptor) ?: return
//        generateFunction(function)
//    }

    private fun doGenerateFromJsonProperties() {
        //todo: NOT GUT!
        val model = (classDescriptor.unsubstitutedMemberScope as LazyClassMemberScope).getJsonNodeForProvided()

        for (field in model.fields()) {
            //todo: proper error
//            if (field.value is JsonNode) continue
            val property = bindingContext.get(BindingContext.PROVIDED_FROM_JSON_PROPERTY,
                                              Pair(classDescriptor, Name.identifier(field.key))) ?: return
            generateProperty(property, property.name)
        }
    }

}


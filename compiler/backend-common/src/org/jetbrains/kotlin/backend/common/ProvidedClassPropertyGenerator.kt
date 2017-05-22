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

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContextUtils
import org.jetbrains.kotlin.resolve.ProvidedClassDescriptionResolver


/**
 * Logic for generatind provided class.
 */
abstract class ProvidedClassPropertyGenerator(private val declaration: KtClassOrObject, private val bindingContext: BindingContext) {
    protected val classDescriptor: ClassDescriptor = BindingContextUtils.getNotNull(bindingContext, BindingContext.CLASS, declaration)

    fun generate() {
        generateGetter()
    }

    protected abstract fun generateGetterFunction(function: FunctionDescriptor)

    private fun generateGetter() {
        val function = CodegenUtil.getMemberToGenerate(classDescriptor, ProvidedClassDescriptionResolver.GET_ONE_METHOD_NAME.identifier, KotlinBuiltIns::isString, List<ValueParameterDescriptor>::isEmpty) ?: return
        generateGetterFunction(function)
    }

}


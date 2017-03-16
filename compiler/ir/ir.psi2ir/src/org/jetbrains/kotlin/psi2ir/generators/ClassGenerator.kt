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

package org.jetbrains.kotlin.psi2ir.generators

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.*
import org.jetbrains.kotlin.ir.descriptors.IrImplementingDelegateDescriptorImpl
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.expressions.mapValueParameters
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.DescriptorRendererModifier
import org.jetbrains.kotlin.renderer.OverrideRenderingPolicy
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import java.lang.AssertionError
import java.util.*

class ClassGenerator(declarationGenerator: DeclarationGenerator) : DeclarationGeneratorExtension(declarationGenerator) {
    fun generateClass(ktClassOrObject: KtClassOrObject): IrClass {
        val descriptor = getOrFail(BindingContext.CLASS, ktClassOrObject)
        val irClass = IrClassImpl(ktClassOrObject.startOffset, ktClassOrObject.endOffset, IrDeclarationOrigin.DEFINED, descriptor)

        declarationGenerator.generateTypeParameterDeclarations(irClass, descriptor.declaredTypeParameters)

        generatePrimaryConstructor(irClass, ktClassOrObject)

        generatePropertiesDeclaredInPrimaryConstructor(irClass, ktClassOrObject)

        generateMembersDeclaredInSupertypeList(irClass, ktClassOrObject)

        generateMembersDeclaredInClassBody(irClass, ktClassOrObject)

        if (descriptor.isData) {
            generateAdditionalMembersForDataClass(irClass, ktClassOrObject)
        }

        if (descriptor.kind == ClassKind.ENUM_CLASS) {
            generateAdditionalMembersForEnumClass(irClass)
        }

        return irClass
    }

    private object StableDelegatesComparator : Comparator<CallableMemberDescriptor> {
        override fun compare(member1: CallableMemberDescriptor?, member2: CallableMemberDescriptor?): Int {
            if (member1 == member2) return 0
            if (member1 == null) return -1
            if (member2 == null) return 1

            val image1 = DESCRIPTOR_RENDERER.render(member1)
            val image2 = DESCRIPTOR_RENDERER.render(member2)
            return image1.compareTo(image2)
        }

        private val DESCRIPTOR_RENDERER = DescriptorRenderer.withOptions {
            withDefinedIn = false
            overrideRenderingPolicy = OverrideRenderingPolicy.RENDER_OPEN_OVERRIDE
            includePropertyConstant = true
            classifierNamePolicy = ClassifierNamePolicy.FULLY_QUALIFIED
            verbose = true
            modifiers = DescriptorRendererModifier.ALL
        }
    }

    private fun generateMembersDeclaredInSupertypeList(irClass: IrClass, ktClassOrObject: KtClassOrObject) {
        ktClassOrObject.getSuperTypeList()?.let { ktSuperTypeList ->
            val delegatedMembers = irClass.descriptor.unsubstitutedMemberScope
                    .getContributedDescriptors(DescriptorKindFilter.CALLABLES)
                    .filterIsInstance<CallableMemberDescriptor>()
                    .filter { it.kind == CallableMemberDescriptor.Kind.DELEGATION }
                    .sortedWith(StableDelegatesComparator)
            if (delegatedMembers.isEmpty()) return

            for (ktEntry in ktSuperTypeList.entries) {
                if (ktEntry is KtDelegatedSuperTypeEntry) {
                    generateDelegatedImplementationMembers(irClass, ktEntry, delegatedMembers)
                }
            }
        }
    }

    private fun generateDelegatedImplementationMembers(
            irClass: IrClass,
            ktEntry: KtDelegatedSuperTypeEntry,
            delegatedMembers: List<CallableMemberDescriptor>
    ) {
        val ktDelegateExpression = ktEntry.delegateExpression!!
        val delegateType = getInferredTypeWithImplicitCastsOrFail(ktDelegateExpression)
        val superType = getOrFail(BindingContext.TYPE, ktEntry.typeReference!!)
        val superTypeConstructorDescriptor = superType.constructor.declarationDescriptor
        val superClass = superTypeConstructorDescriptor as? ClassDescriptor ?:
                         throw AssertionError("Unexpected supertype constructor for delegation: $superTypeConstructorDescriptor")
        val delegateDescriptor = IrImplementingDelegateDescriptorImpl(irClass.descriptor, delegateType, superType)
        val irDelegate = IrFieldImpl(ktDelegateExpression.startOffset, ktDelegateExpression.endOffset, IrDeclarationOrigin.DELEGATE,
                                     delegateDescriptor)
        val bodyGenerator = BodyGenerator(irClass.descriptor, context)
        irDelegate.initializer = bodyGenerator.generateExpressionBody(ktDelegateExpression)
        irClass.addMember(irDelegate)

        for (delegatedMember in delegatedMembers) {
            val overriddenMember = delegatedMember.overriddenDescriptors.find { it.containingDeclaration.original == superClass.original }
            if (overriddenMember != null) {
                generateDelegatedMember(irClass, irDelegate, delegatedMember, overriddenMember)
            }
        }
    }

    private fun generateDelegatedMember(irClass: IrClass, irDelegate: IrField,
                                        delegatedMember: CallableMemberDescriptor, overriddenMember: CallableMemberDescriptor) {
        when (delegatedMember) {
            is FunctionDescriptor ->
                generateDelegatedFunction(irClass, irDelegate, delegatedMember, overriddenMember as FunctionDescriptor)
            is PropertyDescriptor ->
                generateDelegatedProperty(irClass, irDelegate, delegatedMember, overriddenMember as PropertyDescriptor)
        }

    }

    private fun generateDelegatedProperty(irClass: IrClass, irDelegate: IrField, delegated: PropertyDescriptor, overridden: PropertyDescriptor) {
        irClass.addMember(generateDelegatedProperty(irDelegate, delegated, overridden))
    }

    private fun generateDelegatedProperty(irDelegate: IrField, delegated: PropertyDescriptor, overridden: PropertyDescriptor): IrPropertyImpl {
        val startOffset = irDelegate.startOffset
        val endOffset = irDelegate.endOffset

        val irProperty = IrPropertyImpl(startOffset, endOffset, IrDeclarationOrigin.DELEGATED_MEMBER, false, delegated)

        irProperty.getter = generateDelegatedFunction(irDelegate, delegated.getter!!, overridden.getter!!)

        if (delegated.isVar) {
            irProperty.setter = generateDelegatedFunction(irDelegate, delegated.setter!!, overridden.setter!!)
        }
        return irProperty
    }

    private fun generateDelegatedFunction(irClass: IrClass, irDelegate: IrField, delegated: FunctionDescriptor, overridden: FunctionDescriptor) {
        irClass.addMember(generateDelegatedFunction(irDelegate, delegated, overridden))
    }

    private fun generateDelegatedFunction(irDelegate: IrField, delegated: FunctionDescriptor, overridden: FunctionDescriptor): IrFunction {
        val irFunction = IrFunctionImpl(irDelegate.startOffset, irDelegate.endOffset, IrDeclarationOrigin.DELEGATED_MEMBER, delegated)
        FunctionGenerator(declarationGenerator).generateSyntheticFunctionParameterDeclarations(irFunction)
        irFunction.body = generateDelegateFunctionBody(irDelegate, delegated, overridden)
        return irFunction
    }

    private fun generateDelegateFunctionBody(irDelegate: IrField, delegated: FunctionDescriptor, overridden: FunctionDescriptor): IrBlockBodyImpl {
        val startOffset = irDelegate.startOffset
        val endOffset = irDelegate.endOffset
        val dispatchReceiver = delegated.dispatchReceiverParameter ?:
                               throw AssertionError("Delegated member should have a dispatch receiver: $delegated")

        val irBlockBody = IrBlockBodyImpl(startOffset, endOffset)
        val returnType = overridden.returnType!!
        val irCall = IrCallImpl(startOffset, endOffset, returnType, overridden, null)
        irCall.dispatchReceiver = IrGetFieldImpl(startOffset, endOffset, irDelegate.descriptor,
                                                 IrGetValueImpl(startOffset, endOffset, dispatchReceiver)
                                                 )
        irCall.extensionReceiver = delegated.extensionReceiverParameter?.let { extensionReceiver ->
            IrGetValueImpl(startOffset, endOffset, extensionReceiver)
        }
        irCall.mapValueParameters { overriddenValueParameter ->
            val delegatedValueParameter = delegated.valueParameters[overriddenValueParameter.index]
            IrGetValueImpl(startOffset, endOffset, delegatedValueParameter)
        }
        if (KotlinBuiltIns.isUnit(returnType) || KotlinBuiltIns.isNothing(returnType)) {
            irBlockBody.statements.add(irCall)
        }
        else {
            val irReturn = IrReturnImpl(startOffset, endOffset, context.builtIns.nothingType, delegated, irCall)
            irBlockBody.statements.add(irReturn)
        }
        return irBlockBody
    }

    private fun generateAdditionalMembersForDataClass(irClass: IrClass, ktClassOrObject: KtClassOrObject) {
        DataClassMembersGenerator(declarationGenerator).generate(ktClassOrObject, irClass)
    }

    private fun generateAdditionalMembersForEnumClass(irClass: IrClass) {
        EnumClassMembersGenerator(context).generateSpecialMembers(irClass)
    }

    private fun generatePrimaryConstructor(irClass: IrClass, ktClassOrObject: KtClassOrObject) {
        val classDescriptor = irClass.descriptor
        if (DescriptorUtils.isAnnotationClass(classDescriptor)) return

        val primaryConstructorDescriptor = classDescriptor.unsubstitutedPrimaryConstructor ?: return

        val irPrimaryConstructor = FunctionGenerator(declarationGenerator).generatePrimaryConstructor(primaryConstructorDescriptor, ktClassOrObject)

        irClass.addMember(irPrimaryConstructor)
    }

    private fun generatePropertiesDeclaredInPrimaryConstructor(irClass: IrClass, ktClassOrObject: KtClassOrObject) {
        ktClassOrObject.primaryConstructor?.let { ktPrimaryConstructor ->
            for (ktParameter in ktPrimaryConstructor.valueParameters) {
                if (ktParameter.hasValOrVar()) {
                    irClass.addMember(PropertyGenerator(declarationGenerator).generatePropertyForPrimaryConstructorParameter(ktParameter))
                }
            }
        }
    }

    private fun generateMembersDeclaredInClassBody(irClass: IrClass, ktClassOrObject: KtClassOrObject) {
        ktClassOrObject.getBody()?.let { ktClassBody ->
            ktClassBody.declarations.mapTo(irClass.declarations) { ktDeclaration ->
                declarationGenerator.generateClassMemberDeclaration(ktDeclaration, irClass.descriptor)
            }
        }
    }

    fun generateEnumEntry(ktEnumEntry: KtEnumEntry): IrEnumEntry {
        val enumEntryDescriptor = getOrFail(BindingContext.CLASS, ktEnumEntry)
        val irEnumEntry = IrEnumEntryImpl(ktEnumEntry.startOffset, ktEnumEntry.endOffset, IrDeclarationOrigin.DEFINED, enumEntryDescriptor)

        irEnumEntry.initializerExpression =
                BodyGenerator(enumEntryDescriptor.containingDeclaration, context)
                        .generateEnumEntryInitializer(ktEnumEntry, enumEntryDescriptor)

        if (ktEnumEntry.declarations.isNotEmpty()) {
            irEnumEntry.correspondingClass = generateClass(ktEnumEntry)
        }

        return irEnumEntry
    }
}

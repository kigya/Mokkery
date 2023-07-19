package dev.mokkery.plugin.ext

import dev.mokkery.plugin.Kotlin
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irCatch
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.jvm.ir.kClassReference
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildVariable
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.parent
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrIfThenElseImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.defaultConstructor
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.invokeFun
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.konan.isNative

fun IrBuilderWithScope.kClassReferenceUnified(context: IrPluginContext, classType: IrType): IrClassReference {
    // for some reason IrClassReference is compiled differently on K/N
    // this 'if' solves the problem
    return if (context.platform!!.isNative()) {
        IrClassReferenceImpl(startOffset, endOffset, classType, classType.classifierOrFail, classType)
    } else {
        kClassReference(classType)
    }
}

fun IrBuilderWithScope.irGetEnumEntry(irClass: IrClass, name: String): IrGetEnumValue {
    return IrGetEnumValueImpl(startOffset, endOffset, irClass.defaultType, irClass.getEnumEntry(name).symbol)
}

fun IrBuilderWithScope.irCallHashCode(irClass: IrClass) = context
    .irBuiltIns
    .anyClass
    .getSimpleFunction("hashCode")!!
    .let { irCall(it) }
    .apply {
        dispatchReceiver = irGet(irClass.thisReceiver!!)
    }

fun IrBuilderWithScope.irToString(
    pluginContext: IrPluginContext,
    expression: IrExpression,
    radix: Int,
) = pluginContext
    .referenceFunctions(Kotlin.FunctionId.toString)
    .first { it.owner.extensionReceiverParameter?.type == context.irBuiltIns.intType }
    .let {
        irCall(it).apply {
            extensionReceiver = expression
            putValueArgument(0, irInt(radix))
        }
    }

fun IrBuilderWithScope.irCallConstructor(constructor: IrConstructor) =
    irCallConstructor(constructor.symbol, emptyList())

fun IrBuilderWithScope.irTryCatchAny(expression: IrExpression): IrTry {
    val e = buildVariable(
        parent = parent,
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        origin = IrDeclarationOrigin.CATCH_PARAMETER,
        name = Name.identifier("e"),
        type = context.irBuiltIns.throwableType
    )
    return irTry(expression.type.makeNullable(), expression, catches = listOf(irCatch(e, irNull())), null)
}

fun IrBuilderWithScope.irDelegatingDefaultConstructorOrAny(irClass: IrClass): IrDelegatingConstructorCall {
    return irDelegatingConstructorCall(
        irClass.defaultConstructor ?: context.irBuiltIns.anyClass.owner.primaryConstructor!!
    )
}

fun IrBuilderWithScope.irIfNotNull(arg: IrExpression, then: IrExpression): IrIfThenElseImpl {
    return irIfThen(
        condition = irNot(irEqualsNull(argument = arg)),
        thenPart = then
    )
}
fun IrBuilderWithScope.irLambda(
    returnType: IrType,
    lambdaType: IrType,
    parent: IrDeclarationParent,
    block: IrBlockBodyBuilder.(IrSimpleFunction) -> Unit
): IrFunctionExpression {
    val lambda = context.irFactory.buildFun {
        this.startOffset = UNDEFINED_OFFSET
        this.endOffset = UNDEFINED_OFFSET
        this.name = Name.identifier("invoke")
        this.returnType = returnType
        visibility = DescriptorVisibilities.LOCAL
        origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
    }.apply {
        val bodyBuilder = DeclarationIrBuilder(context, symbol, startOffset, endOffset)
        this.copyParametersFrom(lambdaType.getClass()!!.invokeFun!!)
        this.parent = parent
        body = bodyBuilder.irBlockBody {
            block(this@apply)
        }
    }
    return IrFunctionExpressionImpl(
        UNDEFINED_OFFSET,
        UNDEFINED_OFFSET,
        lambdaType,
        lambda,
        IrStatementOrigin.LAMBDA
    )
}

fun IrBuilderWithScope.irInvokeIfNotNull(function: IrExpression, vararg args: IrExpression): IrIfThenElseImpl {
    return irIfNotNull(
        function,
        irInvoke(function, *args)
    )
}

fun IrBuilderWithScope.irInvoke(function: IrExpression, vararg args: IrExpression): IrFunctionAccessExpression {
    return irCall(context.irBuiltIns.functionN(args.size).invokeFun!!).apply {
        dispatchReceiver = function
        args.forEachIndexed { index, arg ->
            putValueArgument(index, arg)
        }
    }
}

fun irDefaultValue(type: IrType) = IrConstImpl.defaultValueForType(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type)

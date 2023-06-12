package dev.mokkery.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class CallTrackingTransformer(
    private val pluginContext: IrPluginContext,
    private val mockTable: Map<IrClass, IrClass>,
) : IrElementTransformerVoid() {

    private val internalEveryFunction = MokkeryDeclarations.internalEvery(pluginContext)
    private val internalEverySuspendFunction = MokkeryDeclarations.internalEverySuspend(pluginContext)
    private val internalVerifyFunction = MokkeryDeclarations.internalVerify(pluginContext)
    private val internalVerifySuspendFunction = MokkeryDeclarations.internalVerifySuspend(pluginContext)

    override fun visitCall(expression: IrCall): IrExpression {
        val function = expression.symbol.owner
        return when (function.kotlinFqName) {
            MokkeryDeclarations.everyFunctionName -> transformEvery(expression, internalEveryFunction)
            MokkeryDeclarations.verifyFunctionName -> transformVerify(expression, internalVerifyFunction)
            MokkeryDeclarations.everySuspendFunctionName -> transformEvery(expression, internalEverySuspendFunction)
            MokkeryDeclarations.verifySuspendFunctionName -> transformVerify(expression, internalVerifySuspendFunction)
            else -> super.visitCall(expression)
        }
    }

    private fun transformEvery(expression: IrCall, function: IrSimpleFunctionSymbol): IrCall {
        val nestedTransformer = NestedCallsTransformer(mockTable)
        val block = expression.getValueArgument(0)!!
        block.transformChildren(nestedTransformer, null)
        return DeclarationIrBuilder(pluginContext, expression.symbol).run {
            irCall(function).apply {
                putValueArgument(0, irVararg(nestedTransformer.trackedExpressions.toList()))
                putValueArgument(1, block)
            }
        }
    }

    private fun transformVerify(expression: IrCall, function: IrSimpleFunctionSymbol): IrCall {
        val nestedTransformer = NestedCallsTransformer(mockTable)
        val mode = expression.getValueArgument(0)
        val block = expression.getValueArgument(1)!!
        block.transformChildren(nestedTransformer, null)
        return DeclarationIrBuilder(pluginContext, expression.symbol).run {
            irCall(function).apply {
                putValueArgument(0, irVararg(nestedTransformer.trackedExpressions.toList()))
                putValueArgument(1, mode)
                putValueArgument(2, block)
            }
        }
    }
}

private class NestedCallsTransformer(
    private val mockTable: Map<IrClass, IrClass>
) : IrElementTransformerVoid() {

    private val _trackedExpressions = mutableSetOf<IrExpression>()
    val trackedExpressions: Set<IrExpression> = _trackedExpressions

    override fun visitCall(expression: IrCall): IrExpression {
        val dispatchReceiver = expression.dispatchReceiver ?: return super.visitCall(expression)
        if (!mockTable.containsKey(dispatchReceiver.type.getClass())) {
            return super.visitCall(expression)
        }
        _trackedExpressions.add(dispatchReceiver)
        // make return type nullable to avoid runtime checks on non-primitive types (e.g. suspend fun on K/N)
        if (!expression.type.isPrimitiveType(nullable = false)) {
            expression.type = expression.type.makeNullable()
        }
        return super.visitCall(expression)
    }
}

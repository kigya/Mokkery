package dev.mokkery.plugin.transformers

import dev.mokkery.plugin.Mokkery
import dev.mokkery.plugin.ext.firstFunction
import dev.mokkery.plugin.ext.getIrClassOf
import dev.mokkery.plugin.ext.irCallConstructor
import dev.mokkery.plugin.ext.irVararg
import dev.mokkery.plugin.mokkeryError
import dev.mokkery.plugin.warningAt
import dev.mokkery.verify.SoftVerifyMode
import dev.mokkery.verify.VerifyMode
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class CallTrackingTransformer(
    private val messageCollector: MessageCollector,
    private val irFile: IrFile,
    private val pluginContext: IrPluginContext,
    private val table: Map<IrClass, IrClass>,
    private val verifyMode: VerifyMode,
) : IrElementTransformerVoid() {

    private val irFunctions = IrFunctions()

    override fun visitCall(expression: IrCall): IrExpression {
        val function = expression.symbol.owner
        return when (function.kotlinFqName) {
            Mokkery.Function.every -> transformEvery(expression, irFunctions.internalEvery)
            Mokkery.Function.verify -> transformVerify(expression, irFunctions.internalVerify)
            Mokkery.Function.everySuspend -> transformEvery(expression, irFunctions.internalEverySuspend)
            Mokkery.Function.verifySuspend -> transformVerify(expression, irFunctions.internalVerifySuspend)
            else -> super.visitCall(expression)
        }
    }

    private fun transformEvery(expression: IrCall, function: IrSimpleFunctionSymbol): IrCall {
        val nestedTransformer = CallTrackingNestedTransformer(table)
        val block = expression.getValueArgument(0)!!
        block.assertFunctionExpressionThatOriginatesLambda()
        block.transformChildren(nestedTransformer, null)
        if (nestedTransformer.trackedExpressions.isEmpty()) {
            messageCollector.warningAt(block, irFile) { "Block does not refer to any mock!" }
        }
        return DeclarationIrBuilder(pluginContext, expression.symbol).run {
            irCall(function).apply {
                putValueArgument(0, irVararg(nestedTransformer.trackedExpressions.toList()))
                putValueArgument(1, block)
            }
        }
    }

    private fun transformVerify(expression: IrCall, function: IrSimpleFunctionSymbol): IrCall {
        val nestedTransformer = CallTrackingNestedTransformer(table)
        val mode = expression.getValueArgument(0)
        val block = expression.getValueArgument(1)!!
        block.assertFunctionExpressionThatOriginatesLambda()
        block.transformChildren(nestedTransformer, null)
        if (nestedTransformer.trackedExpressions.isEmpty()) {
            messageCollector.warningAt(block, irFile) { "Block does not refer to any mock!" }
        }
        return DeclarationIrBuilder(pluginContext, expression.symbol).run {
            irCall(function).apply {
                putValueArgument(0, irVararg(nestedTransformer.trackedExpressions.toList()))
                putValueArgument(1, mode ?: irGetVerifyMode(verifyMode))
                putValueArgument(2, block)
            }
        }
    }

    private fun IrBuilderWithScope.irGetVerifyMode(verifyMode: VerifyMode): IrExpression {
        val expression = when (verifyMode) {
            is SoftVerifyMode -> {
                irCallConstructor(pluginContext.getIrClassOf(SoftVerifyMode::class).primaryConstructor!!).apply {
                    putValueArgument(0, irInt(verifyMode.atLeast))
                    putValueArgument(1, irInt(verifyMode.atMost))
                }
            }
            else -> irGetObject(pluginContext.getIrClassOf(verifyMode::class).symbol)
        }
        return expression
    }

    private fun IrExpression.assertFunctionExpressionThatOriginatesLambda() {
        MessageCollector
        if (this !is IrFunctionExpression) mokkeryError(irFile) { "Block of 'verify' and 'every' must be a lambda! " }
    }

    inner class IrFunctions {
        val internalEvery = pluginContext.firstFunction(Mokkery.FunctionId.internalEvery)
        val internalEverySuspend = pluginContext.firstFunction(Mokkery.FunctionId.internalEverySuspend)
        val internalVerify = pluginContext.firstFunction(Mokkery.FunctionId.internalVerify)
        val internalVerifySuspend = pluginContext.firstFunction(Mokkery.FunctionId.internalVerifySuspend)
    }
}


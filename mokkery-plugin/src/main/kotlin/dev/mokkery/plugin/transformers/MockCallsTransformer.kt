package dev.mokkery.plugin.transformers

import dev.mokkery.MockMode
import dev.mokkery.plugin.Mokkery
import dev.mokkery.plugin.ext.buildClass
import dev.mokkery.plugin.ext.createUniqueMockName
import dev.mokkery.plugin.ext.defaultTypeErased
import dev.mokkery.plugin.ext.eraseFullValueParametersList
import dev.mokkery.plugin.ext.irCallConstructor
import dev.mokkery.plugin.ext.irGetEnumEntry
import dev.mokkery.plugin.ext.irInvokeIfNotNull
import dev.mokkery.plugin.ext.overrideAllOverridableFunctions
import dev.mokkery.plugin.ext.overrideAllOverridableProperties
import dev.mokkery.plugin.mokkeryError
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.render

class MockCallsTransformer(
    pluginContext: IrPluginContext,
    messageCollector: MessageCollector,
    irFile: IrFile,
    private val mockTable: MutableMap<IrClass, IrClass>,
    private val mockMode: MockMode,
) : MokkeryBaseTransformer(pluginContext, messageCollector, irFile) {

    override fun visitCall(expression: IrCall): IrExpression {
        val function = expression.symbol.owner
        if (function.kotlinFqName != Mokkery.Function.mock) return super.visitCall(expression)
        expression.checkInterceptionPossibilities(Mokkery.Function.mock)
        val typeToMock = expression.typeArguments.first()!!
        val classToMock = typeToMock.getClass()!!
        val mockedClass = mockTable.getOrPut(classToMock) {
            declareMock(classToMock).also {
                irFile.addChild(it)
            }
        }
        return DeclarationIrBuilder(pluginContext, expression.symbol).run {
            irCallConstructor(mockedClass.primaryConstructor!!).also {
                val modeArg = expression.valueArguments
                    .getOrNull(0)
                    ?: irGetEnumEntry(irClasses.MockMode, mockMode.toString())
                val block = expression.valueArguments.getOrNull(1)
                block?.applyMockCallsTransformerIfPossible()
                it.putValueArgument(0, modeArg)
                it.putValueArgument(1, block ?: irNull())
            }
        }
    }

    private fun declareMock(classToMock: IrClass): IrClass {
        val newClass = pluginContext.irFactory.buildClass(
            classToMock.createUniqueMockName("Mock"),
            classToMock.defaultTypeErased,
            irClasses.MokkeryMockScope.defaultType,
            if (classToMock.isInterface) pluginContext.irBuiltIns.anyType else null
        )
        newClass.inheritMokkeryInterceptor(
            interceptorScopeClass = irClasses.MokkeryMockScope,
            classToMock = classToMock,
            interceptorInit = { constructor ->
                constructor.addValueParameter("mode", irClasses.MockMode.defaultType)
                constructor.addValueParameter("block", context.irBuiltIns.functionN(1).defaultTypeErased)
                irCall(irFunctions.MokkeryMock).apply {
                    putValueArgument(1, irGet(constructor.valueParameters[0]))
                }
            },
            block = { +irInvokeIfNotNull(irGet(it.valueParameters[1]), irGet(newClass.thisReceiver!!)) }
        )
        newClass.overrideAllOverridableFunctions(pluginContext, classToMock) { mockBody(it) }
        newClass.overrideAllOverridableProperties(
            context = pluginContext,
            superClass = classToMock,
            getterBlock = { mockBody(it) },
            setterBlock = { mockBody(it) }
        )
        return newClass
    }

    private fun IrBlockBodyBuilder.mockBody(function: IrSimpleFunction) {
        function.eraseFullValueParametersList()
        +irReturn(irCallInterceptingMethod(function))
    }

    private fun IrExpression.applyMockCallsTransformerIfPossible() {
        if (this !is IrFunctionExpression) mokkeryError(irFile) { "Block of 'mock' must be a lambda expression! " }
        transformChildren(this@MockCallsTransformer, null)
    }
}

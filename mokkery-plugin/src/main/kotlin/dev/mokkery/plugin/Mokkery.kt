package dev.mokkery.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object Mokkery {

    private val mokkeryPackage = FqName("dev.mokkery")
    private val mokkeryInternalPackage = FqName("dev.mokkery.internal")

    val mockFunctionName = mokkeryPackage.child(Name.identifier("mock"))
    val everyFunctionName = mokkeryPackage.child(Name.identifier("every"))
    val everySuspendFunctionName = mokkeryPackage.child(Name.identifier("everySuspend"))
    val verifyFunctionName = mokkeryPackage.child(Name.identifier("verify"))
    val verifySuspendFunctionName = mokkeryPackage.child(Name.identifier("verifySuspend"))

    fun mokkeryMockClass(
        context: IrPluginContext
    ) = context
        .referenceClass(ClassId(mokkeryInternalPackage, Name.identifier("MokkeryMock")))!!

    fun mokkeryMockFunction(context: IrPluginContext) = context
        .referenceFunctions(CallableId(mokkeryInternalPackage, Name.identifier("MokkeryMock")))
        .first()

    fun mokkeryMockScopeClass(context: IrPluginContext) = context
        .referenceClass(ClassId(mokkeryInternalPackage, Name.identifier("MokkeryMockScope")))!!
        .owner

    fun internalEvery(context: IrPluginContext) = context
        .referenceFunctions(CallableId(mokkeryInternalPackage, Name.identifier("internalEvery")))
        .first()

    fun internalVerify(context: IrPluginContext) = context
        .referenceFunctions(CallableId(mokkeryInternalPackage, Name.identifier("internalVerify")))
        .first()


    fun internalEverySuspend(context: IrPluginContext) = context
        .referenceFunctions(CallableId(mokkeryInternalPackage, Name.identifier("internalEverySuspend")))
        .first()

    fun internalVerifySuspend(context: IrPluginContext) = context
        .referenceFunctions(CallableId(mokkeryInternalPackage, Name.identifier("internalVerifySuspend")))
        .first()

    fun mockModeDefault(context: IrPluginContext, builder: IrBuilderWithScope) = builder.run {
        val companion = mockModeClass(context).companionObject()!!
        irCall(companion.getPropertyGetter("Default")!!.owner).apply {
            dispatchReceiver = irGetObject(companion.symbol)
        }
    }

    fun mockModeClass(context: IrPluginContext) = context
        .referenceClass(ClassId(mokkeryPackage, Name.identifier("MockMode")))!!
        .owner
}

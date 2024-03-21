package com.momid.compiler.output

import com.momid.compiler.*
import com.momid.parser.expression.*

fun createGenericClassIfNotExists(currentGeneration: CurrentGeneration, genericClass: GenericClass): CStruct {
    val alreadyExists = currentGeneration.classesInformation.classes.entries.find {
        it.key == genericClass
    } != null

    val cStructVariables = ArrayList<CStructVariable>()
    var cStruct = CStruct(currentGeneration.createCStructName(), cStructVariables)

    genericClass.variables.forEach {
        cStructVariables.add(CStructVariable(it.name, resolveType(it.type, currentGeneration)))
    }

    if (alreadyExists) {
        cStruct = currentGeneration.classesInformation.classes.entries.find {
            it.key == genericClass
        }!!.value!!
    } else {
        currentGeneration.classesInformation.classes[genericClass] = cStruct

        currentGeneration.globalDefinitionsGeneratedSource += cStruct(cStruct.name, cStruct.variables.map { Pair(it.name, cTypeName(it.type)) })
    }

    return cStruct
}

fun ExpressionResultsHandlerContext.createGenericFunctionIfNotExists(currentGeneration: CurrentGeneration, genericFunction: GenericFunction): Result<CFunction> {
    val alreadyExists = currentGeneration.functionsInformation.functionsInformation.entries.find {
        it.key == genericFunction
    } != null

    val cFunctionParameters = ArrayList<CFunctionParameter>()
    val cFunction: CFunction

    genericFunction.parameters.forEach {
        cFunctionParameters.add(CFunctionParameter(it.name, resolveType(it.type, currentGeneration)))
    }

    if (alreadyExists) {
        cFunction = currentGeneration.functionsInformation.functionsInformation.entries.find {
            it.key == genericFunction
        }!!.value!!
    } else {
        if (genericFunction.function is ClassFunction) {
            val functionScope = Scope()
            functionScope.scopeContext = FunctionContext(genericFunction)

            genericFunction.parameters.forEachIndexed { index, functionParameter ->
                val variableInformation = VariableInformation(
                    cFunctionParameters[index].name,
                    cFunctionParameters[index].type,
                    "",
                    functionParameter.name,
                    actualOutputType(functionParameter.type)
                )
                functionScope.variables.add(variableInformation)
            }

            functionScope.variables.add(
                VariableInformation(
                    "this_receiver",
                    resolveType(genericFunction.function.receiverType, currentGeneration),
                    "",
                    "this",
                    actualOutputType(genericFunction.function.receiverType)
                )
            )

            val cFunctionCode = continueStraight(
                ExpressionResult(
                    this.expressionResult.expression,
                    genericFunction.bodyRange
                )
            ) { handleCodeBlock(currentGeneration, functionScope) }.okOrReport {
                println(it.error)
                return it.to()
            }
            cFunction = CFunction(
                currentGeneration.createCFunctionName(),
                cFunctionParameters.apply {
                    this.add(
                        0,
                        CFunctionParameter(
                            "this_receiver",
                            resolveType(genericFunction.function.receiverType, currentGeneration)
                        )
                    )
                },
                resolveType(genericFunction.returnType, currentGeneration),
                cFunctionCode
            )
            currentGeneration.functionsInformation.functionsInformation[genericFunction] = cFunction

            currentGeneration.functionDeclarationsGeneratedSource += cFunction(
                cFunction.name,
                cFunction.parameters.map {
                    cTypeAndVariableName(it.type, it.name)
                },
                cTypeName(cFunction.returnType),
                cFunction.codeText.trim()
            ) + "\n"
        } else {
            val functionScope = Scope()
            functionScope.scopeContext = FunctionContext(genericFunction)

            genericFunction.parameters.forEachIndexed { index, functionParameter ->
                val variableInformation = VariableInformation(
                    cFunctionParameters[index].name,
                    cFunctionParameters[index].type,
                    "",
                    functionParameter.name,
                    actualOutputType(functionParameter.type)
                )
                functionScope.variables.add(variableInformation)
            }
            val cFunctionCode = continueStraight(
                ExpressionResult(
                    this.expressionResult.expression,
                    genericFunction.bodyRange
                )
            ) { handleCodeBlock(currentGeneration, functionScope) }.okOrReport {
                println(it.error)
                return it.to()
            }
            cFunction = CFunction(
                currentGeneration.createCFunctionName(),
                cFunctionParameters,
                resolveType(genericFunction.returnType, currentGeneration),
                cFunctionCode
            )
            currentGeneration.functionsInformation.functionsInformation[genericFunction] = cFunction

            currentGeneration.functionDeclarationsGeneratedSource += cFunction(
                cFunction.name,
                cFunction.parameters.map {
                    cTypeAndVariableName(it.type, it.name)
                },
                cTypeName(cFunction.returnType),
                cFunction.codeText.trim()
            ) + "\n"
        }
    }

    return Ok(cFunction)
}

/***
 * returns the actual type of a type parameter if its type is a type parameter. otherwise returns the type.
 */
fun actualOutputType(outputType: OutputType): OutputType {
    if (outputType is TypeParameterType) {
        return outputType.genericTypeParameter.substitutionType
            ?: throw (Throwable("type parameter should have been substituted here"))
    } else {
        return outputType
    }
}

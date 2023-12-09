package com.momid.compiler

import com.momid.compiler.output.*
import com.momid.compiler.terminal.printError
import com.momid.parser.expression.*
import com.momid.parser.not

val classVariableExp by lazy {
    variableNameO["variableName"] + spaces + ":" + spaces + outputTypeO["variableType"]
}

val classVariable by lazy {
    ignoreParentheses(condition { it != ',' && it != '}' })
}

val classVariables by lazy {
    spaces + inline(classVariable["classVariable"] + inline(some0(spaces + !"," + spaces + classVariable["classVariable"])) + spaces) + spaces
}

val klass by lazy {
    !"class" + space + variableNameO["className"] + spaces + insideOf("classInside", '{', '}')
}

fun ExpressionResultsHandlerContext.handleClass(currentGeneration: CurrentGeneration): Result<String> {
    this.expressionResult.isOf(klass) {
        val classVariablesOutput = ArrayList<ClassVariable>()

        val className = it["className"].tokens()

        val outputClass = Class(className, classVariablesOutput)

        currentGeneration.classesInformation.classes[outputClass] = null

        val classVariablesEvaluation = continueWithOne(it["classInside"].content, classVariables) { handleClassVariables(currentGeneration) }

        classVariablesEvaluation.handle({
            println(it.error)
            return it.to()
        }, {
            it.forEach {
                if (it.type == ClassType(outputClass)) {
                    printError("a class cannot have a variable of its own type. instead you can have a reference of that type")
                    return Error("a class cannot have a variable of its own type. instead you can have a reference of that type", this.expressionResult.range)
                }
                val classVariable = ClassVariable(it.name, it.type)
                classVariablesOutput.add(classVariable)
            }

            val outputStruct = CStruct(currentGeneration.createCStructName(), classVariablesOutput.map {
                CStructVariable(it.name, resolveType(it.type, currentGeneration))
            })

            currentGeneration.classesInformation.classes[outputClass] = outputStruct

            currentGeneration.globalDefinitionsGeneratedSource += cStruct(outputStruct.name, outputStruct.variables.map { Pair(it.name, it.type.name) }) + "\n"

            return Ok("")
        })
    }
    println("is not class")
    return Error("is not class", this.expressionResult.range)
}

fun ExpressionResultsHandlerContext.handleClassVariables(currentGeneration: CurrentGeneration): Result<List<ClassVariableEvaluation>> {
    this.expressionResult.isOf(classVariables) {
        var hasErrors = false
        val classVariables = ArrayList<ClassVariableEvaluation>()
        it.forEach {
            it.forEach {
                val classVariable = continueWithOne(it, classVariableExp) { handleClassVariable(currentGeneration) }

                classVariable.handle({
                    currentGeneration.errors.add(it)
                }, {
                    classVariables.add(it)
                })
            }
        }
        return Ok(classVariables)
    }
    println("is not class variables")
    return Error("is not class variables", this.expressionResult.range)
}

fun ExpressionResultsHandlerContext.handleClassVariable(currentGeneration: CurrentGeneration): Result<ClassVariableEvaluation> {
    this.expressionResult.isOf(classVariableExp) {
        val name = it["variableName"].tokens()
        val type = continueStraight(it["variableType"]) { handleOutputType(currentGeneration) }.okOrReport {
            println(it.error)
            return it.to()
        }
        return Ok(ClassVariableEvaluation(name, type))
    }
    return Error("is not class variable", this.expressionResult.range)
}

fun resolveClass(outputType: Class, currentGeneration: CurrentGeneration): CStruct {
    return currentGeneration.classesInformation.classes[outputType] ?: throw (Throwable("could not resolve corresponding CStruct to this class"))
}

fun resolveType(outputType: Class, currentGeneration: CurrentGeneration): Type {
    if (outputType == outputInt) {
        return Type.Int
    } else if (outputType == outputString) {
        return Type.CharArray
    } else {
        return Type(currentGeneration.classesInformation.classes[outputType]?.name ?: throw (Throwable("could not resolve corresponding CStruct to this class")))
    }
}

fun resolveType(outputType: OutputType, currentGeneration: CurrentGeneration): Type {
    if (outputType is ClassType) {
        return resolveType(outputType.outputClass, currentGeneration)
    } else {
        if (outputType is ReferenceType) {
            return CReferenceType(resolveType(outputType.actualType, currentGeneration))
        } else if (outputType is NorType) {
            return Type.Void
        } else {
            throw (Throwable("only class type is available currently"))
        }
    }
}

fun confirmTypeIsClassType(outputType: OutputType): ClassType {
    if (outputType is ClassType) {
        return outputType
    } else {
        throw (Throwable("only class type is available currently"))
    }
}

fun resolveType(outputTypeName: String, currentGeneration: CurrentGeneration): Class? {
    val outputClass = currentGeneration.classesInformation.classes.entries.find {
        it.key.name == outputTypeName
    }?.key.also {
        if (it == null) {
            println("class with this name not found: " + outputTypeName)
        }
    }
    if (outputClass is GenericClass) {
        return outputClass.clone()
    } else {
        return outputClass
    }
}

inline fun <T> Result<T>.handle(error: (Error<T>) -> Unit = {  }, ok: (T) -> Unit) {
    if (this is Ok) {
        ok(this.ok)
    }

    if (this is Error) {
        error(this)
    }
}

class ClassVariableS(val name: String, val type: String)

class ClassVariableEvaluation(val name: String, val type: OutputType)

fun main() {
    val currentGeneration = CurrentGeneration()
    val text = "class SomeClass { someVariable: SomeType, anotherVariable: AnotherType, someOtherVariable: SomeOtherType }".toList()
    val finder = ExpressionFinder()
    finder.registerExpressions(listOf(klass))
    finder.start(text).forEach {
        handleExpressionResult(finder, it, text) {
            handleClass(currentGeneration)
        }
    }
}

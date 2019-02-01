package lang.taxi.typescript

import lang.taxi.CompilationUnit
import lang.taxi.Type
import lang.taxi.services.Operation
import lang.taxi.services.Service
import lang.taxi.types.*
import me.ntrrgc.tsGenerator.TypeScriptGenerator
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.charset.Charset
import java.time.Instant

const val DEFAULT_PATH = "../../src/schema.ts"

object ErrorFixes {
    val replacements = listOf<Pair<String, String>>(
            "interface TypeAlias extends UserType," to "interface TypeAlias extends UserType<TypeAliasDefinition,TypeAliasExtension>,",
            "interface ObjectType extends UserType," to "interface ObjectType extends UserType<ObjectTypeDefinition, ObjectTypeExtension>,",
            "interface EnumType extends UserType," to "interface EnumType extends UserType<EnumDefinition,EnumDefinition>,",

            // Remove references to CompilationUnit - since they're not really relevant
            "compilationUnits: CompilationUnit[];" to "",
            "compilationUnit: CompilationUnit;" to "",
            "source: CompilationUnit;" to ""


    )
}

/**
 * This program generates typescript definitions for the types used within the
 * Taxi compiler.
 *
 * The generated types are used by the Typescript SDK.
 *
 * At the time of writing, this program is invoked manually within the IDE.
 * We might add it to a CI / CD pipeline later.
 */
fun main(args: Array<String>) {
    val outputPath = args.getOrNull(0) ?: DEFAULT_PATH
    val generator = TypeScriptGenerator(
            rootClasses = setOf(
                    EnumType::class,
                    ArrayType::class,
                    Type::class,
                    ObjectType::class,
                    PrimitiveType::class,
                    TypeAlias::class,
                    Operation::class,
                    Service::class
            ),
            ignoreSuperclasses = setOf(
                    CompilationUnit::class
            )
    )
    val file = File(outputPath)
    val generated = generator.definitionsText
    val patched = ErrorFixes.replacements.foldRight(generated) { errorFix, definitionText ->
        val (errorToFix, replacement) = errorFix
        definitionText.replace(errorToFix, replacement)
    }

    val prefix = """
/**
 * This file is generated - do not edit.
 *
 * Generated at ${Instant.now()}
 *
 * To recreate, run the TypescriptEmitter program
 */

    """.trimIndent()


    val output = prefix + patched
    FileUtils.writeStringToFile(file, output, Charset.defaultCharset())
    println("Wrote definition file to ${file.canonicalPath}")
}
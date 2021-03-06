package lang.taxi.types

import arrow.core.Either
import lang.taxi.Equality
import lang.taxi.services.operations.constraints.Constraint
import lang.taxi.services.operations.constraints.ConstraintTarget
import kotlin.reflect.KProperty1

data class FieldExtension(
   val name: String,
   override val annotations: List<Annotation>,
   val refinedType: Type?,
   val defaultValue: Any?) : Annotatable
data class ObjectTypeExtension(val annotations: List<Annotation> = emptyList(),
                               val fieldExtensions: List<FieldExtension> = emptyList(),
                               val typeDoc: String? = null,
                               override val compilationUnit: CompilationUnit) : TypeDefinition {
   fun fieldExtensions(name: String): List<FieldExtension> {
      return this.fieldExtensions.filter { it.name == name }
   }
}

data class ObjectTypeDefinition(
   val fields: Set<Field> = emptySet(),
   val annotations: Set<Annotation> = emptySet(),
   val modifiers: List<Modifier> = emptyList(),
   val inheritsFrom: Set<Type> = emptySet(),
   val format: String? = null,
   val formattedInstanceOfType: Type? = null,
   override val typeDoc: String? = null,
   override val compilationUnit: CompilationUnit
) : TypeDefinition, Documented {
   private val equality = Equality(this, ObjectTypeDefinition::fields.toSet(), ObjectTypeDefinition::annotations.toSet(), ObjectTypeDefinition::modifiers.toSet())
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

internal fun <T, R> KProperty1<T, Collection<R>>.toSet(): T.() -> Set<R>? {
   val prop = this
   return {
      prop.get(this).toSet()
   }
}

enum class FieldModifier(val token: String) {
   CLOSED("closed")
}

enum class Modifier(val token: String) {

   // A Parameter type indicates that the object
// is used when constructing requests,
// and that frameworks should freely construct
// these types based on known values.
   PARAMETER_TYPE("parameter"),

   /**
    * Closed types can not be decomponsed into their individual parts,
    * they only make sense as a single, cohesive unit.
    */
   CLOSED("closed");

   companion object {
      fun fromToken(token: String): Modifier {
         return Modifier.values().first { it.token == token }
      }
   }

}

data class ObjectType(
   override val qualifiedName: String,
   override var definition: ObjectTypeDefinition?,
   override val extensions: MutableList<ObjectTypeExtension> = mutableListOf()
) : UserType<ObjectTypeDefinition, ObjectTypeExtension>, Annotatable, Documented {
   companion object {
      fun undefined(name: String): ObjectType {
         return ObjectType(name, definition = null)
      }
   }

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type>
      get() {
         return if (isDefined) wrapper.allInheritedTypes else emptySet()
      }
   override val baseEnum: EnumType?
      get() {
         return if (isDefined) wrapper.baseEnum else null
      }
   override val inheritsFromPrimitive: Boolean
      get() {
         return if (isDefined) wrapper.inheritsFromPrimitive else false
      }
   override val basePrimitive: PrimitiveType?
      get() {
         return if (isDefined) wrapper.basePrimitive else null
      }
   override val definitionHash: String?
      get() {
         return if (isDefined) wrapper.definitionHash else null
      }

   override val format: String?
      get() {
         return if (this.definition?.format != null) {
            this.definition?.format
         } else {
            val inheritedFormats = this.inheritsFrom.filter { it.format != null }
            when {
               inheritedFormats.isEmpty() -> null
               inheritedFormats.size == 1 -> inheritedFormats.first().format
               else -> error("Multiple formats found in inheritence - this is an error")
            }
         }
      }

   override val formattedInstanceOfType: Type?
      get() = this.definition?.formattedInstanceOfType

   override val referencedTypes: List<Type>
      get() {
         val fieldTypes = this.allFields.map { it.type }
         val inheritedTypes = this.definition?.inheritsFrom?.toList() ?: emptyList()
         return (fieldTypes + inheritedTypes).filterIsInstance<UserType<*, *>>()
      }

   override fun addExtension(extension: ObjectTypeExtension): Either<ErrorMessage, ObjectTypeExtension> {
      val error = verifyMaxOneTypeRefinementPerField(extension.fieldExtensions)
      if (error != null) return Either.left(error)
      this.extensions.add(extension)

      return Either.right(extension)
   }

   private fun verifyMaxOneTypeRefinementPerField(fieldExtensions: List<FieldExtension>): ErrorMessage? {
      fieldExtensions.filter { it.refinedType != null }
         .forEach { proposedExtension ->
            val existingRefinedType = fieldExtensions(proposedExtension.name).mapNotNull { it.refinedType }
            if (existingRefinedType.isNotEmpty() && proposedExtension.refinedType != null) {
               return "Cannot refinement field ${proposedExtension.name} to ${proposedExtension.refinedType!!.qualifiedName} as it has already been refined to ${existingRefinedType.first().qualifiedName}"
            }
         }
      return null;
   }

   override val inheritsFrom: Set<Type>
      get() {
         return definition?.inheritsFrom ?: emptySet()
      }

   val inheritsFromNames: List<String>
      get() {
         return inheritsFrom.map { it.qualifiedName }
      }

   override fun toString(): String {
      return qualifiedName
   }

   val modifiers: List<Modifier>
      get() {
         return this.definition?.modifiers ?: emptyList()
      }

   val inheritedFields: List<Field>
      get() {
         return allInheritedTypes
            .filterIsInstance<ObjectType>()
            .flatMap { it.fields }
      }

   val allFields: List<Field>
      get() {
         return inheritedFields + fields
      }

   override val typeDoc: String?
      get() {
         return listOfNotNull(this.definition?.typeDoc).plus(this.extensions.mapNotNull { it.typeDoc }).joinToString("\n")
      }
   val fields: List<Field>
      get() {
         return this.definition?.fields?.map { field ->
            val fieldExtensions = fieldExtensions(field.name)
            val collatedAnnotations = field.annotations + fieldExtensions.annotations()
            val (refinedType, defaultValue) = fieldExtensions
               .asSequence()
               .mapNotNull { refinement -> refinement.refinedType?.let {Pair(refinement.refinedType, refinement.defaultValue)} }
               .firstOrNull() ?: Pair(field.type, null)
            field.copy(annotations = collatedAnnotations, type = refinedType, defaultValue = defaultValue)
         } ?: emptyList()
      }

   override val annotations: List<Annotation>
      get() {
         val collatedAnnotations = this.extensions.flatMap { it.annotations }.toMutableList()
         definition?.annotations?.forEach { collatedAnnotations.add(it) }
         return collatedAnnotations.toList()
      }

   private fun fieldExtensions(fieldName: String): List<FieldExtension> {
      return this.extensions.flatMap { it.fieldExtensions(fieldName) }
   }

   fun hasField(name: String): Boolean = allFields.any { it.name == name }
   fun field(name: String): Field = allFields.first { it.name == name }
   fun annotation(name: String): Annotation = annotations.first { it.name == name }
   fun fieldsWithType(typeName: QualifiedName): List<Field> {
      return this.fields.filter { applicableQualifiedNames(it.type).contains(typeName) }
   }

   fun applicableQualifiedNames(type: Type): List<QualifiedName> {
      return type.inheritsFrom.map { it.toQualifiedName() }.plus(type.toQualifiedName())
   }

}


interface AnnotationProvider {
   fun toAnnotation(): Annotation
}

fun Iterable<AnnotationProvider>.toAnnotations(): List<Annotation> {
   return this.map { it.toAnnotation() }
}

interface NameTypePair {
   // Relaxed to Nullable to support Parameters, which don't mandate names
   val name: String?
   val type: Type

}

data class Annotation(val name: String, val parameters: Map<String, Any?> = emptyMap())
data class Field(
   override val name: String,
   override val type: Type,
   val nullable: Boolean = false,
   val modifiers: List<FieldModifier> = emptyList(),
   override val annotations: List<Annotation> = emptyList(),
   override val constraints: List<Constraint> = emptyList(),
   val accessor: Accessor? = null,
   val readCondition: FieldSetCondition? = null,
   override val typeDoc: String? = null,
   val defaultValue: Any? = null
) : Annotatable, ConstraintTarget, Documented, NameTypePair {

   override val description: String = "field $name"

   // For equality - don't compare on the type (as this can cause stackOverflow when the type is an Object type)
   private val typeName = type.qualifiedName
   private val equality = Equality(this, Field::name, Field::typeName, Field::nullable, Field::annotations, Field::constraints)

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()


}


interface Accessor
interface ExpressionAccessor : Accessor {
   val expression: String
}

data class XpathAccessor(override val expression: String) : ExpressionAccessor
data class JsonPathAccessor(override val expression: String) : ExpressionAccessor

// TODO : This is duplicating concepts in ColumnMapping, one should die.
data class ColumnAccessor(val index: Int) : ExpressionAccessor {
   override val expression: String = index.toString()
}

data class DestructuredAccessor(val fields: Map<String, Accessor>) : Accessor

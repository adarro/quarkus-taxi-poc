package lang.taxi.types

/**
 * A set of fields with additional conditional mapping logic
 * applied
 */
data class ConditionalFieldSet(val fields: List<Field>, val expression: FieldSetExpression)

interface FieldSetExpression : TaxiStatementGenerator

data class CalculatedFieldSetExpression(
   val operand1: FieldReferenceSelector,
   val operand2: FieldReferenceSelector,
   val operator: FormulaOperator
) : FieldSetExpression {
   override fun asTaxi(): String = "(${operand1.asTaxi()} ${operator.symbol} ${operand2.asTaxi()})"
}

data class WhenFieldSetCondition(
   val selectorExpression: WhenSelectorExpression,
   val cases: List<WhenCaseBlock>
) : FieldSetExpression {
   override fun asTaxi(): String {
      val whenAsTaxi = if (selectorExpression.asTaxi().isEmpty()) {
         "when"

      } else {
         "when (${selectorExpression.asTaxi()})"
      }
      return """$whenAsTaxi {
   ${cases.joinToString("\n") { it.asTaxi() }}
}
      """.trimMargin()
   }
}

interface WhenSelectorExpression : TaxiStatementGenerator {
   val declaredType: Type
}

data class AccessorExpressionSelector(
   val accessor: Accessor,
   override val declaredType: Type
) : WhenSelectorExpression {
   override fun asTaxi(): String {
      TODO("Not yet implemented")
   }
}

data class TypeReferenceSelector(val type: Type) : Accessor
// TODO : Can FieldReferenceSelector, ReferenceAssignment and ReferenceCaseMatchExpression all be merged?
data class FieldReferenceSelector(val fieldName: String, override val returnType: Type) : WhenSelectorExpression, Accessor {
   override val declaredType: Type = returnType

   companion object {
      fun fromField(field: Field): FieldReferenceSelector {
         return FieldReferenceSelector(field.name, field.type)
      }
   }

   override fun asTaxi(): String = "this.$fieldName"
}

class EmptyReferenceSelector: WhenSelectorExpression {
   override fun asTaxi(): String {
      return ""
   }
   // TODO : What type is this?
   override val declaredType: Type = PrimitiveType.ANY
}

data class WhenCaseBlock(
   val matchExpression: WhenCaseMatchExpression,
   val assignments: List<AssignmentExpression>
) : TaxiStatementGenerator {
   override fun asTaxi(): String {
      val assignmentStatement = if (assignments.size > 1) {
         """{
   ${assignments.joinToString("\n") { it.asTaxi() }}
         }""".trimMargin()
      } else assignments.first().asTaxi()
      return "${matchExpression.asTaxi()} -> $assignmentStatement"
   }

   fun getAssignmentFor(fieldName: String): FieldAssignmentExpression {
      return assignments
         .filterIsInstance<FieldAssignmentExpression>()
         .firstOrNull { it.fieldName == fieldName } ?: error("No assignment exists for field $fieldName")
   }

   /**
    * Expects that a single InlineAssignmentExpression is present,
    * and will error if not.
    */
   fun getSingleAssignment(): InlineAssignmentExpression {
      require(assignments.size == 1) { "Cannot call getSingleAssignment() when there are multiple assignments present.  You should call getAssignmentFor(..) instead" }
      require(assignments[0] is InlineAssignmentExpression) { "Expected single assignment to be an InlineAssignmentExpression, but found ${assignments[0]::class.simpleName}" }
      return assignments[0] as InlineAssignmentExpression
   }
}

sealed class AssignmentExpression : TaxiStatementGenerator {
   abstract val assignment: ValueAssignment
}

/**
 * An assignment for a specific field.
 * Used when mapping a when { } block for mulitple fields, to indicate which field is going to
 * be assigned
 */
data class FieldAssignmentExpression(val fieldName: String, override val assignment: ValueAssignment) : AssignmentExpression() {
   override fun asTaxi(): String = "$fieldName ${assignment.asTaxi()}"
}

/**
 * An assignment where the field is implied
 * Used when mapping a when block for a single field, and the field name is handled higher
 * in the AST.
 */
data class InlineAssignmentExpression(override val assignment: ValueAssignment) : AssignmentExpression() {
   override fun asTaxi(): String = assignment.asTaxi()
}


interface WhenCaseMatchExpression : TaxiStatementGenerator {
   val type: Type
}

// TODO : Can FieldReferenceSelector, ReferenceAssignment and ReferenceCaseMatchExpression all be merged?
class ReferenceCaseMatchExpression(val reference: String, override val type: Type) : WhenCaseMatchExpression {
   companion object {
      fun fromField(field: Field): ReferenceCaseMatchExpression {
         return ReferenceCaseMatchExpression(field.name, field.type)
      }
   }

   override fun asTaxi(): String = reference // I don't think this is right...
}

class EnumLiteralCaseMatchExpression(val enumValue: EnumValue, override val type: EnumType) : WhenCaseMatchExpression {
   override fun asTaxi(): String = enumValue.qualifiedName
}

class LiteralCaseMatchExpression(val value: Any) : WhenCaseMatchExpression {
   private val accessor = LiteralAccessor(value)
   override val type: Type = accessor.returnType

   override fun asTaxi(): String = accessor.asTaxi()
}

object ElseMatchExpression : WhenCaseMatchExpression {
   override fun asTaxi(): String = "else"
   override val type: Type = PrimitiveType.ANY
}

interface ValueAssignment : TaxiStatementGenerator {
   val type: Type
}

data class ScalarAccessorValueAssignment(val accessor: Accessor) : ValueAssignment {
   override fun asTaxi(): String = "/* ScalarAccessorValueAssignment does not yet generate taxi */"
   override val type: Type = accessor.returnType
}


data class DestructuredAssignment(val assignments: List<FieldAssignmentExpression>) : ValueAssignment {
   override fun asTaxi(): String {
      return assignments.joinToString("\n") { it.asTaxi() }
   }

   // TODO -- Not sure how to model this, since through destructing there's actually
   // multiple types here.
   override val type: Type = PrimitiveType.ANY
}

// TODO : Can FieldReferenceSelector, ReferenceAssignment and ReferenceCaseMatchExpression all be merged?
data class ReferenceAssignment(val reference: String, override val type: Type) : ValueAssignment {
   companion object {
      fun fromField(field: Field): ReferenceAssignment {
         return ReferenceAssignment(field.name, field.type)
      }
   }

   override fun asTaxi(): String = reference
}

data class LiteralAssignment(val value: Any) : ValueAssignment {
   private val accessor = LiteralAccessor(value)

   override fun asTaxi(): String = accessor.asTaxi()
   override val type: Type = accessor.returnType
}

data class EnumValueAssignment(val enum: EnumType, val enumValue: EnumValue) : ValueAssignment {
   override fun asTaxi(): String = "${enum.qualifiedName}.${enumValue.name}"
   override val type: Type = enum
}

object NullAssignment : ValueAssignment {
   override fun asTaxi(): String = "null"

   // TODO : Can we infer the type better?
   override val type: Type = PrimitiveType.ANY
}

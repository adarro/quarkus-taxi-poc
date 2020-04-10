package lang.taxi.types

interface Type : Named, Compiled {
   val inheritsFrom: Set<Type>

   val allInheritedTypes: Set<Type>
      get() {
         return getInheritanceGraph()
      }

   val inheritsFromPrimitive: Boolean
      get() = basePrimitive != null

   val basePrimitive: PrimitiveType?
      get() {
         val primitives = (this.allInheritedTypes + this).filter { it is PrimitiveType || it is EnumType }
         return when {
            primitives.isEmpty() -> null
            primitives.size == 1 -> {
               val baseType = primitives.first()
               if (baseType is PrimitiveType) {
                  baseType
               } else {
                  // We can revisit this later if neccessary.
                  require(baseType is EnumType) { "Expected baseType to be EnumType" }
                  PrimitiveType.STRING
               }
            }
            else -> error("Type ${this.qualifiedName} inherits from multiple primitives: ${primitives.joinToString { it.qualifiedName }}")
         }
      }

   private fun getInheritanceGraph(typesToExclude: Set<Type> = emptySet()): Set<Type> {
      val allExcludedTypes: Set<Type> = typesToExclude + setOf(this)
      val aliasType = if (this is TypeAlias) {
         this.aliasType!!
      } else {
         null
      }
      return (this.inheritsFrom + aliasType)
         .filterNotNull()
         .flatMap { type ->
            // Include aliases if this is a TypeAlias
            if (type is TypeAlias) {
               listOf(type, type.aliasType!!)
            } else {
               listOf(type)
            }
         }
         .flatMap { inheritedType ->
            if (!typesToExclude.contains(inheritedType))
               setOf(inheritedType) + inheritedType.getInheritanceGraph(allExcludedTypes)
            else emptySet<Type>()
         }.toSet()
   }


}

interface TypeDefinition {
   val compilationUnit: CompilationUnit
}

interface Documented {
   val typeDoc: String?

   companion object {
      fun typeDoc(sources: List<Documented?>): String? {
         return sources.filterNotNull().typeDoc()
      }
   }

}


/**
 * A type that can be declared by users explicity.
 * eg:  Object type, Enum type.
 * ArrayType is excluded (as arrays are primitive, and the inner
 * type will be a UserType)
 */
interface UserType<TDef : TypeDefinition, TExt : TypeDefinition> : Type {
   var definition: TDef?

   val extensions: List<TExt>

   fun addExtension(extension: TExt): ErrorMessage?

   val isDefined: Boolean
      get() {
         return this.definition != null
      }

   override val compilationUnits: List<CompilationUnit>
      get() = (this.extensions.map { it.compilationUnit } + this.definition?.compilationUnit).filterNotNull()

   /**
    * A list of all the other types this UserType makes reference to.
    * Used when importing this type, to ensure the full catalogue of types is imported
    */
   val referencedTypes: List<Type>

}


fun List<Documented>.typeDoc(): String? {
   return this.mapNotNull { it.typeDoc }.joinToString("\n")
}

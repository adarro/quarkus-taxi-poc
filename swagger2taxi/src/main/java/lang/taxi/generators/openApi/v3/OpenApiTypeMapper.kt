package lang.taxi.generators.openApi.v3

import io.swagger.oas.models.OpenAPI
import io.swagger.oas.models.media.ArraySchema
import io.swagger.oas.models.media.BooleanSchema
import io.swagger.oas.models.media.ComposedSchema
import io.swagger.oas.models.media.DateSchema
import io.swagger.oas.models.media.DateTimeSchema
import io.swagger.oas.models.media.EmailSchema
import io.swagger.oas.models.media.IntegerSchema
import io.swagger.oas.models.media.NumberSchema
import io.swagger.oas.models.media.ObjectSchema
import io.swagger.oas.models.media.Schema
import io.swagger.oas.models.media.StringSchema
import io.swagger.oas.models.media.UUIDSchema
import lang.taxi.generators.Logger
import lang.taxi.generators.openApi.Utils
import lang.taxi.generators.openApi.Utils.replaceIllegalCharacters
import lang.taxi.types.ArrayType
import lang.taxi.types.CompilationUnit
import lang.taxi.types.Field
import lang.taxi.types.ObjectType
import lang.taxi.types.ObjectTypeDefinition
import lang.taxi.types.PrimitiveType
import lang.taxi.types.Type

class OpenApiTypeMapper(val api: OpenAPI, val defaultNamespace: String, private val logger: Logger) {

   private val _generatedTypes = mutableMapOf<String, Type>()

   val generatedTypes: Set<Type> get() = _generatedTypes.values.toSet()

   fun generateTypes() {
      api.components?.schemas?.forEach { (name, schema) ->
         generateAndStoreType(name, schema)
      }
   }

   private fun generateAndStoreType(name: String, schema: Schema<*>): Type {
      val generatedType = generateType(name, schema)
      /*
       * generatedTypes is the set of types we want to write out in the
       * generated taxi code. We don't want to write out
       * `type lang.taxi.Array` - it gets skipped by `SchemaWriter` anyway,
       * resulting in an empty `namespace lang.taxi` being written out.
       *
       * In addition, generatedTypes is used as a cache by definition name,
       * which is the same for all arrays, meaning that the first array
       * generated becomes the sole array used in future, despite them
       * having different generic types.
       */
      if (generatedType !is ArrayType) {
         _generatedTypes[name] = generatedType
      }
      return generatedType
   }

   private fun generateType(name: String, schema: Schema<*>): Type {
      return when (schema) {
         is ArraySchema -> generateArrayType(name, schema)
         else -> generateObjectType(name, schema)
      }
   }

   private fun generateArrayType(name: String, schema: ArraySchema): Type {
      val arrayInnerType = getOrGenerateType(schema.items, defaultToAny = true)
      return ArrayType(arrayInnerType, CompilationUnit.unspecified());
   }

   private fun generateObjectType(name: String, schema: Schema<*>): Type {
      val requiredFields = schema.required ?: emptyList()
      val qualifiedName = Utils.qualifyTypeNameIfRaw(name, defaultNamespace)
      val properties = schema.properties ?: emptyMap()
      val fields = properties.map { (name, schema) ->
         generateField(name, schema, requiredFields.contains(name))
      }
      val typeDef = ObjectTypeDefinition(
         fields = fields.toSet(),
         compilationUnit = CompilationUnit.unspecified(),
         typeDoc = schema.description,
      )
      return ObjectType(qualifiedName, typeDef)
   }

   private fun generateField(name: String, schema: Schema<*>, required: Boolean): Field {
      return Field(
         name = name.replaceIllegalCharacters(),
         type = getOrGenerateType(schema, defaultToAny = true),
         nullable = required,
         compilationUnit = CompilationUnit.unspecified(),
         typeDoc = schema.description,
      )
   }

   private fun getOrGenerateType(schema: Schema<*>, anonymousTypeNamePartial: String? = null, defaultToAny: Boolean = false): Type {
      val type = getPrimitiveType(schema)
         ?: _generatedTypes[schema.type]
         ?: getTypeFromRef(schema.`$ref`)
         ?: getTypeFromAllOfDeclaration(schema, anonymousTypeNamePartial)

      if (type != null) {
         return type
      } else if (canDetectTypeName(schema, anonymousTypeNamePartial)) {
         val name = typeNameFromSchema(schema, anonymousTypeNamePartial)
         return generateAndStoreType(name, schema)
      } else if (defaultToAny) {
         return PrimitiveType.ANY
      } else {
         error("Cannot detect type, a type name to generate, and not permitted to default to Any.  Giving up.")
      }


   }


   private fun getTypeFromAllOfDeclaration(schema: Schema<*>, anonymousTypeNamePartial: String?): Type? {
      if (schema !is ComposedSchema) {
         return null
      } else if (schema.allOf.isNullOrEmpty()) {
         return null
      }
      // AllOf allows schema API's to declare in-line classes which are composed from multiple
      // entries.
      // Need to consider how we handle this in Taxi.
      // Also, we should consider comppsition in general as a topic
      // https://swagger.io/docs/specification/data-models/oneof-anyof-allof-not/

      // Incremental support - handle single allOf()'s only initially
      if (schema.allOf.size > 1) {
         TODO("Handling allOf() with multiple entries is not yet supported.")
      }
      return getOrGenerateType(schema.allOf.first())
   }

   private fun canDetectTypeName(schema: Schema<*>, anonymousTypeNamePartial: String?): Boolean {
      return when {
         schema.type != null -> true
         anonymousTypeNamePartial != null -> true
         else -> false
      }
   }

   private fun typeNameFromSchema(schema: Schema<*>, anonymousTypeNamePartial: String?, defaultToAny: Boolean = false): String {
      if (schema.type != null) return schema.type
      if (anonymousTypeNamePartial != null) return "AnonymousType$anonymousTypeNamePartial"
      error("Type name is not defined, and no naming for anonymous type was provided")
   }

   private fun getTypeFromRef(typeRef: String?): Type? {
      if (typeRef == null) return null;
      val typeName = typeRef.split("/").last()
      val type = this._generatedTypes[typeName]
      if (type == null) {
         val schema = RefEvaluator.navigate(this.api, typeRef)
         return getOrGenerateType(schema)
      }
      return type
   }

   private fun getPrimitiveType(schema: Schema<*>): PrimitiveType? {
      return when (schema) {
         is BooleanSchema -> PrimitiveType.BOOLEAN
         is DateSchema -> PrimitiveType.LOCAL_DATE
         is DateTimeSchema -> PrimitiveType.DATE_TIME
         is EmailSchema -> PrimitiveType.STRING
         is IntegerSchema -> PrimitiveType.INTEGER
         is NumberSchema -> PrimitiveType.DECIMAL
         is StringSchema -> PrimitiveType.STRING
         is UUIDSchema -> PrimitiveType.STRING
         is ObjectSchema -> PrimitiveType.ANY
         else -> null
      }
   }

   fun findType(schema: Schema<*>, anonymousTypeNamePartial: String? = null): Type {
      return getOrGenerateType(schema, anonymousTypeNamePartial)
   }

}

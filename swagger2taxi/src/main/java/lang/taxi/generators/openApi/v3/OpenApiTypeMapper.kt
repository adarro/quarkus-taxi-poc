package lang.taxi.generators.openApi.v3

import io.swagger.oas.models.OpenAPI
import io.swagger.oas.models.media.*
import lang.taxi.generators.openApi.Utils
import lang.taxi.generators.openApi.Utils.replaceIllegalCharacters
import lang.taxi.types.*

class OpenApiTypeMapper(private val api: OpenAPI, val defaultNamespace: String) {

   private val _generatedTypes = mutableMapOf<QualifiedName, Type>()

   val generatedTypes: Set<Type> get() = _generatedTypes.values.toSet()

   fun generateTypes() {
      api.components?.schemas?.forEach { (name, schema) ->
         generateNamedTypeRecursively(schema, qualify(name))
      }
   }

   private fun generateNamedTypeRecursively(
      schema: Schema<*>,
      name: QualifiedName
   ): Type {
      val taxiExtension = schema.taxiExtension
      return if (taxiExtension == null) {
         if (schema.hasProperties()) {
            generateModel(name, schema)
         } else {
            val supertype = toType(schema, name.typeName)
            generateType(name, supertype)
         }
      } else {
         val taxiExtName = qualify(taxiExtension.name)
         if (schema.hasProperties() && taxiExtension.create != false) {
            generateModel(taxiExtName, schema)
         } else if (!schema.hasProperties() && taxiExtension.create == true) {
            val supertype = toType(schema, name.typeName)
            generateType(taxiExtName, supertype)
         } else {
            makeType(taxiExtName)
         }
      }
   }

   fun generateUnnamedTypeRecursively(
      schema: Schema<*>,
      context: String
   ): Type =
      getTypeFromExtensions(schema) ?:
      toType(schema, context) ?:
      schema.`$ref`?.getTypeFromRef() ?:
      generateFromComposedSchema(schema, context) ?:
      generateModelIfAppropriate(schema, context) ?:
      PrimitiveType.ANY

   private fun getTypeFromExtensions(schema: Schema<*>): Type? {
      val explicitTaxiType = schema.taxiExtension
      return if (explicitTaxiType != null) {
         generateNamedTypeRecursively(schema, qualify(explicitTaxiType.name))
      } else {
         null
      }
   }

   private fun generateFromComposedSchema(schema: Schema<*>, context: String): Type? =
      if (schema is ComposedSchema && schema.allOf?.size == 1) {
         generateUnnamedTypeRecursively(schema.allOf.single(), context)
      } else null

   private fun generateModelIfAppropriate(schema: Schema<*>, context: String): Type? =
      if (schema.hasProperties()) {
         val name = anonymousModelName(context)
         generateModel(name, schema)
      } else null

   private fun Schema<*>.hasProperties() = !properties.isNullOrEmpty()

   private fun toType(schema: Schema<*>, context: String) =
      primitiveTypeFor(schema) ?:
      intermediateTypeFor(schema) ?:
      if (schema is ArraySchema) {
         makeArrayType(schema, context + "Element")
      } else null

   private fun primitiveTypeFor(schema: Schema<*>) = when (schema) {
      is BooleanSchema -> PrimitiveType.BOOLEAN
      is DateSchema -> PrimitiveType.LOCAL_DATE
      is DateTimeSchema -> PrimitiveType.DATE_TIME
      is IntegerSchema -> PrimitiveType.INTEGER
      is NumberSchema -> PrimitiveType.DECIMAL
      is StringSchema -> PrimitiveType.STRING
      else -> null
   }

   private fun intermediateTypeFor(schema: Schema<*>): Type? = when (schema) {
      is BinarySchema,
      is FileSchema,
      is ByteArraySchema,
      is PasswordSchema,
      is UUIDSchema,
      is EmailSchema -> {
         val formatTypeQualifiedName = qualify(schema.format)
         generateType(formatTypeQualifiedName, PrimitiveType.STRING)
      }
      else -> null
   }

   private fun generateType(
      taxiExtName: QualifiedName,
      supertype: Type?
   ) = _generatedTypes.getOrPut(taxiExtName) {
      makeType(taxiExtName, supertype)
   }

   private fun makeType(
      formatTypeQualifiedName: QualifiedName,
      supertype: Type? = null
   ) = ObjectType(
      formatTypeQualifiedName.toString(),
      ObjectTypeDefinition(
         inheritsFrom = setOfNotNull(supertype),
         compilationUnit = CompilationUnit.unspecified()
      )
   )

   private fun makeArrayType(schema: ArraySchema, context: String): ArrayType {
      val innerType = generateUnnamedTypeRecursively(schema.items, context)
      return ArrayType.of(innerType)
   }

   private fun generateModel(
      taxiExtName: QualifiedName,
      schema: Schema<*>
   ) = _generatedTypes.getOrPut(taxiExtName) {
      makeModel(taxiExtName, schema)
   }


   private fun makeModel(
      name: QualifiedName,
      schema: Schema<*>
   ): ObjectType {
      // This allows us to support recursion - the undefined type will prevent us getting into an endless loop
      _generatedTypes[name] = ObjectType.undefined(name.fullyQualifiedName)
      val requiredFields = schema.required ?: emptyList()
      val properties = schema.properties ?: emptyMap()
      val fields = properties.map { (propName, schema) ->
         generateField(
            name = propName,
            schema = schema,
            required = requiredFields.contains(propName),
            parent = name,
         )
      }
      val typeDef = ObjectTypeDefinition(
         fields = fields.toSet(),
         compilationUnit = CompilationUnit.unspecified(),
         typeDoc = schema.description,
      )
      return ObjectType(name.fullyQualifiedName, typeDef)
   }

   private fun generateField(name: String, schema: Schema<*>, required: Boolean, parent: QualifiedName): Field {
      val legalName = name.replaceIllegalCharacters()
      return Field(
         name = legalName,
         type = generateUnnamedTypeRecursively(schema, context = parent.typeName+legalName.capitalize()),
         nullable = !required,
         compilationUnit = CompilationUnit.unspecified(),
         typeDoc = schema.description,
      )
   }

   private fun String.getTypeFromRef(): Type {
      val (name, schema) = RefEvaluator.navigate(this@OpenApiTypeMapper.api, this)
      return generateNamedTypeRecursively(schema, qualify(name))
   }

   private fun anonymousModelName(context: String): QualifiedName {
      val typeName =
         if (context.startsWith("AnonymousType")) context
         else "AnonymousType${context.capitalize()}"
      return qualify(typeName)
   }

   private fun qualify(name: String) =
      Utils.qualifyTypeNameIfRaw(name, defaultNamespace)
}

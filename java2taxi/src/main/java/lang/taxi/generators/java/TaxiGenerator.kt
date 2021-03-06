package lang.taxi.generators.java

import com.google.common.annotations.VisibleForTesting
import lang.taxi.TaxiDocument
import lang.taxi.annotations.DataType
import lang.taxi.annotations.Service
import lang.taxi.generators.SchemaWriter
import lang.taxi.types.Type

class TaxiGenerator(val typeMapper: TypeMapper = DefaultTypeMapper(),
                    val serviceMapper: ServiceMapper = DefaultServiceMapper(),
                    val schemaWriter: SchemaWriter = SchemaWriter()) {
    private val classes = mutableSetOf<Class<*>>()
    private val generatedTypes = mutableSetOf<Type>()
    private val services = mutableSetOf<lang.taxi.services.Service>()
    fun forClasses(vararg classes: Class<*>): TaxiGenerator {
        this.classes.addAll(classes)
        return this
    }

    fun forClasses(classes: List<Class<*>>): TaxiGenerator {
        this.classes.addAll(classes)
        return this
    }

    @VisibleForTesting
    internal fun generateModel(): TaxiDocument {
        generateTaxiTypes()
        generateTaxiServices()
        return TaxiDocument(generatedTypes.toSet(), services.toSet(), emptySet())
    }

    private fun generateTaxiServices() {
        services.addAll(classesWithAnnotation(Service::class.java)
                .flatMap { javaClass ->
                    serviceMapper.getTaxiServices(javaClass, this.typeMapper, this.generatedTypes)
                })
    }

    private fun generateTaxiTypes() {
        classesWithAnnotation(DataType::class.java)
                .forEach { javaClass ->
                    typeMapper.getTaxiType(javaClass, generatedTypes)
                }
    }

    private fun classesWithAnnotation(annotation: Class<out Annotation>) = this.classes
            .filter { it.isAnnotationPresent(annotation) }


    fun generateAsStrings(): List<String> {
        val taxiDocs = generateModel()
        return schemaWriter.generateSchemas(listOf(taxiDocs))
    }


}

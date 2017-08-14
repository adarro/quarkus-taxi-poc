package lang.taxi.annotations

@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.PROPERTY, AnnotationTarget.FIELD,
        // When on a Function, indicates the return type.
        // Useful for methods that return String etc.
        AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DataType(
        /**
         * The qualified name of the attribute within a taxonomy.
         * If blank, will be inferred from the class name
         */
        val value: String = ""
)

fun DataType.hasNamespace(): Boolean = Namespaces.hasNamespace(this.value)
fun DataType.namespace(): String? = Namespaces.pluckNamespace(this.value)

fun DataType.declaresName(): Boolean {
    return this.value.isNotEmpty()
}

fun DataType.qualifiedName(defaultNamespace: String): String = Namespaces.qualifiedName(this.value, defaultNamespace)

/**
 * Specifies that an input must be provided in a specific format.
 * Eg: On a currency amount field, may specify the currency
 * On a date field, may specify the format
 * On a weight or height field, may specify the unit of measurement.
 *
 * Should be a pointer to a fully qualified Enum value within the
 * global taxonomy
 *
 * // TODO : Consider how DataFormats relate to contracts discussed here:
 * https://gitlab.com/osmosis-platform/taxi-lang/issues/1
 *
 * Contracts may be a more powerful expression, as it's more contextual
 */
annotation class DataFormat(val value: String)


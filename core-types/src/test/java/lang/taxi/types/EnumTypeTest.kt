package lang.taxi.types

import com.winterbe.expekt.should
import lang.taxi.sources.SourceCode
import org.junit.Test

class EnumTypeTest {

   @Test
   fun lookupByNameOrValueWithName() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.has("USD").should.be.`true`
      enumType.of("USD").name.should.equal("USD")
      enumType.of("USD").value.should.equal("US Dollars")
   }

   @Test
   fun lookupByNameOrValueWithNameNoValue() {
      // Enum has no value, with shuld default to name
      val enumType = enumType(listOf(
         EnumValue(name = "USD", qualifiedName = "Currency.USD", annotations = emptyList(), synonyms = emptyList()),
         EnumValue(name = "EUR", qualifiedName = "Currency.EUR", annotations = emptyList(), synonyms = emptyList())
      ))

      enumType.has("USD").should.be.`true`
      enumType.of("USD").name.should.equal("USD")
      enumType.of("USD").value.should.equal("USD")
   }

   @Test
   fun lookupByNameOrValueWithValue() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.has("US Dollars").should.be.`true`
      enumType.of("US Dollars").name.should.equal("USD")
      enumType.of("US Dollars").value.should.equal("US Dollars")
   }

   @Test(expected = NoSuchElementException::class)
   fun lookupByNameOrValueDoesntExist() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.has("GBP").should.be.`false`
      enumType.of("GBP")
   }

   @Test
   fun lookupByName() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.hasName("USD").should.be.`true`
      enumType.ofName("USD").name.should.equal("USD")
      enumType.ofName("USD").value.should.equal("US Dollars")
   }

   @Test(expected = NoSuchElementException::class)
   fun lookupByNameWithValue() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.hasName("US Dollars").should.be.`false`
      enumType.ofName("US Dollars")
   }

   @Test
   fun lookupByValue() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.hasValue("Euro").should.be.`true`
      enumType.ofValue("Euro").name.should.equal("EUR")
      enumType.ofValue("Euro").value.should.equal("Euro")
   }

   @Test(expected = NoSuchElementException::class)
   fun lookupByValueWithName() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.hasValue("EUR").should.be.`false`
      enumType.ofValue("EUR")
   }

   fun lookupWithNulls() {
      val enumType = enumType(listOf(
         EnumValue("USD", "US Dollars", "Currency.USD", emptyList(), emptyList()),
         EnumValue("EUR", "Euro", "Currency.EUR", emptyList(), emptyList())
      ))

      enumType.has(null).should.be.`false`
      enumType.hasValue(null).should.be.`false`
      enumType.hasName(null).should.be.`false`
   }

   private fun enumType(values: List<EnumValue>): EnumType {
      val definition = EnumDefinition(values = values, compilationUnit = CompilationUnit(null, SourceCode("", "")), basePrimitive = PrimitiveType.STRING)
      return EnumType("Currency", definition)
   }


}

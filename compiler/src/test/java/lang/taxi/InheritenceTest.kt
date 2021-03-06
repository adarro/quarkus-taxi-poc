package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.types.PrimitiveType
import org.junit.Test

// NOte : I'm trying to split tests out from GrammarTest
// to more specific areas.
// There's also a bunch of tests in GrammarTest that cover type Inheritence
class InheritenceTest {

   @Test
   fun canInheritFromCollection() {
      val src = """
type Person {
   firstName : FirstName as String
}
type ListOfPerson inherits Person[]
      """.trimIndent()
      val doc = Compiler(src).compile()
      val type = doc.type("ListOfPerson")
      type.inheritsFrom.map { it.toQualifiedName().parameterizedName }.should.contain("lang.taxi.Array<Person>")
   }

   @Test
   fun canInheritFromAlias() {
      val src = """
   type alias CcySymbol as String
   type BaseCurrency inherits CcySymbol
""".trimIndent()
      val doc = Compiler(src).compile()
      val type = doc.type("BaseCurrency")
      type.inheritsFrom.should.have.size(1)
   }

   @Test
   fun detectsUnderlyingPrimitivesCorrectly() {
      val src = """
   type alias CcySymbol as String
   type BaseCurrency inherits CcySymbol

   type Person {
      firstName : String
   }
""".trimIndent()
      val doc = Compiler(src).compile()
      doc.type("CcySymbol").basePrimitive!!.should.equal(PrimitiveType.STRING)
      doc.type("BaseCurrency").basePrimitive!!.should.equal(PrimitiveType.STRING)
      doc.type("Person").basePrimitive.should.be.`null`
      PrimitiveType.STRING.basePrimitive.should.equal(PrimitiveType.STRING)
   }

   @Test
   fun detectsUnderlyingEnum() {
      val src = """
         enum Country {
            NZ,
            AUS
         }
         type BetterCountry inherits Country
         type NotAnEnum {}
      """.trimIndent()
      val doc = Compiler(src).compile()
      doc.type("BetterCountry").baseEnum!!.qualifiedName.should.equal("Country")
      doc.type("NotAnEnum").baseEnum.should.be.`null`
   }

    @Test
    fun cacheAliasTypeProperties() {
        val src = """
         type alias CcySymbol as String""".trimIndent()
       val doc = Compiler(src).compile()
       doc.type("CcySymbol").basePrimitive.should.be.equal(PrimitiveType.STRING)
       doc.type("CcySymbol").basePrimitive.should.be.equal(PrimitiveType.STRING)
       doc.type("CcySymbol").allInheritedTypes.should.be.equal(setOf(PrimitiveType.STRING))
       doc.type("CcySymbol").allInheritedTypes.should.be.equal(setOf(PrimitiveType.STRING))
       doc.type("CcySymbol").inheritsFromPrimitive.should.be.`true`
       doc.type("CcySymbol").inheritsFromPrimitive.should.be.`true`
       doc.type("CcySymbol").baseEnum.should.be.`null`
       doc.type("CcySymbol").baseEnum.should.be.`null`
    }

   @Test
   fun cachePrimitiveTypeProperties() {
      PrimitiveType.INTEGER.basePrimitive.should.be.equal(PrimitiveType.INTEGER)
      PrimitiveType.INTEGER.basePrimitive.should.be.equal(PrimitiveType.INTEGER)
      PrimitiveType.INTEGER.allInheritedTypes.should.be.equal(emptySet())
      PrimitiveType.INTEGER.allInheritedTypes.should.be.equal(emptySet())
      PrimitiveType.INTEGER.inheritsFromPrimitive.should.be.`true`
      PrimitiveType.INTEGER.inheritsFromPrimitive.should.be.`true`
      PrimitiveType.INTEGER.baseEnum.should.be.`null`
      PrimitiveType.INTEGER.baseEnum.should.be.`null`
   }
}

package lang.taxi.generators.java

import com.winterbe.expekt.expect
import com.winterbe.expekt.should
import lang.taxi.TypeAliasRegistry
import lang.taxi.annotations.*
import lang.taxi.demo.FirstName
import lang.taxi.testing.TestHelpers
import org.junit.Ignore
import org.junit.Test
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

class ServiceTests {
   @DataType("taxi.example.Money")
   data class Money(
      @field:DataType("taxi.example.Currency") val currency: String,
      @field:DataType("taxi.example.MoneyAmount") val value: BigDecimal)

   @DataType
   @Namespace("taxi.example")
   data class Person(@field:DataType("taxi.example.PersonId") val personId: String)


   @RestController
   @Service("taxi.example.PersonService")
   class MyService {
      @Operation
      fun findPerson(@DataType("taxi.example.PersonId") personId: String): Person {
         TODO("not real")
      }

      @Operation
      @ResponseContract(basedOn = "source",
         constraints = [ResponseConstraint("currency = targetCurrency")]
      )
      fun convertRates(@Parameter(constraints = [Constraint("currency = 'GBP'")]) source: Money, @Parameter(name = "targetCurrency") targetCurrency: String): Money {
         TODO("Not a real service")
      }

   }

   // TODO : This test sometimes fails, which is annoying.
   @Test
   fun generatesServiceTemplate() {
      val taxiDef = TaxiGenerator().forClasses(MyService::class.java, Person::class.java).generateAsStrings()
      expect(taxiDef).to.have.size(1)
      val expected = """
namespace taxi.example

type Person {
    personId : PersonId as String
}
type Money {
    currency : Currency as String
    value : MoneyAmount as Decimal
}
service PersonService {
    operation findPerson(PersonId) : Person
    operation convertRates( Money( this.currency = "GBP" ),
        targetCurrency : String ) : Money( from source, this.currency = targetCurrency )
}

"""
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun given_serviceReturnsPrimitiveWithAnnotation_then_typeAliasIsGenerated() {
      @Service("TestService")
      @Namespace("taxi.example")
      class TestService {
         @DataType("taxi.example.EmailAddress")
         @Operation
         fun findEmail(@DataType("taxi.example.PersonId") input: String): String {
            TODO("Not a real service")
         }
      }

      val taxiDef = TaxiGenerator().forClasses(TestService::class.java).generateAsStrings()
      expect(taxiDef).to.have.size(1)


      val expected = """
namespace taxi.example
type alias EmailAddress as String
type alias PersonId as String
service TestService {
    operation findEmail(PersonId):EmailAddress
}"""
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }


   @Test
   @Ignore("Needs investigation - looks like type aliases not being registered correctly - is the plugin running in the build?")
   fun givenOperationReturnsTypeAliasedList_then_schemaIsGeneratedCorrectly() {
      @Service("TestService")
      @Namespace("foo")
      class TestService {
         @Operation
         fun listPeopleNames(): List<PersonName> {
            TODO()
         }
      }
      TypeAliasRegistry.register(TypeAliases::class)
      val taxiDef = TaxiGenerator().forClasses(TestService::class.java).generateAsStrings()
      val expected = """
namespace foo {

   type alias PersonName as String

   service TestService {
      operation listPeopleNames(  ) : PersonName[]
   }
}
        """.trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun givenOperationReturnsList_then_schemaIsGeneratedCorrectly() {
      @Service("TestService")
      @Namespace("taxi.example")
      class TestService {
         @Operation
         fun listPeople(): List<ServiceTests.Person> {
            TODO()
         }
      }
      TypeAliasRegistry.register(TypeAliases::class)
      val taxiDef = TaxiGenerator().forClasses(TestService::class.java).generateAsStrings()
      val expected = """
    namespace taxi.example {

   type Person {
      personId : PersonId
   }

   type alias PersonId as String

   service TestService {
      operation listPeople(  ) : Person[]
   }
}

""".trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }

   @Test
   fun given_typeUsesTypeFromAnotherLibrary_then_itIsImported() {
      @Service("TestService")
      @Namespace("taxi.example")
      class TestService {
         @Operation
         fun findEmail(input: PersonName): FirstName {
            TODO("Not a real service")
         }
      }
      TypeAliasRegistry.register(lang.taxi.demo.TypeAliases::class)
      TypeAliasRegistry.register(TypeAliases::class)

      // Note - we're not using compilesSameAs(..) for tests involving imports, as they likely don't compile without the imported definition
      val taxiDef = TaxiGenerator().forClasses(TestService::class.java).generateAsStrings()
      taxiDef.joinToString("\n").should.contain("import lang.taxi.demo.FirstName")
   }

   @Test
   fun generatesValidTaxiFromJavaService() {
      TypeAliasRegistry.register(lang.taxi.demo.TypeAliases::class)
      TypeAliasRegistry.register(TypeAliases::class)

      // Note - we're not using compilesSameAs(..) for tests involving imports, as they likely don't compile without the imported definition
      val taxiDef = TaxiGenerator().forClasses(JavaServiceTest::class.java).generateAsStrings()
      // Imports should be collated to the top
      taxiDef[0].should.equal("import lang.taxi.FirstName")
      taxiDef[1].removeSpaces().should.equal("""namespace foo {

   type Person {
      name : PersonName
   }

   type alias PersonName as String


}""".removeSpaces())
      taxiDef[2].removeSpaces().should.equal("""namespace lang.taxi.generators.java {



   service JavaService {
      operation findByEmail(  FirstName ) : foo.Person
   }
}""".removeSpaces())
   }

   @Test
   @Ignore("Needs investigation - looks like type aliases not being registered correctly - is the plugin running in the build?")
   fun given_serviceAcceptsTypeAliasedPrimitive_then_signatureIsGeneratedCorrectly() {
      @Service("TestService")
      @Namespace("taxi.example")
      class TestService {
         @Operation
         fun findEmail(input: PersonName): PersonName {
            TODO("Not a real service")
         }
      }
      TypeAliasRegistry.register(TypeAliases::class)

      val taxiDef = TaxiGenerator().forClasses(TestService::class.java).generateAsStrings()
      val expected = """
namespace foo {
    type alias PersonName as String
}
namespace taxi.example {
    service TestService {
        operation findEmail(  foo.PersonName ) : foo.PersonName
    }
}
        """.trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }


   @Test
   fun given_operationDeclaresScope_then_itIsExported() {
      @Service("TestService")
      @Namespace("taxi.example")
      class TestService {
         @Operation(scope = "read")
         fun findEmail(input: String): String {
            TODO("Not a real service")
         }
      }

      val taxiDef = TaxiGenerator().forClasses(TestService::class.java).generateAsStrings()
      val expected = """
namespace taxi.example {
    service TestService {
        read operation findEmail( String ) : String
    }
}
        """.trimIndent()
      TestHelpers.expectToCompileTheSame(taxiDef, expected)
   }
}

@DataType("taxi.PersonList")
typealias PersonList = List<ServiceTests.Person>

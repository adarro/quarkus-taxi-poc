package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.services.FilterCapability
import lang.taxi.services.SimpleQueryCapability
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object OperationSpec : Spek({
   describe("Grammar for operations") {
      val taxi = """
         type Trade {
            tradeId : TradeId as String
            tradeDate : TradeDate as Instant
         }
         type alias EmployeeCode as String
      """.trimIndent()

      it("should compile operations with array return types") {
         """
            $taxi

            service Foo {
               operation listAllTrades():Trade[]
            }
         """.trimIndent()
            .compiled().service("Foo")
            .operation("listAllTrades")
            .returnType.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Trade>")
      }

      it("should parse constraints on inputs") {
         val param = """
            type Money {
               currency : CurrencySymbol as String
            }
            service ClientService {
              operation convertMoney(Money(this.currency = 'GBP'),target : CurrencySymbol):Money( this.currency = target )
            }
         """.trimIndent()
            .compiled().service("ClientService")
            .operation("convertMoney")
            .parameters[0]
         param.constraints.should.have.size(1)
      }

      // See OperationContextSpec ... need to pick a syntax for this
      xit("should parse constraints that reference parameters using type") {
         val param = """
         namespace demo {
            type RewardsAccountBalance {
               balance : RewardsBalance as Decimal
               currencyUnit : CurrencyUnit as String
            }
         }
         namespace test {
            service RewardsBalanceService {
               operation convert(  demo.CurrencyUnit, @RequestBody demo.RewardsAccountBalance ) : demo.RewardsAccountBalance( from source, this.currencyUnit = demo.CurrencyUnit )
            }
         }
         """.trimIndent()
            .compiled().service("ClientService")
            .operation("convertMoney")
            .parameters[0]
         param.constraints.should.have.size(1)
      }

      it("should parse constraints that are not part of return type") {
         val param = """
         namespace demo {
            type CreatedAt inherits Date
            type RewardsAccountBalance {
               balance : RewardsBalance as Decimal
               currencyUnit : CurrencyUnit as String
            }
         }
         namespace test {
            service RewardsBalanceService {
               operation findByCaskInsertedAtBetween( @PathVariable(name = "start") start : demo.CreatedAt, @PathVariable(name = "end") end : demo.CreatedAt ) : demo.RewardsAccountBalance[]( demo.CreatedAt >= start, demo.CreatedAt < end )
            }
         }
         """.trimIndent()
            .compiled().service("test.RewardsBalanceService")
            .operation("findByCaskInsertedAtBetween")
            .parameters[0]
     //    param.constraints.should.have.size(1)
      }
   }
   describe("Grammar for query operations") {
      it("should compile query grammar") {
         val queryOperation = """
            model Person {}
            service PersonService {
               query personQuery(vyneQl):Person[] with capabilities {
                  filter(=,in,like),
                  sum,
                  count
               }
            }
         """.compiled()
            .service("PersonService")
            .queryOperation("personQuery")

         queryOperation.grammar.should.equal("vyneQl")
         queryOperation.returnType.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Person>")
         val capabilities = queryOperation.capabilities
         capabilities.should.have.size(3)
         capabilities.should.contain.elements(SimpleQueryCapability.SUM, SimpleQueryCapability.COUNT)
         val filter = capabilities.filterIsInstance<FilterCapability>().first()
         filter.supportedOperations.should.have.size(3)
         filter.supportedOperations.should.contain.elements(FilterCapability.FilterOperation.IN,FilterCapability.FilterOperation.EQUAL,FilterCapability.FilterOperation.LIKE)
      }
   }

})

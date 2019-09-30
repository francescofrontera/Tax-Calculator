package ffrontera

import java.util.UUID

import ffrontera.errors.CommonError.NoSuchProductException
import ffrontera.models.Item
import ffrontera.services.Dsl
import ffrontera.services.ops.TaxOps
import scalaz.Id.Id
import scalaz.{Free, ~>}

package object interpeter {
  import Dsl._

  object SalesTaxInterpreter {
    private def calculateTax(items: Seq[Item],
                             taxRange: BigDecimal,
                             importedTaxRange: BigDecimal,
                             roundTax: BigDecimal): TaxResult =
      items
        .foldLeft(TaxResult(List.empty[Item], TaxOps.zero, TaxOps.zero)) {
          case (acc, item) =>
            val TaxResult(items, tot, totTax) = acc
            val Item(category, _, price, isImported, qt, _) = item

            val totalClass = TaxOps.composeCalculationAndScale(
              price,
              taxRange,
              importedTaxRange,
              roundTax,
              category,
              isImported
            )

            val newPrice = price + totalClass
            val updatedItems = item.copy(price = newPrice) :: items
            val updatedTot = tot + (newPrice * qt)
            val updatedTax = totTax + (totalClass * qt)

            acc.copy(updatedItems, updatedTot, updatedTax)
        }

    val ImpureInterpreter =
      new (SalesTaxDSL ~> Id) {
        val state = collection.mutable.Map.empty[UUID, Item]

        override def apply[A](fa: SalesTaxDSL[A]): Id[A] = fa match {
          case AddProduct(item) ⇒
            val id = item.id
            state(id) = item
            id
          case GetItem(uuid) ⇒
            state.get(uuid)
          case RemoveProduct(uuid) ⇒
            state get uuid match {
              case Some(uuidRe) ⇒
                state -= uuidRe.id;
                Right(uuid)
              case None ⇒
                Left(NoSuchProductException("SS"))
            }
          case GetAllProducts() ⇒ state.values.toSeq
          case Tax(range, imRange, rTax, items) ⇒
            calculateTax(items, range, imRange, rTax)
        }
      }
  }

  object Runner {
    def impureRunner[A](program: Free[SalesTaxDSL, A]): scalaz.Id.Id[A] =
      program.foldMap(SalesTaxInterpreter.ImpureInterpreter)
  }

}

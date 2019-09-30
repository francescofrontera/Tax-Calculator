package ffrontera.services.ops

import ffrontera.models.ProductEnum

object TaxOps {

  import ffrontera.Utils._

  @inline def zero: BigDecimal = BigDecimal("0.0")

  @inline private def calculateTax =
    (price: BigDecimal, range: BigDecimal) => price * range

  def composeCalculationAndScale(price: BigDecimal,
                                 basetax: BigDecimal,
                                 importedTax: BigDecimal,
                                 roundApproximation: BigDecimal,
                                 category: ProductEnum.Category,
                                 isImported: Boolean): BigDecimal = {
    val gTax =
      if (ProductEnum.notTaxCategory(category))
        calculateTax(price, basetax)
      else
        zero

    val iTax =
      if (isImported)
        calculateTax(price, importedTax)
      else
        zero

    (gTax + iTax) roundField roundApproximation
  }

}

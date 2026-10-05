package com.vaibhav.emicalc.core.fnf

import com.vaibhav.emicalc.core.coerceAtLeastZero
import com.vaibhav.emicalc.core.divideRate
import com.vaibhav.emicalc.core.toPaise
import com.vaibhav.emicalc.core.toRupees
import java.math.BigDecimal

/** A tax computation, broken out so the app can show its working. */
data class TaxComputation(
    val taxableIncome: BigDecimal,
    val slabTax: BigDecimal,
    val rebate87A: BigDecimal,
    val marginalRelief: BigDecimal,
    val surcharge: BigDecimal,
    val cess: BigDecimal,
    val total: BigDecimal,
)

/**
 * Income tax under the new regime.
 *
 * This estimates the tax on a settlement. It is **not** the TDS an employer will
 * actually deduct: employers deduct against projected full-year income, prior-employer
 * figures and declared deductions, none of which this module can see. Present it as an
 * estimate and say so in the UI.
 */
object IncomeTax {

    private val HUNDRED = BigDecimal("100")

    /** Slab tax before rebate, surcharge and cess. */
    internal fun slabTax(taxableIncome: BigDecimal, slabs: List<TaxSlab>): BigDecimal {
        var tax = BigDecimal.ZERO
        var lower = BigDecimal.ZERO
        for (slab in slabs) {
            val upper = slab.upTo?.min(taxableIncome) ?: taxableIncome
            if (upper > lower) {
                tax += (upper - lower).multiply(slab.ratePercent).divideRate(HUNDRED)
            }
            if (slab.upTo == null || taxableIncome <= slab.upTo) break
            lower = slab.upTo
        }
        return tax.toPaise()
    }

    private fun surcharge(
        taxableIncome: BigDecimal,
        baseTax: BigDecimal,
        bands: List<SurchargeBand>,
    ): BigDecimal {
        val rate = bands.filter { taxableIncome > it.above }.maxByOrNull { it.above }?.ratePercent
            ?: return BigDecimal.ZERO
        return baseTax.multiply(rate).divideRate(HUNDRED).toPaise()
    }

    /** Slab tax plus surcharge, after the Section 87A rebate but before cess. */
    private fun taxBeforeCess(income: BigDecimal, config: StatutoryConfig): BigDecimal {
        val slab = slabTax(income, config.slabs)
        val rebate = if (income <= config.rebate87AIncomeLimit) {
            slab.min(config.rebate87AMaxAmount)
        } else {
            BigDecimal.ZERO
        }
        val afterRebate = (slab - rebate).coerceAtLeastZero()
        return (afterRebate + surcharge(income, afterRebate, config.surcharges)).toPaise()
    }

    /**
     * Computes tax with marginal relief.
     *
     * Marginal relief applies at the rebate threshold and at every surcharge threshold:
     * crossing one must never cost more in tax than the extra income earned. It is
     * applied by capping the liability at (liability exactly at the threshold) plus the
     * income above it, for whichever threshold binds hardest.
     */
    fun compute(grossIncome: BigDecimal, config: StatutoryConfig = StatutoryConfig.DEFAULT): TaxComputation {
        val taxable = (grossIncome - config.standardDeduction).coerceAtLeastZero().toPaise()

        val slab = slabTax(taxable, config.slabs)
        val rebate = if (taxable <= config.rebate87AIncomeLimit) {
            slab.min(config.rebate87AMaxAmount)
        } else {
            BigDecimal.ZERO
        }

        val unrelieved = taxBeforeCess(taxable, config)

        val thresholds = buildList {
            add(config.rebate87AIncomeLimit)
            addAll(config.surcharges.map { it.above })
        }
        var relieved = unrelieved
        for (threshold in thresholds) {
            if (taxable > threshold) {
                val ceiling = (taxBeforeCess(threshold, config) + (taxable - threshold)).toPaise()
                relieved = relieved.min(ceiling)
            }
        }
        val marginalRelief = (unrelieved - relieved).coerceAtLeastZero()

        val afterRebate = (slab - rebate).coerceAtLeastZero()
        val surchargeAmount = (relieved - afterRebate).coerceAtLeastZero()
        val cess = relieved.multiply(config.cessPercent).divideRate(HUNDRED).toPaise()

        return TaxComputation(
            taxableIncome = taxable,
            slabTax = slab,
            rebate87A = rebate,
            marginalRelief = marginalRelief,
            surcharge = surchargeAmount,
            cess = cess,
            total = (relieved + cess).toRupees(),
        )
    }
}

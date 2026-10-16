package com.shreekrishna.organics.order.service;

import com.shreekrishna.organics.order.entity.Order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Read-only tax presentation helper for an already-created order.
 *
 * The current order schema stores aggregate product GST in orders.tax_amount,
 * but does not snapshot a per-item GST rate. This helper therefore exposes an
 * effective aggregate rate for display and preserves the recorded tax amount.
 */
final class OrderTaxView {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.02");
    private static final String SUPPLIER_STATE = "Maharashtra";

    private OrderTaxView() {}

    static Breakdown from(Order order) {
        BigDecimal subtotal = amount(order.getSubtotal());
        BigDecimal delivery = amount(order.getDeliveryCharge());
        BigDecimal cod = amount(order.getCodCharge());
        BigDecimal discount = amount(order.getDiscountAmount());
        BigDecimal tax = amount(order.getTaxAmount());
        BigDecimal total = amount(order.getTotalAmount());

        BigDecimal totalBeforeAdditionalTax = subtotal
                .add(delivery)
                .add(cod)
                .subtract(discount);

        BigDecimal taxActuallyAddedToTotal = total.subtract(totalBeforeAdditionalTax);
        boolean gstIncludedInSubtotal = tax.signum() > 0
                && taxActuallyAddedToTotal.subtract(tax).abs().compareTo(TOLERANCE) > 0;

        BigDecimal taxableAmount;
        if (tax.signum() == 0) {
            taxableAmount = subtotal;
        } else if (gstIncludedInSubtotal) {
            taxableAmount = subtotal.subtract(tax).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        } else {
            taxableAmount = subtotal;
        }

        BigDecimal effectiveGstRate = BigDecimal.ZERO.setScale(2);
        if (tax.signum() > 0 && taxableAmount.signum() > 0) {
            effectiveGstRate = tax.multiply(HUNDRED)
                    .divide(taxableAmount, 2, RoundingMode.HALF_UP);
        }

        boolean intraState = SUPPLIER_STATE.equalsIgnoreCase(safe(order.getShippingState()));
        BigDecimal cgst = BigDecimal.ZERO.setScale(2);
        BigDecimal sgst = BigDecimal.ZERO.setScale(2);
        BigDecimal igst = BigDecimal.ZERO.setScale(2);

        if (tax.signum() > 0) {
            if (intraState) {
                cgst = tax.divide(TWO, 2, RoundingMode.HALF_UP);
                sgst = tax.subtract(cgst).setScale(2, RoundingMode.HALF_UP);
            } else {
                igst = tax;
            }
        }

        return new Breakdown(
                taxableAmount,
                effectiveGstRate,
                cgst,
                sgst,
                igst,
                gstIncludedInSubtotal
        );
    }

    private static BigDecimal amount(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    record Breakdown(BigDecimal taxableAmount,
                     BigDecimal effectiveGstRate,
                     BigDecimal cgstAmount,
                     BigDecimal sgstAmount,
                     BigDecimal igstAmount,
                     boolean gstIncludedInSubtotal) {
        BigDecimal cgstRate() {
            return igstAmount.signum() == 0
                    ? effectiveGstRate.divide(TWO, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2);
        }

        BigDecimal sgstRate() {
            return cgstRate();
        }

        BigDecimal igstRate() {
            return igstAmount.signum() > 0 ? effectiveGstRate : BigDecimal.ZERO.setScale(2);
        }
    }
}

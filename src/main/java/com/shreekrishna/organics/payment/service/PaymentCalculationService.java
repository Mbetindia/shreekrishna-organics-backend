package com.shreekrishna.organics.payment.service;

import com.shreekrishna.organics.payment.entity.PaymentMethod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PaymentCalculationService {

    private static final BigDecimal COD_RATE =
            new BigDecimal("0.05");

    public BigDecimal calculateCodCharge(
            BigDecimal orderAmount,
            PaymentMethod paymentMethod
    ) {
        validateAmount(orderAmount);

        if (paymentMethod == null) {
            throw new IllegalArgumentException(
                    "Payment method is required"
            );
        }

        if (paymentMethod == PaymentMethod.COD) {
            return orderAmount.multiply(COD_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal calculateFinalAmount(
            BigDecimal orderAmount,
            PaymentMethod paymentMethod
    ) {
        validateAmount(orderAmount);

        BigDecimal codCharge = calculateCodCharge(
                orderAmount,
                paymentMethod
        );

        return orderAmount.add(codCharge)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateAmount(BigDecimal orderAmount) {
        if (orderAmount == null ||
                orderAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Invalid order amount"
            );
        }
    }
}
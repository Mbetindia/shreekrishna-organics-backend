package com.shreekrishna.organics.payment.service;

import com.shreekrishna.organics.payment.entity.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentCalculationServiceTest {

    private final PaymentCalculationService service =
            new PaymentCalculationService();

    @Test
    void codShouldAddFivePercent() {
        BigDecimal result = service.calculateFinalAmount(
                new BigDecimal("1000.00"),
                PaymentMethod.COD
        );

        assertEquals(new BigDecimal("1050.00"), result);
    }

    @Test
    void razorpayShouldNotAddCodCharge() {
        BigDecimal result = service.calculateFinalAmount(
                new BigDecimal("1000.00"),
                PaymentMethod.RAZORPAY
        );

        assertEquals(new BigDecimal("1000.00"), result);
    }

    @Test
    void negativeAmountShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculateFinalAmount(
                        new BigDecimal("-100.00"),
                        PaymentMethod.COD
                )
        );
    }
}
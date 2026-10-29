package com.spe.calculator.service;

import com.spe.calculator.exception.DivisionByZeroException;
import java.math.BigDecimal;
import java.math.MathContext;
import org.springframework.stereotype.Service;

/**
 * Arithmetic operations on BigDecimal so results are exact for decimal input
 * (0.1 + 0.2 = 0.3) and division is rounded to 16 significant digits.
 */
@Service
public class CalculatorService {

    private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL64;

    public BigDecimal add(BigDecimal a, BigDecimal b) {
        return normalize(a.add(b));
    }

    public BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return normalize(a.subtract(b));
    }

    public BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return normalize(a.multiply(b));
    }

    public BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (b.signum() == 0) {
            throw new DivisionByZeroException();
        }
        return normalize(a.divide(b, DIVISION_CONTEXT));
    }

    /** Drop trailing zeros so 2.50 + 0.50 is returned as 3, not 3.00. */
    private static BigDecimal normalize(BigDecimal value) {
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }
}

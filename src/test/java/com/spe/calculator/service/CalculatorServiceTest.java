package com.spe.calculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spe.calculator.exception.DivisionByZeroException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculatorServiceTest {

    private final CalculatorService service = new CalculatorService();

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Nested
    @DisplayName("add")
    class Add {

        @ParameterizedTest(name = "{0} + {1} = {2}")
        @CsvSource({"2, 3, 5", "-4, 1, -3", "0.1, 0.2, 0.3", "2.50, 0.50, 3", "0, 0, 0"})
        void addsNumbers(String a, String b, String expected) {
            assertThat(service.add(n(a), n(b))).isEqualByComparingTo(expected);
        }
    }

    @Nested
    @DisplayName("subtract")
    class Subtract {

        @ParameterizedTest(name = "{0} - {1} = {2}")
        @CsvSource({"5, 3, 2", "3, 5, -2", "1.5, 0.25, 1.25", "-2, -2, 0"})
        void subtractsNumbers(String a, String b, String expected) {
            assertThat(service.subtract(n(a), n(b))).isEqualByComparingTo(expected);
        }
    }

    @Nested
    @DisplayName("multiply")
    class Multiply {

        @ParameterizedTest(name = "{0} * {1} = {2}")
        @CsvSource({"4, 3, 12", "-2, 3, -6", "0.5, 0.5, 0.25", "7, 0, 0"})
        void multipliesNumbers(String a, String b, String expected) {
            assertThat(service.multiply(n(a), n(b))).isEqualByComparingTo(expected);
        }
    }

    @Nested
    @DisplayName("divide")
    class Divide {

        @ParameterizedTest(name = "{0} / {1} = {2}")
        @CsvSource({"10, 2, 5", "7, 2, 3.5", "-9, 3, -3", "0, 5, 0", "1, 3, 0.3333333333333333"})
        void dividesNumbers(String a, String b, String expected) {
            assertThat(service.divide(n(a), n(b))).isEqualByComparingTo(expected);
        }

        @Test
        void divisionByZeroThrows() {
            assertThatThrownBy(() -> service.divide(n("10"), BigDecimal.ZERO))
                    .isInstanceOf(DivisionByZeroException.class)
                    .hasMessage("Division by zero is not allowed");
        }

        @Test
        void divisionByZeroWithScaleThrows() {
            assertThatThrownBy(() -> service.divide(n("1"), n("0.000")))
                    .isInstanceOf(DivisionByZeroException.class);
        }
    }

    @Test
    void resultsHaveNoTrailingZeros() {
        assertThat(service.add(n("2.50"), n("0.50"))).hasToString("3");
        assertThat(service.subtract(n("1.00"), n("1.00"))).hasToString("0");
    }
}

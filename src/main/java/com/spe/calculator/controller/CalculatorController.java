package com.spe.calculator.controller;

import com.spe.calculator.dto.CalculationResponse;
import com.spe.calculator.exception.InvalidOperandException;
import com.spe.calculator.service.CalculatorService;
import java.math.BigDecimal;
import java.util.function.BinaryOperator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CalculatorController {

    /** Guards against inputs like 1e999999999 that would make BigDecimal arithmetic explode. */
    static final int MAX_PRECISION = 50;
    static final int MAX_EXPONENT = 1000;

    private final CalculatorService calculatorService;

    public CalculatorController(CalculatorService calculatorService) {
        this.calculatorService = calculatorService;
    }

    @GetMapping("/add")
    public CalculationResponse add(@RequestParam BigDecimal a, @RequestParam BigDecimal b) {
        return calculate("add", a, b, calculatorService::add);
    }

    @GetMapping("/subtract")
    public CalculationResponse subtract(@RequestParam BigDecimal a, @RequestParam BigDecimal b) {
        return calculate("subtract", a, b, calculatorService::subtract);
    }

    @GetMapping("/multiply")
    public CalculationResponse multiply(@RequestParam BigDecimal a, @RequestParam BigDecimal b) {
        return calculate("multiply", a, b, calculatorService::multiply);
    }

    @GetMapping("/divide")
    public CalculationResponse divide(@RequestParam BigDecimal a, @RequestParam BigDecimal b) {
        return calculate("divide", a, b, calculatorService::divide);
    }

    private CalculationResponse calculate(String operation, BigDecimal a, BigDecimal b,
                                          BinaryOperator<BigDecimal> op) {
        validate("a", a);
        validate("b", b);
        return new CalculationResponse(operation, a, b, op.apply(a, b));
    }

    private static void validate(String name, BigDecimal value) {
        int exponent = value.precision() - value.scale() - 1;
        if (value.precision() > MAX_PRECISION || Math.abs(exponent) > MAX_EXPONENT) {
            throw new InvalidOperandException(String.format(
                    "Parameter '%s' is out of range: at most %d significant digits and an exponent within ±%d",
                    name, MAX_PRECISION, MAX_EXPONENT));
        }
    }
}

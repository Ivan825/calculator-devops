package com.spe.calculator.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CalculatorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}({1}, {2}) = {3}")
    @CsvSource({
            "add, 2, 3, 5",
            "subtract, 10, 4, 6",
            "multiply, 6, 7, 42",
            "divide, 7, 2, 3.5"
    })
    void operationReturnsJsonResult(String operation, String a, String b, String expected) throws Exception {
        mockMvc.perform(get("/api/v1/" + operation).param("a", a).param("b", b))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.operation").value(operation))
                .andExpect(jsonPath("$.result").value(Double.parseDouble(expected)));
    }

    @Test
    void addReturnsDocumentedResponseShape() throws Exception {
        mockMvc.perform(get("/api/v1/add").param("a", "2").param("b", "3"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"operation\":\"add\",\"a\":2,\"b\":3,\"result\":5}", JsonCompareMode.STRICT));
    }

    @Test
    void decimalArithmeticIsExact() throws Exception {
        mockMvc.perform(get("/api/v1/add").param("a", "0.1").param("b", "0.2"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"result\":0.3}")));
    }

    @Test
    void divideByZeroReturns400WithErrorJson() throws Exception {
        mockMvc.perform(get("/api/v1/divide").param("a", "5").param("b", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Division by zero is not allowed"))
                .andExpect(jsonPath("$.path").value("/api/v1/divide"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void missingParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/add").param("a", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'b' is missing"));
    }

    @Test
    void emptyParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/multiply").param("a", "").param("b", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'a' is missing"));
    }

    @Test
    void nonNumericParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/subtract").param("a", "abc").param("b", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'a' must be a number, got 'abc'"));
    }

    @Test
    void outOfRangeParameterReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/multiply").param("a", "1e999999999").param("b", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Parameter 'a' is out of range")));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

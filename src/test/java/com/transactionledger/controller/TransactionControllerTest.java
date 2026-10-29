package com.transactionledger.controller;

import com.transactionledger.exception.GlobalExceptionHandler;
import com.transactionledger.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Test
    @DisplayName("invalid transaction type is rejected")
    void invalidTransactionType() throws Exception {
        mockMvc.perform(post("/api/accounts/1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TRANSFER\",\"amount\":10,\"referenceId\":\"REF-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transaction type must be CREDIT or DEBIT"));
    }

    @Test
    @DisplayName("zero amount is rejected")
    void invalidAmountZero() throws Exception {
        mockMvc.perform(post("/api/accounts/1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CREDIT\",\"amount\":0,\"referenceId\":\"REF-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Amount must be greater than zero"));
    }

    @Test
    @DisplayName("negative amount is rejected")
    void invalidAmountNegative() throws Exception {
        mockMvc.perform(post("/api/accounts/1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DEBIT\",\"amount\":-5,\"referenceId\":\"REF-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
    }

    @Test
    @DisplayName("missing reference id is rejected")
    void missingReferenceId() throws Exception {
        mockMvc.perform(post("/api/accounts/1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CREDIT\",\"amount\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("referenceId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Reference id is required"));
    }

    @Test
    @DisplayName("invalid type filter and pagination parameters are rejected")
    void invalidTypeFilterAndPagination() throws Exception {
        mockMvc.perform(get("/api/accounts/1/transactions").param("type", "TRANSFER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transaction type must be CREDIT or DEBIT"));

        mockMvc.perform(get("/api/accounts/1/transactions").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pagination parameters must be numbers"));

        mockMvc.perform(get("/api/accounts/abc/transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Account id must be a number"));
    }
}

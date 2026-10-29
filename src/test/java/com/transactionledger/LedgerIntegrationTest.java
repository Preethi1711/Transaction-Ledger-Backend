package com.transactionledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactionledger.entity.Account;
import com.transactionledger.entity.Transaction;
import com.transactionledger.entity.TransactionType;
import com.transactionledger.repository.AccountRepository;
import com.transactionledger.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("new accounts ignore a client-supplied balance and start at zero")
    void initialBalanceIsAlwaysZero() throws Exception {
        JsonNode created = read(mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Household\",\"balance\":500}"))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(created.get("name").asText()).isEqualTo("Household");
        assertThat(new BigDecimal(created.get("balance").asText())).isEqualByComparingTo("0.00");
        assertThat(created.get("createdAt").asText()).isNotBlank();

        long accountId = created.get("id").asLong();
        JsonNode balance = read(mockMvc.perform(get("/api/accounts/" + accountId + "/balance"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(balance.get("accountId").asLong()).isEqualTo(accountId);
        assertThat(new BigDecimal(balance.get("balance").asText())).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("credits and debits keep the stored balance consistent, including an exact debit")
    void creditAndDebitUpdateBalanceConsistently() throws Exception {
        long accountId = createAccount("Spending");

        postTransaction(accountId, "CREDIT", "100.25", "C-1").andExpect(status().isCreated());
        postTransaction(accountId, "DEBIT", "40.25", "D-1").andExpect(status().isCreated());
        postTransaction(accountId, "DEBIT", "60.00", "D-2").andExpect(status().isCreated());

        JsonNode balance = read(mockMvc.perform(get("/api/accounts/" + accountId + "/balance"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(new BigDecimal(balance.get("balance").asText())).isEqualByComparingTo("0.00");
        assertThat(transactionRepository.countByAccount_Id(accountId)).isEqualTo(3);
    }

    @Test
    @DisplayName("a rejected debit does not create a transaction or change the balance")
    void rejectedDebitLeavesBalanceUnchanged() throws Exception {
        long accountId = createAccount("Empty");
        postTransaction(accountId, "CREDIT", "20.00", "C-1").andExpect(status().isCreated());

        mockMvc.perform(post("/api/accounts/" + accountId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DEBIT\",\"amount\":20.01,\"referenceId\":\"TOO-MUCH\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Insufficient balance for this debit"));

        Account account = accountRepository.findById(accountId).orElseThrow();
        assertThat(account.getBalance()).isEqualByComparingTo("20.00");
        assertThat(transactionRepository.countByAccount_Id(accountId)).isEqualTo(1);
        assertThat(transactionRepository.existsByAccount_IdAndReferenceId(accountId, "TOO-MUCH")).isFalse();
    }

    @Test
    @DisplayName("duplicate reference ids are rejected for one account and allowed on another")
    void duplicateReferenceIsScopedToTheAccount() throws Exception {
        long first = createAccount("First");
        long second = createAccount("Second");

        postTransaction(first, "CREDIT", "12.00", "SHARED-REF").andExpect(status().isCreated());
        mockMvc.perform(post("/api/accounts/" + first + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CREDIT\",\"amount\":3,\"referenceId\":\"SHARED-REF\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A transaction with reference id 'SHARED-REF' already exists for this account"));

        postTransaction(second, "CREDIT", "4.00", "SHARED-REF").andExpect(status().isCreated());

        assertThat(accountRepository.findById(first).orElseThrow().getBalance()).isEqualByComparingTo("12.00");
        assertThat(accountRepository.findById(second).orElseThrow().getBalance()).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("the database unique constraint rejects a duplicate reference for the same account")
    void databaseRejectsDuplicateReference() {
        Account account = accountRepository.saveAndFlush(new Account("Constraint"));
        transactionRepository.saveAndFlush(new Transaction(
                account, TransactionType.CREDIT, new BigDecimal("1.00"), null, "DB-REF"));

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(new Transaction(
                account, TransactionType.CREDIT, new BigDecimal("2.00"), null, "DB-REF")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("history is newest first and supports type filters and pagination")
    void historyFilteringPaginationAndSort() throws Exception {
        long accountId = createAccount("History");
        postTransaction(accountId, "CREDIT", "5.00", "H-1").andExpect(status().isCreated());
        postTransaction(accountId, "DEBIT", "1.00", "H-2").andExpect(status().isCreated());
        postTransaction(accountId, "CREDIT", "2.00", "H-3").andExpect(status().isCreated());

        JsonNode all = read(mockMvc.perform(get("/api/accounts/" + accountId + "/transactions")
                        .param("page", "1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(all.get("totalRecords").asLong()).isEqualTo(3);
        assertThat(all.get("transactions").get(0).get("referenceId").asText()).isEqualTo("H-3");
        assertThat(all.get("transactions").get(1).get("referenceId").asText()).isEqualTo("H-2");
        assertThat(all.get("transactions").get(2).get("referenceId").asText()).isEqualTo("H-1");

        JsonNode credits = read(mockMvc.perform(get("/api/accounts/" + accountId + "/transactions")
                        .param("type", "CREDIT"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(credits.get("totalRecords").asLong()).isEqualTo(2);
        assertThat(credits.get("transactions").get(0).get("referenceId").asText()).isEqualTo("H-3");
        assertThat(credits.get("transactions").get(1).get("referenceId").asText()).isEqualTo("H-1");

        JsonNode debits = read(mockMvc.perform(get("/api/accounts/" + accountId + "/transactions")
                        .param("type", "DEBIT"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(debits.get("totalRecords").asLong()).isEqualTo(1);
        assertThat(debits.get("transactions").get(0).get("referenceId").asText()).isEqualTo("H-2");

        JsonNode page = read(mockMvc.perform(get("/api/accounts/" + accountId + "/transactions")
                        .param("page", "2")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(page.get("page").asInt()).isEqualTo(2);
        assertThat(page.get("pageSize").asInt()).isEqualTo(2);
        assertThat(page.get("totalRecords").asLong()).isEqualTo(3);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("transactions")).hasSize(1);
        assertThat(page.get("transactions").get(0).get("referenceId").asText()).isEqualTo("H-1");
    }

    @Test
    @DisplayName("invalid pagination and unknown accounts return errors")
    void invalidPaginationAndUnknownAccount() throws Exception {
        long accountId = createAccount("Paging");

        mockMvc.perform(get("/api/accounts/" + accountId + "/transactions").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Page must be greater than or equal to 1"));
        mockMvc.perform(get("/api/accounts/" + accountId + "/transactions").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Limit must be greater than or equal to 1"));
        mockMvc.perform(get("/api/accounts/" + accountId + "/transactions").param("limit", "-3"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/accounts/999999/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found: 999999"));
        mockMvc.perform(get("/api/accounts/999999/transactions"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/accounts/999999/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CREDIT\",\"amount\":1,\"referenceId\":\"MISSING\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("OpenAPI documents the account and transaction endpoints and there is no update or delete API")
    void apiIsDocumentedAndTransactionsAreImmutable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/accounts']").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/{accountId}/balance']").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/{accountId}/transactions']").exists())
                .andExpect(jsonPath("$.paths['/api/transfers']").exists());

        boolean hasMutatingMethod = handlerMapping.getHandlerMethods().keySet().stream()
                .map(RequestMappingInfo::getMethodsCondition)
                .flatMap(condition -> condition.getMethods().stream())
                .anyMatch(method -> method.name().equals("PUT")
                        || method.name().equals("DELETE")
                        || method.name().equals("PATCH"));
        assertThat(hasMutatingMethod).isFalse();
    }

    @Test
    @DisplayName("a transfer updates both balances together and a failure leaves both unchanged")
    void transferIsAtomic() throws Exception {
        long source = createAccount("Account A");
        long destination = createAccount("Account B");
        postTransaction(source, "CREDIT", "100.00", "OPEN-A").andExpect(status().isCreated());
        postTransaction(destination, "CREDIT", "50.00", "OPEN-B").andExpect(status().isCreated());

        JsonNode transfer = read(mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":%d,"toAccountId":%d,"amount":30.00,"description":"Payment","referenceId":"TRF-1"}
                                """.formatted(source, destination)))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(transfer.get("fromAccountId").asLong()).isEqualTo(source);
        assertThat(transfer.get("toAccountId").asLong()).isEqualTo(destination);
        assertThat(new BigDecimal(transfer.get("fromBalance").asText())).isEqualByComparingTo("70.00");
        assertThat(new BigDecimal(transfer.get("toBalance").asText())).isEqualByComparingTo("80.00");
        assertThat(transfer.get("debit").get("type").asText()).isEqualTo("DEBIT");
        assertThat(transfer.get("credit").get("type").asText()).isEqualTo("CREDIT");
        assertThat(transfer.get("debit").get("referenceId").asText()).isEqualTo("TRF-1");
        assertThat(transfer.get("credit").get("referenceId").asText()).isEqualTo("TRF-1");

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":%d,"toAccountId":%d,"amount":150.00,"referenceId":"TRF-TOO-MUCH"}
                                """.formatted(source, destination)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Insufficient balance for this debit"));

        assertThat(accountRepository.findById(source).orElseThrow().getBalance()).isEqualByComparingTo("70.00");
        assertThat(accountRepository.findById(destination).orElseThrow().getBalance()).isEqualByComparingTo("80.00");
        assertThat(transactionRepository.countByAccount_Id(source)).isEqualTo(2);
        assertThat(transactionRepository.countByAccount_Id(destination)).isEqualTo(2);

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":%d,"toAccountId":999999,"amount":10.00,"referenceId":"TRF-MISSING"}
                                """.formatted(source)))
                .andExpect(status().isNotFound());
        assertThat(accountRepository.findById(source).orElseThrow().getBalance()).isEqualByComparingTo("70.00");

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":%d,"toAccountId":%d,"amount":10.00,"referenceId":"TRF-SELF"}
                                """.formatted(source, source)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Source and destination accounts must be different"));

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":%d,"toAccountId":%d,"amount":5.00,"referenceId":"TRF-1"}
                                """.formatted(source, destination)))
                .andExpect(status().isConflict());
        assertThat(accountRepository.findById(source).orElseThrow().getBalance()).isEqualByComparingTo("70.00");
        assertThat(accountRepository.findById(destination).orElseThrow().getBalance()).isEqualByComparingTo("80.00");
    }

    private long createAccount(String name) throws Exception {
        JsonNode created = read(mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        return created.get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions postTransaction(
            long accountId,
            String type,
            String amount,
            String referenceId
    ) throws Exception {
        String body = """
                {"type":"%s","amount":%s,"description":"Test","referenceId":"%s"}
                """.formatted(type, amount, referenceId);
        return mockMvc.perform(post("/api/accounts/" + accountId + "/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}

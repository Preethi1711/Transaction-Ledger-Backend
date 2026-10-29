package com.transactionledger.service;

import com.transactionledger.dto.AccountResponse;
import com.transactionledger.dto.BalanceResponse;
import com.transactionledger.dto.CreateAccountRequest;
import com.transactionledger.entity.Account;
import com.transactionledger.exception.AccountNotFoundException;
import com.transactionledger.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    @DisplayName("account creation stores the name and always starts at balance zero")
    void createAccountStartsAtZeroBalance() {
        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 1L);
            return account;
        });

        AccountResponse response = accountService.createAccount(new CreateAccountRequest("  Household  "));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Household");
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo("0.00");
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Household");
        assertThat(response.balance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("missing account name is rejected")
    void missingAccountNameIsRejected() {
        assertThatThrownBy(() -> accountService.createAccount(new CreateAccountRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account name is required");
    }

    @Test
    @DisplayName("balance is returned for an existing account")
    void existingAccountBalance() {
        Account account = new Account("Savings");
        account.applyCredit(new BigDecimal("15.50"));
        ReflectionTestUtils.setField(account, "id", 4L);
        when(accountRepository.findById(4L)).thenReturn(Optional.of(account));

        BalanceResponse response = accountService.getBalance(4L);

        assertThat(response.accountId()).isEqualTo(4L);
        assertThat(response.balance()).isEqualByComparingTo("15.50");
    }

    @Test
    @DisplayName("balance lookup fails when the account does not exist")
    void missingAccountBalance() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance(99L))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: 99");
    }
}

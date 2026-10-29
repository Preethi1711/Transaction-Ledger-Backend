package com.transactionledger.service;

import com.transactionledger.dto.CreateTransactionRequest;
import com.transactionledger.dto.TransactionPageResponse;
import com.transactionledger.dto.TransactionResponse;
import com.transactionledger.dto.TransferRequest;
import com.transactionledger.dto.TransferResponse;
import com.transactionledger.entity.Account;
import com.transactionledger.entity.Transaction;
import com.transactionledger.entity.TransactionType;
import com.transactionledger.exception.AccountNotFoundException;
import com.transactionledger.exception.DuplicateTransactionException;
import com.transactionledger.exception.InsufficientBalanceException;
import com.transactionledger.exception.InvalidPaginationException;
import com.transactionledger.repository.AccountRepository;
import com.transactionledger.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    @DisplayName("credit increases the account balance and saves the transaction")
    void creditIncreasesBalance() {
        Account account = account(1L, "0.00");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 10L);
            return transaction;
        });

        TransactionResponse response = transactionService.addTransaction(
                1L,
                new CreateTransactionRequest(TransactionType.CREDIT, new BigDecimal("25.50"), " Salary ", " REF-1 ")
        );

        assertThat(account.getBalance()).isEqualByComparingTo("25.50");
        assertThat(response.amount()).isEqualByComparingTo("25.50");
        assertThat(response.referenceId()).isEqualTo("REF-1");
        assertThat(response.description()).isEqualTo("Salary");
        assertThat(response.type()).isEqualTo(TransactionType.CREDIT);
        verify(accountRepository).saveAndFlush(account);
    }

    @Test
    @DisplayName("debit with sufficient balance decreases the account balance")
    void debitDecreasesBalance() {
        Account account = account(2L, "100.00");
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(account));
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.addTransaction(
                2L,
                new CreateTransactionRequest(TransactionType.DEBIT, new BigDecimal("40.00"), null, "REF-2")
        );

        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("debit with insufficient balance does not save a transaction or change the balance")
    void debitWithInsufficientBalanceIsRejected() {
        Account account = account(3L, "10.00");
        when(accountRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> transactionService.addTransaction(
                3L,
                new CreateTransactionRequest(TransactionType.DEBIT, new BigDecimal("10.01"), null, "REF-3")
        )).isInstanceOf(InsufficientBalanceException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("duplicate reference id for the same account is rejected without a balance change")
    void duplicateReferenceForSameAccountIsRejected() {
        Account account = account(4L, "50.00");
        when(accountRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccount_IdAndReferenceId(4L, "REF-SAME")).thenReturn(true);

        assertThatThrownBy(() -> transactionService.addTransaction(
                4L,
                new CreateTransactionRequest(TransactionType.CREDIT, new BigDecimal("5.00"), null, "REF-SAME")
        )).isInstanceOf(DuplicateTransactionException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("50.00");
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("the same reference id is accepted when that account does not already have it")
    void sameReferenceIsAllowedForAnotherAccount() {
        Account account = account(5L, "0.00");
        when(accountRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccount_IdAndReferenceId(5L, "SHARED")).thenReturn(false);
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.addTransaction(
                5L,
                new CreateTransactionRequest(TransactionType.CREDIT, new BigDecimal("8.00"), null, "SHARED")
        );

        assertThat(response.referenceId()).isEqualTo("SHARED");
        assertThat(account.getBalance()).isEqualByComparingTo("8.00");
    }

    @Test
    @DisplayName("a unique-constraint failure rolls the operation back to a duplicate error")
    void databaseDuplicateIsTranslatedAndNotCommittedByTheService() {
        Account account = account(6L, "0.00");
        when(accountRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(account));
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_transactions_account_reference\""));

        assertThatThrownBy(() -> transactionService.addTransaction(
                6L,
                new CreateTransactionRequest(TransactionType.CREDIT, new BigDecimal("3.00"), null, "REF-RACE")
        )).isInstanceOf(DuplicateTransactionException.class);

        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("adding a transaction fails when the account does not exist")
    void addTransactionRequiresAnAccount() {
        when(accountRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.addTransaction(
                404L,
                new CreateTransactionRequest(TransactionType.CREDIT, new BigDecimal("1.00"), null, "REF")
        )).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("history returns both types, newest first, with pagination metadata")
    void historyReturnsBothTypesNewestFirst() {
        Account account = account(7L, "0.00");
        Transaction transaction = new Transaction(account, TransactionType.DEBIT, new BigDecimal("2.00"), "Rent", "REF-H");
        ReflectionTestUtils.setField(transaction, "id", 20L);
        when(accountRepository.existsById(7L)).thenReturn(true);
        when(transactionRepository.findByAccount_Id(eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaction), PageRequest.of(0, 10), 21));

        TransactionPageResponse page = transactionService.getHistory(7L, null, 1, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findByAccount_Id(eq(7L), captor.capture());
        Sort.Order createdAt = captor.getValue().getSort().getOrderFor("createdAt");
        assertThat(createdAt).isNotNull();
        assertThat(createdAt.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(10);
        assertThat(page.totalRecords()).isEqualTo(21);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.transactions()).hasSize(1);
        verify(transactionRepository, never()).findByAccount_IdAndType(any(), any(), any());
    }

    @Test
    @DisplayName("history can be filtered to CREDIT")
    void historyFiltersCredit() {
        when(accountRepository.existsById(8L)).thenReturn(true);
        when(transactionRepository.findByAccount_IdAndType(eq(8L), eq(TransactionType.CREDIT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        transactionService.getHistory(8L, TransactionType.CREDIT, 1, 5);

        verify(transactionRepository).findByAccount_IdAndType(eq(8L), eq(TransactionType.CREDIT), any(Pageable.class));
        verify(transactionRepository, never()).findByAccount_Id(any(), any());
    }

    @Test
    @DisplayName("history can be filtered to DEBIT")
    void historyFiltersDebit() {
        when(accountRepository.existsById(9L)).thenReturn(true);
        when(transactionRepository.findByAccount_IdAndType(eq(9L), eq(TransactionType.DEBIT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        transactionService.getHistory(9L, TransactionType.DEBIT, 2, 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findByAccount_IdAndType(eq(9L), eq(TransactionType.DEBIT), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("invalid pagination values are rejected")
    void invalidPaginationIsRejected() {
        assertThatThrownBy(() -> transactionService.getHistory(1L, null, 0, 10))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessage("Page must be greater than or equal to 1");
        assertThatThrownBy(() -> transactionService.getHistory(1L, null, -1, 10))
                .isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> transactionService.getHistory(1L, null, 1, 0))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessage("Limit must be greater than or equal to 1");
        assertThatThrownBy(() -> transactionService.getHistory(1L, null, 1, -5))
                .isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> transactionService.getHistory(1L, null, 1, 101))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessage("Limit must not exceed 100");
        verify(accountRepository, never()).existsById(any());
    }

    @Test
    @DisplayName("a transfer debits the source and credits the destination")
    void transferMovesMoneyBetweenAccounts() {
        Account source = account(1L, "100.00");
        Account destination = account(2L, "50.00");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", transaction.getType() == TransactionType.DEBIT ? 11L : 12L);
            return transaction;
        });

        TransferResponse response = transactionService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("30.00"), "Payment", "TRF-1")
        );

        assertThat(source.getBalance()).isEqualByComparingTo("70.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("80.00");
        assertThat(response.fromBalance()).isEqualByComparingTo("70.00");
        assertThat(response.toBalance()).isEqualByComparingTo("80.00");
        assertThat(response.debit().type()).isEqualTo(TransactionType.DEBIT);
        assertThat(response.debit().accountId()).isEqualTo(1L);
        assertThat(response.credit().type()).isEqualTo(TransactionType.CREDIT);
        assertThat(response.credit().accountId()).isEqualTo(2L);
        assertThat(response.debit().referenceId()).isEqualTo("TRF-1");
        assertThat(response.credit().referenceId()).isEqualTo("TRF-1");
    }

    @Test
    @DisplayName("a transfer with insufficient balance does not change either account")
    void transferWithInsufficientBalanceChangesNothing() {
        Account source = account(1L, "100.00");
        Account destination = account(2L, "50.00");
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));

        assertThatThrownBy(() -> transactionService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("150.00"), null, "TRF-2")
        )).isInstanceOf(InsufficientBalanceException.class);

        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("50.00");
        verify(transactionRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a transfer to the same account is rejected")
    void transferToTheSameAccountIsRejected() {
        assertThatThrownBy(() -> transactionService.transfer(
                new TransferRequest(1L, 1L, new BigDecimal("10.00"), null, "TRF-3")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source and destination accounts must be different");

        verify(accountRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("a transfer fails when either account does not exist")
    void transferRequiresBothAccounts() {
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account(1L, "100.00")));
        when(accountRepository.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.transfer(
                new TransferRequest(1L, 9L, new BigDecimal("10.00"), null, "TRF-4")
        )).isInstanceOf(AccountNotFoundException.class);

        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a transfer is rejected when the reference id is already used on either account")
    void transferRejectsAReferenceAlreadyUsedOnEitherAccount() {
        Account source = account(2L, "100.00");
        Account destination = account(3L, "50.00");
        when(accountRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(destination));
        when(transactionRepository.existsByAccount_IdAndReferenceId(2L, "TRF-5")).thenReturn(false);
        when(transactionRepository.existsByAccount_IdAndReferenceId(3L, "TRF-5")).thenReturn(true);

        assertThatThrownBy(() -> transactionService.transfer(
                new TransferRequest(2L, 3L, new BigDecimal("10.00"), null, "TRF-5")
        )).isInstanceOf(DuplicateTransactionException.class);

        assertThat(source.getBalance()).isEqualByComparingTo("100.00");
        assertThat(destination.getBalance()).isEqualByComparingTo("50.00");
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("history fails when the account does not exist")
    void historyRequiresAnAccount() {
        when(accountRepository.existsById(44L)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.getHistory(44L, null, 1, 10))
                .isInstanceOf(AccountNotFoundException.class);
    }

    private Account account(Long id, String balance) {
        Account account = new Account("Account " + id);
        BigDecimal amount = new BigDecimal(balance);
        if (amount.signum() > 0) {
            account.applyCredit(amount);
        }
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}

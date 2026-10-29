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
import com.transactionledger.logging.AppLogger;
import com.transactionledger.repository.AccountRepository;
import com.transactionledger.repository.TransactionRepository;
import org.slf4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class TransactionService {

    static final int MAX_PAGE_SIZE = 100;

    private static final Logger log = AppLogger.get(TransactionService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResponse addTransaction(Long accountId, CreateTransactionRequest request) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        String referenceId = request.referenceId().trim();
        if (referenceId.isEmpty()) {
            throw new IllegalArgumentException("Reference id is required");
        }
        if (transactionRepository.existsByAccount_IdAndReferenceId(accountId, referenceId)) {
            throw new DuplicateTransactionException(referenceId);
        }

        BigDecimal amount = normalizeAmount(request.amount());
        if (request.type() == TransactionType.DEBIT && account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException();
        }

        if (request.type() == TransactionType.CREDIT) {
            account.applyCredit(amount);
        } else if (request.type() == TransactionType.DEBIT) {
            account.applyDebit(amount);
        } else {
            throw new IllegalArgumentException("Transaction type must be CREDIT or DEBIT");
        }

        Transaction transaction = new Transaction(
                account,
                request.type(),
                amount,
                normalizeDescription(request.description()),
                referenceId
        );

        try {
            Transaction saved = transactionRepository.saveAndFlush(transaction);
            accountRepository.saveAndFlush(account);
            TransactionResponse response = TransactionResponse.from(saved);
            log.info("Saved transaction id={} accountId={} type={} referenceId={}",
                    response.id(), accountId, response.type(), response.referenceId());
            return response;
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateReference(ex)) {
                throw new DuplicateTransactionException(referenceId);
            }
            throw ex;
        }
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        String referenceId = request.referenceId().trim();
        if (referenceId.isEmpty()) {
            throw new IllegalArgumentException("Reference id is required");
        }
        BigDecimal amount = normalizeAmount(request.amount());

        long firstId = Math.min(request.fromAccountId(), request.toAccountId());
        long secondId = Math.max(request.fromAccountId(), request.toAccountId());
        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));
        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));
        Account source = request.fromAccountId().equals(first.getId()) ? first : second;
        Account destination = request.toAccountId().equals(first.getId()) ? first : second;

        if (transactionRepository.existsByAccount_IdAndReferenceId(source.getId(), referenceId)
                || transactionRepository.existsByAccount_IdAndReferenceId(destination.getId(), referenceId)) {
            throw new DuplicateTransactionException(referenceId);
        }
        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException();
        }

        source.applyDebit(amount);
        destination.applyCredit(amount);
        String description = normalizeDescription(request.description());
        Transaction debit = new Transaction(source, TransactionType.DEBIT, amount, description, referenceId);
        Transaction credit = new Transaction(destination, TransactionType.CREDIT, amount, description, referenceId);

        try {
            Transaction savedDebit = transactionRepository.saveAndFlush(debit);
            Transaction savedCredit = transactionRepository.saveAndFlush(credit);
            accountRepository.saveAndFlush(source);
            accountRepository.saveAndFlush(destination);
            log.info("Transferred amount={} from accountId={} to accountId={} referenceId={}",
                    amount, source.getId(), destination.getId(), referenceId);
            return new TransferResponse(
                    source.getId(),
                    source.getBalance(),
                    destination.getId(),
                    destination.getBalance(),
                    TransactionResponse.from(savedDebit),
                    TransactionResponse.from(savedCredit)
            );
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateReference(ex)) {
                throw new DuplicateTransactionException(referenceId);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public TransactionPageResponse getHistory(Long accountId, TransactionType type, int page, int limit) {
        validatePagination(page, limit);
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }

        Pageable pageable = PageRequest.of(
                page - 1,
                limit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<Transaction> result = type == null
                ? transactionRepository.findByAccount_Id(accountId, pageable)
                : transactionRepository.findByAccount_IdAndType(accountId, type, pageable);

        List<TransactionResponse> transactions = result.getContent().stream()
                .map(TransactionResponse::from)
                .toList();
        log.info("Loaded transaction history accountId={} page={} size={} total={}",
                accountId, page, transactions.size(), result.getTotalElements());
        return new TransactionPageResponse(transactions, page, limit, result.getTotalElements(), result.getTotalPages());
    }

    private void validatePagination(int page, int limit) {
        if (page < 1) {
            throw new InvalidPaginationException("Page must be greater than or equal to 1");
        }
        if (limit < 1) {
            throw new InvalidPaginationException("Limit must be greater than or equal to 1");
        }
        if (limit > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException("Limit must not exceed " + MAX_PAGE_SIZE);
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Amount must have at most 2 decimal places");
        }
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isDuplicateReference(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause.getMessage();
        return message != null
                && message.toLowerCase(Locale.ROOT).contains("uk_transactions_account_reference");
    }
}

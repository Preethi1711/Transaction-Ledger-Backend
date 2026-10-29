package com.transactionledger.service;

import com.transactionledger.dto.AccountResponse;
import com.transactionledger.dto.BalanceResponse;
import com.transactionledger.dto.CreateAccountRequest;
import com.transactionledger.entity.Account;
import com.transactionledger.exception.AccountNotFoundException;
import com.transactionledger.logging.AppLogger;
import com.transactionledger.repository.AccountRepository;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Logger log = AppLogger.get(AccountService.class);

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Account name is required");
        }
        Account account = new Account(name);
        AccountResponse response = AccountResponse.from(accountRepository.save(account));
        log.info("Created account id={}", response.id());
        return response;
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId) {
        Account account = findAccount(accountId);
        return new BalanceResponse(account.getId(), account.getBalance());
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> {
                    log.info("Account not found id={}", accountId);
                    return new AccountNotFoundException(accountId);
                });
    }
}

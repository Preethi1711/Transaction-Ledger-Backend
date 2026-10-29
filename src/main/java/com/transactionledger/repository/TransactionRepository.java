package com.transactionledger.repository;

import com.transactionledger.entity.Transaction;
import com.transactionledger.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    boolean existsByAccount_IdAndReferenceId(Long accountId, String referenceId);

    long countByAccount_Id(Long accountId);

    Page<Transaction> findByAccount_Id(Long accountId, Pageable pageable);

    Page<Transaction> findByAccount_IdAndType(Long accountId, TransactionType type, Pageable pageable);
}

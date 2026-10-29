package com.transactionledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_transactions_account_reference",
                columnNames = {"account_id", "reference_id"}
        )
)
@Check(
        name = "ck_transactions_amount_and_type",
        constraints = "amount > 0 AND transaction_type IN ('CREDIT', 'DEBIT')"
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, updatable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(updatable = false, length = 255)
    private String description;

    @Column(name = "reference_id", nullable = false, updatable = false, length = 25)
    private String referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    public Transaction(Account account, TransactionType type, BigDecimal amount, String description, String referenceId) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.referenceId = referenceId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

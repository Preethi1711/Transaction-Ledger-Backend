package com.transactionledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transactionLedgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Transaction Ledger API")
                .version("1.0.0")
                .description("Create accounts, record immutable credits and debits, read balances and history, and transfer money between accounts."));
    }
}

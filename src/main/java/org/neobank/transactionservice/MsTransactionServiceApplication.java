package org.neobank.transactionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class MsTransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsTransactionServiceApplication.class, args);
    }

}

package com.corebank.digital_banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DigitalBankingApplication {

	public static void main(String[] args) {
		SpringApplication.run(DigitalBankingApplication.class, args);
	}

}

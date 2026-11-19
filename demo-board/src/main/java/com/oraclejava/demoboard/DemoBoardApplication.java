package com.oraclejava.demoboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DemoBoardApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoBoardApplication.class, args);
	}

}

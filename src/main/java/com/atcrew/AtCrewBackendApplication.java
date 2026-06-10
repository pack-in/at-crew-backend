package com.atcrew;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
public class AtCrewBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AtCrewBackendApplication.class, args);
	}

}

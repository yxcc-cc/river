package com.riverbank;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.riverbank.mapper")
@EnableScheduling
public class RiverbankSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(RiverbankSystemApplication.class, args);
	}

}

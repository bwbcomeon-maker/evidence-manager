package com.bwbcomeon.evidence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目交付证据管理系统 - 主应用类
 * 
 * @author system
 */
@SpringBootApplication
@MapperScan("com.bwbcomeon.evidence.mapper")
public class EvidenceManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(EvidenceManagerApplication.class, args);
	}

}

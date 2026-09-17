package com.example.crudpersona;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CrudPersonaSpringApplicationTests {

	@TempDir
	static Path directorio;

	@DynamicPropertySource
	static void baseDePrueba(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + directorio.resolve("contexto.db"));
	}

	@Test
	void contextLoads() {
	}

}

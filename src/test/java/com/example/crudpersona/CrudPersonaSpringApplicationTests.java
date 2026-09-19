package com.example.crudpersona;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.example.crudpersona.support.PostgresTestDatabase;
import java.nio.file.Path;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CrudPersonaSpringApplicationTests {

	@RegisterExtension
	static PostgresTestDatabase database = new PostgresTestDatabase();

	@DynamicPropertySource
	static void baseDePrueba(DynamicPropertyRegistry registry) {
		database.configure(registry);
	}

	@Test
	void contextLoads() {
	}

}

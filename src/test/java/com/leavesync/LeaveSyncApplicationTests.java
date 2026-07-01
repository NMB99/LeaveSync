package com.leavesync;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires running PostgreSQL, Redis, mail server - replace with Testcontainers post-deployment")
class LeaveSyncApplicationTests {

	@Test
	void contextLoads() {
	}

}

package com.commonground.be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import com.commonground.be.BeApplication;

@SpringBootTest
@ContextConfiguration(classes = BeApplication.class)
@org.springframework.test.context.ActiveProfiles("test")
class BeApplicationTests {

	//@Test
	void contextLoads() {
	}

}

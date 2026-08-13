package com.atcrew;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.atcrew.support.DatabaseCleanupExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

@SpringBootTest
@ExtendWith(DatabaseCleanupExtension.class)
@ImportTestcontainers(SharedContainersConfig.class)
class AtCrewBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}

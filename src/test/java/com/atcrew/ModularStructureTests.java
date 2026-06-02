package com.atcrew;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularStructureTests {

    @Test
    void 모듈_구조_검증() {
        ApplicationModules.of(AtCrewBackendApplication.class).verify();
    }
}

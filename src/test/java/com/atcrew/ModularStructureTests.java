package com.atcrew;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularStructureTests {

    private static final ApplicationModules modules =
            ApplicationModules.of(AtCrewBackendApplication.class);

    @Test
    void 모듈_구조_검증() {
        modules.verify();
    }

    @Test
    void 모듈_다이어그램_생성() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}

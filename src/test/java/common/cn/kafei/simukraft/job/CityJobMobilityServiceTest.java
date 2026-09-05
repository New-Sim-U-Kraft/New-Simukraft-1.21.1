package common.cn.kafei.simukraft.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CityJobMobilityServiceTest {
    @Test
    void resolveHireRole_mapsPublicBuildingStaff() {
        assertEquals(CityJobType.DOCTOR, CityJobMobilityService.resolveHireRole("doctor"));
        assertEquals(CityJobType.BANKER, CityJobMobilityService.resolveHireRole("teller"));
        assertEquals(CityJobType.BANKER, CityJobMobilityService.resolveHireRole("banker"));
        assertEquals(CityJobType.BROKER, CityJobMobilityService.resolveHireRole("broker"));
    }
}

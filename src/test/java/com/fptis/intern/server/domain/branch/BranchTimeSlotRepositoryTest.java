package com.fptis.intern.server.domain.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fptis.intern.server.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * @DataJpaTest + 실 MySQL 컨테이너로 Flyway V8 마이그레이션이 BranchTimeSlot 엔티티와
 * 어긋나지 않는지, 그리고 ensureExists(멱등 upsert)가 discussions#13 "방안 A" 대로
 * 슬롯 행을 딱 하나만 만드는지 검증한다.
 */
@Testcontainers
@DataJpaTest
@EnableJpaAuditing
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BranchTimeSlotRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private BranchTimeSlotRepository branchTimeSlotRepository;

    @Test
    void ensureExistsCreatesRowOnceAndLockForUpdateReadsIt() {
        Branch branch = branchRepository.save(Branch.builder()
                .name("명동 환전센터")
                .address("서울 중구 명동길 1")
                .latitude(37.5665)
                .longitude(126.9780)
                .phone("02-123-4567")
                .businessHours("평일 09:00-18:00")
                .timeSlotCapacity(4)
                .build());
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime time = LocalTime.of(10, 30);

        branchTimeSlotRepository.ensureExists(branch.getId(), date, time, 4);
        branchTimeSlotRepository.ensureExists(branch.getId(), date, time, 4);

        assertThat(branchTimeSlotRepository.findAll()).hasSize(1);

        BranchTimeSlot slot = branchTimeSlotRepository.lockForUpdate(branch.getId(), date, time).orElseThrow();
        assertThat(slot.getRemaining()).isEqualTo(4);

        slot.decreaseRemaining();
        branchTimeSlotRepository.save(slot);

        BranchTimeSlot afterEnsureExistsAgain = branchTimeSlotRepository.lockForUpdate(branch.getId(), date, time).orElseThrow();
        assertThat(afterEnsureExistsAgain.getRemaining()).isEqualTo(3);

        branchTimeSlotRepository.ensureExists(branch.getId(), date, time, 4);
        BranchTimeSlot afterNoOpEnsureExists = branchTimeSlotRepository.lockForUpdate(branch.getId(), date, time).orElseThrow();
        assertThat(afterNoOpEnsureExists.getRemaining()).isEqualTo(3);
    }

    @Test
    void decreaseRemainingThrowsWhenSlotIsFull() {
        BranchTimeSlot slot = BranchTimeSlot.builder()
                .branchId(1L)
                .slotDate(LocalDate.now())
                .slotTime(LocalTime.of(10, 0))
                .remaining(0)
                .build();

        assertThatThrownBy(slot::decreaseRemaining)
                .isInstanceOf(BusinessException.class);
    }
}

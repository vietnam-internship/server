package com.fptis.intern.server.domain.branch;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchCurrencyRateRepository extends JpaRepository<BranchCurrencyRate, Long> {

    @Query("select r from BranchCurrencyRate r where r.branchId = :branchId and r.currencyCode = :currencyCode")
    Optional<BranchCurrencyRate> findRate(@Param("branchId") Long branchId, @Param("currencyCode") String currencyCode);

    @Query("select r from BranchCurrencyRate r where r.branchId = :branchId")
    List<BranchCurrencyRate> findRatesByBranch(@Param("branchId") Long branchId);

    @Query("select r from BranchCurrencyRate r where r.branchId in :branchIds")
    List<BranchCurrencyRate> findRatesByBranches(@Param("branchIds") Collection<Long> branchIds);

    /**
     * 예약 생성/취소/만료 시 재고를 증감하기 전 반드시 이 메서드로 조회해 행 락을 잡는다 —
     * 동시 예약이 재고를 동시에 읽고 각자 차감해 초과 판매되는 것을 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BranchCurrencyRate r where r.branchId = :branchId and r.currencyCode = :currencyCode")
    Optional<BranchCurrencyRate> findForUpdate(@Param("branchId") Long branchId, @Param("currencyCode") String currencyCode);
}

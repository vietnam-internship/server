package com.fptis.intern.server.domain.branch;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchCurrencyRateRepository extends JpaRepository<BranchCurrencyRate, Long> {

    Optional<BranchCurrencyRate> findByBranchIdAndCurrencyCode(Long branchId, String currencyCode);

    List<BranchCurrencyRate> findByBranchId(Long branchId);

    List<BranchCurrencyRate> findByBranchIdIn(Collection<Long> branchIds);
}

package com.fptis.intern.server.application.branch;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.currency.Currency;
import com.fptis.intern.server.domain.currency.CurrencyRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.branch.dto.BranchInventoryBulkUpdateRequest;
import com.fptis.intern.server.presentation.branch.dto.BranchInventoryItem;
import com.fptis.intern.server.presentation.branch.dto.BranchInventoryResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchRateAdminItem;
import com.fptis.intern.server.presentation.branch.dto.BranchRateAdminResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchRateBulkUpdateRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchAdminService {

    private final BranchCurrencyRateRepository branchCurrencyRateRepository;
    private final CurrencyRepository currencyRepository;
    private final BranchRepository branchRepository;

    public List<BranchRateAdminResponse> getRates(Long branchId) {
        getBranchOrThrow(branchId);
        List<BranchCurrencyRate> rates = branchCurrencyRateRepository.findRatesByBranch(branchId);
        Map<String, Currency> currencyByCode = currencyRepository
                .findByCodeIn(rates.stream().map(BranchCurrencyRate::getCurrencyCode).distinct().toList())
                .stream().collect(Collectors.toMap(Currency::getCode, Function.identity()));

        return rates.stream()
                .map(rate -> {
                    Currency currency = currencyByCode.get(rate.getCurrencyCode());
                    return new BranchRateAdminResponse(rate.getCurrencyCode(),
                            currency != null ? currency.getBuyRate() : null,
                            currency != null ? currency.getSellRate() : null,
                            rate.getPreferentialRate());
                })
                .toList();
    }

    @Transactional
    public List<BranchRateAdminResponse> updateRates(Long branchId, BranchRateBulkUpdateRequest request) {
        getBranchOrThrow(branchId);
        for (BranchRateAdminItem item : request.rates()) {
            BranchCurrencyRate rate = findOrInitRate(branchId, item.currencyCode());
            rate.update(item.feePercent(), null);
            branchCurrencyRateRepository.save(rate);
        }
        return getRates(branchId);
    }

    public List<BranchInventoryResponse> getInventory(Long branchId) {
        getBranchOrThrow(branchId);
        return branchCurrencyRateRepository.findRatesByBranch(branchId).stream()
                .map(rate -> new BranchInventoryResponse(rate.getCurrencyCode(), rate.getReservationOnlyStock(), false))
                .toList();
    }

    @Transactional
    public List<BranchInventoryResponse> updateInventory(Long branchId, BranchInventoryBulkUpdateRequest request) {
        getBranchOrThrow(branchId);
        for (BranchInventoryItem item : request.items()) {
            BranchCurrencyRate rate = findOrInitRate(branchId, item.currencyCode());
            rate.update(null, item.stock());
            branchCurrencyRateRepository.save(rate);
        }
        return getInventory(branchId);
    }

    private BranchCurrencyRate findOrInitRate(Long branchId, String currencyCode) {
        return branchCurrencyRateRepository.findRate(branchId, currencyCode)
                .orElseGet(() -> BranchCurrencyRate.builder()
                        .branchId(branchId).currencyCode(currencyCode)
                        .preferentialRate(0).reservationOnlyStock(0).build());
    }

    private Branch getBranchOrThrow(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BRANCH_NOT_FOUND));
    }
}

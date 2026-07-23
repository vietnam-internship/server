package com.fptis.intern.server.presentation.currency;

import com.fptis.intern.server.application.currency.CurrencyService;
import com.fptis.intern.server.global.annotation.PublicApi;
import com.fptis.intern.server.global.exception.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;

    @PublicApi
    @GetMapping
    public ApiResponse<?> getCurrencies(@RequestParam(required = false) String q) {
        return ApiResponse.success(currencyService.getCurrencies(q));
    }

    @PublicApi
    @GetMapping("/{code}")
    public ApiResponse<?> getCurrencyDetail(@PathVariable String code) {
        return ApiResponse.success(currencyService.getCurrencyDetail(code));
    }

    @PublicApi
    @GetMapping("/{code}/history")
    public ApiResponse<?> getCurrencyHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(currencyService.getCurrencyHistory(code, days));
    }

    @PublicApi
    @GetMapping("/{code}/recommendation")
    public ApiResponse<?> getTimingRecommendation(@PathVariable String code) {
        return ApiResponse.success(currencyService.getTimingRecommendation(code));
    }
}

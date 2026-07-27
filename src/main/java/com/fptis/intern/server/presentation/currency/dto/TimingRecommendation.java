package com.fptis.intern.server.presentation.currency.dto;

public record TimingRecommendation(
        String currencyCode,
        String signal,
        double currentRate,
        Double predictedRate,
        boolean highVolatility,
        String disclaimer
) {
    public static TimingRecommendation of(String currencyCode, String signal, double currentRate, Double predictedRate, boolean highVolatility) {
        String disclaimer = "본 정보는 과거 데이터를 기반으로 한 AI 예측 결과이며, 실제 환율 변동과 다를 수 있습니다. 투자 판단의 최종 책임은 사용자에게 있습니다.";
        return new TimingRecommendation(
                currencyCode, 
                signal, 
                currentRate, 
                predictedRate, 
                highVolatility, 
                disclaimer
        );
    }
}

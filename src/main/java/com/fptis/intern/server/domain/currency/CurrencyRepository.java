package com.fptis.intern.server.domain.currency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    Optional<Currency> findByCode(String code);

    @Query("SELECT c FROM Currency c WHERE LOWER(c.code) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(c.country) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Currency> searchByCodeOrCountry(@Param("q") String q);
}

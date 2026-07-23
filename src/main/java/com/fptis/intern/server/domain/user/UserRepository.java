package com.fptis.intern.server.domain.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    /**
     * 노쇼 이력이 있는 유저의 "활성 예약 수 확인 → 생성" 구간에서 동시 요청이 카운트를
     * 동시에 0으로 읽고 둘 다 통과하는 것을 막기 위해, 카운트 확인 전 이 메서드로 유저 행 락을 잡는다.
     * 락 순서는 항상 유저 → 재고/슬롯(findForUpdate/lockForUpdate)이어야 데드락을 피할 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findForUpdate(@Param("userId") Long userId);
}

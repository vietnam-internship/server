package com.fptis.intern.server.domain.branch;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    @Query("select b from Branch b where b.active = true")
    List<Branch> findActiveBranches();
}

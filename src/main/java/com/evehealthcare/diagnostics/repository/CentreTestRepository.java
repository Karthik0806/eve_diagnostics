package com.evehealthcare.diagnostics.repository;

import com.evehealthcare.diagnostics.domain.CentreTest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CentreTestRepository extends JpaRepository<CentreTest, Long> {

    @EntityGraph(attributePaths = {"centre", "test"})
    Optional<CentreTest> findByCentreIdAndTestId(Long centreId, Long testId);

    @EntityGraph(attributePaths = {"test"})
    List<CentreTest> findByCentreIdIn(Collection<Long> centreIds);

    @EntityGraph(attributePaths = {"test"})
    List<CentreTest> findByCentreId(Long centreId);
}

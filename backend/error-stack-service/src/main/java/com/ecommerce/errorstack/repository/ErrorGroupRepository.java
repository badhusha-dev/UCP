package com.ecommerce.errorstack.repository;

import com.ecommerce.errorstack.model.ErrorGroup;
import com.ecommerce.errorstack.model.ErrorSeverity;
import com.ecommerce.errorstack.model.ErrorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ErrorGroupRepository extends JpaRepository<ErrorGroup, Long>, JpaSpecificationExecutor<ErrorGroup> {

    Optional<ErrorGroup> findByFingerprint(String fingerprint);

    @Query("SELECT g FROM ErrorGroup g WHERE " +
           "(:serviceName IS NULL OR LOWER(g.serviceName) = LOWER(:serviceName)) AND " +
           "(:environment IS NULL OR LOWER(g.environment) = LOWER(:environment)) AND " +
           "(:severity IS NULL OR g.severity = :severity) AND " +
           "(:status IS NULL OR g.status = :status) AND " +
           "(:fingerprint IS NULL OR g.fingerprint = :fingerprint) AND " +
           "(:startDate IS NULL OR g.lastSeen >= :startDate) AND " +
           "(:endDate IS NULL OR g.lastSeen <= :endDate) AND " +
           "(:search IS NULL OR LOWER(g.message) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(g.errorType) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(g.serviceName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ErrorGroup> findFiltered(
            @Param("serviceName") String serviceName,
            @Param("environment") String environment,
            @Param("severity") ErrorSeverity severity,
            @Param("status") ErrorStatus status,
            @Param("fingerprint") String fingerprint,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT g.serviceName, COUNT(g) FROM ErrorGroup g GROUP BY g.serviceName ORDER BY COUNT(g) DESC")
    List<Object[]> countByService();

    @Query("SELECT g.severity, COUNT(g) FROM ErrorGroup g GROUP BY g.severity")
    List<Object[]> countBySeverity();

    @Query("SELECT g.status, COUNT(g) FROM ErrorGroup g GROUP BY g.status")
    List<Object[]> countByStatus();

    @Query("SELECT COALESCE(SUM(g.occurrencesCount), 0) FROM ErrorGroup g")
    Long sumTotalOccurrences();

    @Query("SELECT COUNT(g) FROM ErrorGroup g WHERE g.status = 'NEW'")
    Long countNewErrors();

    @Query("SELECT COUNT(g) FROM ErrorGroup g WHERE g.severity = 'CRITICAL' AND g.status != 'RESOLVED' AND g.status != 'IGNORED'")
    Long countUnresolvedCritical();

    List<ErrorGroup> findTop10ByOrderByLastSeenDesc();
}

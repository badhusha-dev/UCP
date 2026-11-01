package com.ecommerce.errorstack.repository;

import com.ecommerce.errorstack.model.ErrorRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ErrorRecordRepository extends JpaRepository<ErrorRecord, Long>, JpaSpecificationExecutor<ErrorRecord> {

    Page<ErrorRecord> findByFingerprintOrderByCreatedAtDesc(String fingerprint, Pageable pageable);

    List<ErrorRecord> findTop20ByFingerprintOrderByCreatedAtDesc(String fingerprint);

    @Query("SELECT r FROM ErrorRecord r WHERE r.group.id = :groupId ORDER BY r.createdAt DESC")
    List<ErrorRecord> findRecentByGroupId(@Param("groupId") Long groupId, Pageable pageable);

    @Query("SELECT COUNT(r) FROM ErrorRecord r WHERE r.createdAt >= :since")
    Long countSince(@Param("since") OffsetDateTime since);

    // Hour bucket as "YYYY-MM-DD HH"; the service appends ":00". A literal ":00" inside the TO_CHAR pattern is
    // Postgres-only (H2 rejects it), which broke the overview test.
    @Query("SELECT to_char(r.createdAt, 'YYYY-MM-DD HH24'), COUNT(r) " +
           "FROM ErrorRecord r WHERE r.createdAt >= :since " +
           "GROUP BY to_char(r.createdAt, 'YYYY-MM-DD HH24') " +
           "ORDER BY to_char(r.createdAt, 'YYYY-MM-DD HH24') ASC")
    List<Object[]> getHourlyCountsSince(@Param("since") OffsetDateTime since);
}

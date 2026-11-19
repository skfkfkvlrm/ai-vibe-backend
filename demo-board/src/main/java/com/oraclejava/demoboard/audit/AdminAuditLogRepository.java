package com.oraclejava.demoboard.audit;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

	List<AdminAuditLog> findAllByOrderByAuditIdDesc(Pageable pageable);
}

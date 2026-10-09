package com.habeshlist.repository;

import com.habeshlist.entity.ScanAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ScanAuditRepository extends JpaRepository<ScanAudit, UUID> {}

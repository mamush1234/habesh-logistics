package com.habeshlist.repository;

import com.habeshlist.entity.SenderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface SenderProfileRepository extends JpaRepository<SenderProfile, UUID> {}

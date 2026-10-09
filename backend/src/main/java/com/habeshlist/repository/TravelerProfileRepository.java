package com.habeshlist.repository;

import com.habeshlist.entity.TravelerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface TravelerProfileRepository extends JpaRepository<TravelerProfile, UUID> {}

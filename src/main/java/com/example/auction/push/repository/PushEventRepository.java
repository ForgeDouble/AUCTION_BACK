package com.example.auction.push.repository;

import com.example.auction.push.domain.PushEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushEventRepository extends JpaRepository<PushEvent, Long> { }

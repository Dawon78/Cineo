package com.dw.movie.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);
}
package com.dw.movie.auth.repository;

import com.dw.movie.auth.entity.Member;

import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Member> findById(Long id);

    Optional<Member> findByIdForUpdate(Long id);
}
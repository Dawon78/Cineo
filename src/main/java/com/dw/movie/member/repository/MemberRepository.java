package com.dw.movie.member.repository;

import com.dw.movie.member.entity.Member;

import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Member> findById(Long id);

    Optional<Member> findByIdForUpdate(Long id);
}
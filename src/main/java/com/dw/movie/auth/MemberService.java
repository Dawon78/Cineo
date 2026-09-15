package com.dw.movie.auth;

import com.dw.movie.auth.dto.SignupRequest;
import com.dw.movie.common.exception.DuplicateEmailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("이미 가입된 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        Member member = new Member(
                request.getEmail(),
                encodedPassword,
                request.getName(),
                request.getPhone()
        );

        memberRepository.save(member);
    }
}
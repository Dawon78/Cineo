package com.dw.movie.member.service;

import com.dw.movie.common.exception.MemberNotFoundException;
import com.dw.movie.member.dto.MemberInfoResponse;
import com.dw.movie.member.entity.Member;
import com.dw.movie.member.repository.MemberRepository;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public MemberInfoResponse getMyInfo(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException("해당 회원을 찾을 수 없습니다."));
        return new MemberInfoResponse(member.getEmail(), member.getName(), member.getTier(),
                member.getTotalSpent(), member.getPoints());
    }
}

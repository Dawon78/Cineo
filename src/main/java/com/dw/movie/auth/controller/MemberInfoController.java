package com.dw.movie.auth.controller;

import com.dw.movie.auth.dto.MemberInfoResponse;
import com.dw.movie.auth.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberInfoController {

    private final MemberService memberService;

    public MemberInfoController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/me")
    public ResponseEntity<MemberInfoResponse> getMyInfo(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(memberService.getMyInfo(memberId));
    }
}

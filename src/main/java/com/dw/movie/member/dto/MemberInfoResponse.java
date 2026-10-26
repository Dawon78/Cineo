package com.dw.movie.member.dto;

import com.dw.movie.member.entity.Tier;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberInfoResponse {
    private String email;
    private String name;
    private Tier tier;
    private Long totalSpent;
    private Long points;
}

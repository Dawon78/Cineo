package com.dw.movie.auth.dto;

import lombok.Getter;

@Getter
public class RefreshRequest {
    private String refreshToken;
}
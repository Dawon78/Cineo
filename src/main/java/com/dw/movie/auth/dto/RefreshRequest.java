package com.dw.movie.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class RefreshRequest {
    @NotBlank
    private String refreshToken;
}

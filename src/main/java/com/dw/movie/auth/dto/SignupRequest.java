package com.dw.movie.auth.dto;

import lombok.Getter;

@Getter
public class SignupRequest {
    private String email;
    private String password;
    private String name;
    private String phone;
}

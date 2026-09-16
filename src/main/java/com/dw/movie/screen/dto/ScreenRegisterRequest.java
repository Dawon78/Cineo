package com.dw.movie.screen.dto;

import lombok.Getter;

@Getter
public class ScreenRegisterRequest {
    private String name;
    private int rowCount;
    private int colCount;
}

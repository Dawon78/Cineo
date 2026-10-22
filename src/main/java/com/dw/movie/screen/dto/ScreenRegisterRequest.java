package com.dw.movie.screen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ScreenRegisterRequest {
    @NotBlank
    @Size(max = 50)
    private String name;

    @Min(1)
    @Max(26)
    private int rowCount;

    @Min(1)
    @Max(50)
    private int colCount;
}

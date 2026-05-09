package com.smart.nl2sql.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SqlEditCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "SQL不能为空")
    private String sql;
}
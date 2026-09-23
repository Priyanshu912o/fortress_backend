package com.fortress.chat.dto.response;

import lombok.*;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ApiErrorResponse {
    private int statusCode;
    private String error;
    private String message;
}

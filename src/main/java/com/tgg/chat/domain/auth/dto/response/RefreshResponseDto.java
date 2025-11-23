package com.tgg.chat.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Schema(description = "토큰 재발급 응답 DTO")
public class RefreshResponseDto {

    @Schema(description = "엑세스 토큰")
    private final String accessToken;

    @Schema(description = "리프레시 토큰")
    private final String refreshToken;

    private RefreshResponseDto(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public static RefreshResponseDto of(String accessToken, String refreshToken) {
        return new RefreshResponseDto(accessToken, refreshToken);
    }

}

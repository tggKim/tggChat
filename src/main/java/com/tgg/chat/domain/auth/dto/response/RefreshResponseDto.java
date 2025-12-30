package com.tgg.chat.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Schema(description = "토큰 재발급 응답 DTO")
public class RefreshResponseDto {

    @Schema(description = "엑세스 토큰")
    private final String accessToken;

    private RefreshResponseDto(String accessToken) {
        this.accessToken = accessToken;
    }

    public static RefreshResponseDto of(String accessToken) {
        return new RefreshResponseDto(accessToken);
    }

}

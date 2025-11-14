package com.tgg.chat.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserUpdateRequestDto {

    @NotBlank(message = "사용자명은 필수입니다.")
    @Size(max = 50, message = "사용자명 길이는 50자 이하입니다.")
    private String username;

}

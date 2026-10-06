package com.tgg.chat.domain.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTagGeneratorTest {

    private final UserTagGenerator userTagGenerator = new UserTagGenerator();

    @Test
    @DisplayName("사용자 태그 생성 성공 - 허용 문자로 구성된 8자리 태그 반환")
    void generate_success() {
        // when
        String userTag = userTagGenerator.generate();

        // then
        assertThat(userTag)
                .hasSize(8)
                .matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
    }
}

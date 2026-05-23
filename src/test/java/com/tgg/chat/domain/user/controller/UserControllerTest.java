package com.tgg.chat.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.jwt.JwtSecurityFilter;
import com.tgg.chat.domain.user.dto.request.SignUpRequestDto;
import com.tgg.chat.domain.user.dto.response.SignUpResponseDto;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    UserService userService;

    @MockitoBean
    JwtSecurityFilter jwtSecurityFilter;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("회원가입 api 성공")
    void signup_api_success() throws Exception {
        // given
        Map<String, Object> requestMap = Map.of(
                "email", "test@test.com",
                "password", "testPassword",
                "username", "testUsername"
        );

        User savedUser = User.of("test@test.com", "encodedPassword", "testUsername");
        LocalDateTime testTime = LocalDateTime.of(2026, 5, 23, 12, 0, 0);
        ReflectionTestUtils.setField(savedUser, "userId", 1L);
        ReflectionTestUtils.setField(savedUser, "createdAt", testTime);
        ReflectionTestUtils.setField(savedUser, "updatedAt", testTime);
        SignUpResponseDto responseDto = SignUpResponseDto.of(savedUser);

        when(userService.signUpUser(any(SignUpRequestDto.class))).thenReturn(responseDto);

        // when & then
        mockMvc.perform(
                post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestMap)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.username").value("testUsername"))
                .andExpect(jsonPath("$.createdAt").value("2026-05-23 12:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-05-23 12:00:00"));

        ArgumentCaptor<SignUpRequestDto> argumentCaptor = ArgumentCaptor.forClass(SignUpRequestDto.class);
        verify(userService, times(1)).signUpUser(argumentCaptor.capture());

        SignUpRequestDto captorValue = argumentCaptor.getValue();
        assertThat(captorValue.getEmail()).isEqualTo("test@test.com");
        assertThat(captorValue.getPassword()).isEqualTo("testPassword");
        assertThat(captorValue.getUsername()).isEqualTo("testUsername");
    }
}
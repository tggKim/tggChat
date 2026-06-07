package com.tgg.chat.domain.friend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.jwt.JwtSecurityFilter;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.friend.dto.request.CreateFriendRequestDto;
import com.tgg.chat.domain.friend.dto.response.FriendListResponseDto;
import com.tgg.chat.domain.friend.service.UserFriendService;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(UserFriendController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserFriendControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    UserFriendService userFriendService;

    @MockitoBean
    JwtSecurityFilter jwtSecurityFilter;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("친구 추가 API 성공")
    void create_friend_api_success() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "username", "friendUsername"
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test@test.com", "testUsername");
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        // when & then
        try {
            mockMvc.perform(post("/friends")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk());

            ArgumentCaptor<CreateFriendRequestDto> argumentCaptor = ArgumentCaptor.forClass(CreateFriendRequestDto.class);
            verify(userFriendService, times(1)).createFriend(eq(1L), argumentCaptor.capture());
            CreateFriendRequestDto createFriendRequestDto = argumentCaptor.getValue();
            assertThat(createFriendRequestDto.getUsername()).isEqualTo("friendUsername");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("친구 추가 API 실패 - 사용자명 미입력")
    void create_friend_api_fail_empty_username() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "username", ""
        );

        // when & then
        mockMvc.perform(post("/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("사용자명은 필수입니다."));

        verify(userFriendService, never()).createFriend(anyLong(), any(CreateFriendRequestDto.class));
    }

    @Test
    @DisplayName("친구 추가 API 실패 - 사용자명 길이 초과")
    void create_friend_api_fail_username_too_long() throws Exception {
        // given
        String longUsername = "a".repeat(51);

        Map<String, Object> requestBody = Map.of(
                "username", longUsername
        );

        // when & then
        mockMvc.perform(post("/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("사용자명 길이는 50자 이하입니다."));

        verify(userFriendService, never()).createFriend(anyLong(), any(CreateFriendRequestDto.class));
    }

    @Test
    @DisplayName("친구 추가 API 실패 - 존재하지 않거나 삭제된 유저")
    void create_friend_api_fail_not_found_or_deleted_user() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "username", "friendUsername"
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test@test.com", "testUsername");
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        doThrow(new ErrorException(ErrorCode.USER_NOT_FOUND)).when(userFriendService).createFriend(eq(1L), any(CreateFriendRequestDto.class));

        // when & then
        try {
            mockMvc.perform(post("/friends")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("U003"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("존재하지 않는 유저입니다."));

            verify(userFriendService, times(1)).createFriend(eq(1L), any(CreateFriendRequestDto.class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("친구 추가 API 실패 - 이미 친구로 등록된 유저")
    void create_friend_api_fail_already_friend() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of(
                "username", "friendUsername"
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test@test.com", "testUsername");
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        doThrow(new ErrorException(ErrorCode.ALREADY_FRIEND)).when(userFriendService).createFriend(eq(1L), any(CreateFriendRequestDto.class));

        // when & then
        try {
            mockMvc.perform(post("/friends")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("F001"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("이미 친구로 등록되어 있습니다."));

            verify(userFriendService, times(1)).createFriend(eq(1L), any(CreateFriendRequestDto.class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("친구 목록 조회 API 성공")
    void find_friend_list_success() throws Exception {
        // given
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test@test.com", "testUsername");
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        FriendListResponseDto friendListResponseDto1 = FriendListResponseDto.of(1L, "friend1");
        FriendListResponseDto friendListResponseDto2 = FriendListResponseDto.of(2L, "friend2");
        when(userFriendService.findFriendListByOwnerId(1L)).thenReturn(List.of(friendListResponseDto1, friendListResponseDto2));

        // when & then
        try {
            mockMvc.perform(get("/friends"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].friendId").value(1))
                    .andExpect(jsonPath("$[0].friendUsername").value("friend1"))
                    .andExpect(jsonPath("$[1].friendId").value(2))
                    .andExpect(jsonPath("$[1].friendUsername").value("friend2"));

            verify(userFriendService, times(1)).findFriendListByOwnerId(1L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("친구 목록 조회 API 실패 - 존재하지 않거나 삭제된 유저")
    void find_friend_list_fail_not_found_or_deleted_user() throws Exception {
        // given
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "test@test.com", "testUsername");
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authenticatedUser, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        when(userFriendService.findFriendListByOwnerId(1L)).thenThrow(new ErrorException(ErrorCode.USER_NOT_FOUND));

        // when & then
        try {
            mockMvc.perform(get("/friends"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("U003"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("존재하지 않는 유저입니다."));

            verify(userFriendService, times(1)).findFriendListByOwnerId(1L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
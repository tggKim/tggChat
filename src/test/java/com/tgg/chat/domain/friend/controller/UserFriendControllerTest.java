package com.tgg.chat.domain.friend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.jwt.JwtSecurityFilter;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.friend.dto.request.CreateFriendRequestDto;
import com.tgg.chat.domain.friend.dto.response.FriendListResponseDto;
import com.tgg.chat.domain.friend.dto.response.SearchFriendResponseDto;
import com.tgg.chat.domain.friend.service.UserFriendService;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
    @DisplayName("친구 검색 API 성공 - 동명이인의 userId와 이메일 반환")
    void search_friends_api_success() throws Exception {
        // given
        String username = "김민재";

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        try {
            User user1 = User.of("minjae1@test.com", "password1", username);
            ReflectionTestUtils.setField(user1, "userId", 2L);
            ReflectionTestUtils.setField(user1, "profileImageKey", "profileImage1");

            User user2 = User.of("minjae2@test.com", "password2", username);
            ReflectionTestUtils.setField(user2, "userId", 3L);
            ReflectionTestUtils.setField(user2, "profileImageKey", "profileImage2");

            when(userFriendService.searchFriends(1L, username))
                    .thenReturn(List.of(
                            SearchFriendResponseDto.of(user1),
                            SearchFriendResponseDto.of(user2)
                    ));

            // when & then
            mockMvc.perform(get("/friends/search")
                            .param("username", username))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].userId").value(2))
                    .andExpect(jsonPath("$[0].username").value(username))
                    .andExpect(jsonPath("$[0].email").value("minjae1@test.com"))
                    .andExpect(jsonPath("$[0].profileImageKey").value("profileImage1"))
                    .andExpect(jsonPath("$[1].userId").value(3))
                    .andExpect(jsonPath("$[1].username").value(username))
                    .andExpect(jsonPath("$[1].email").value("minjae2@test.com"))
                    .andExpect(jsonPath("$[1].profileImageKey").value("profileImage2"));

            verify(userFriendService, times(1)).searchFriends(1L, username);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("친구 검색 API 성공 - 검색 결과가 없으면 빈 배열 반환")
    void search_friends_api_success_empty_result() throws Exception {
        // given
        String username = "김민재";

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        try {
            when(userFriendService.searchFriends(1L, username))
                    .thenReturn(List.of());

            // when & then
            mockMvc.perform(get("/friends/search")
                            .param("username", username))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(userFriendService, times(1)).searchFriends(1L, username);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @MethodSource("invalidSearchUsernames")
    @DisplayName("친구 검색 API 실패 - 검색어 누락, 공백 또는 길이 초과")
    void search_friends_api_fail_invalid_username(String username) throws Exception {
        // given
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        try {
            when(userFriendService.searchFriends(1L, username))
                    .thenThrow(new ErrorException(ErrorCode.INVALID_SEARCH_USERNAME));

            MockHttpServletRequestBuilder request = get("/friends/search");
            if (username != null) {
                request.param("username", username);
            }

            // when & then
            mockMvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("F003"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value("검색할 이름이 필요하며, 50자 이하여야 합니다."));

            verify(userFriendService, times(1)).searchFriends(1L, username);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    static Stream<String> invalidSearchUsernames() {
        return Stream.of(
                null,
                "",
                " ",
                "   ",
                "\t",
                "\n",
                "가".repeat(51)
        );
    }

    @Test
    @DisplayName("친구 검색 API 실패 - 존재하지 않거나 삭제된 로그인 유저")
    void search_friends_api_fail_not_found_or_deleted_login_user() throws Exception {
        // given
        String username = "김민재";

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        try {
            when(userFriendService.searchFriends(1L, username))
                    .thenThrow(new ErrorException(ErrorCode.USER_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/friends/search")
                            .param("username", username))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("U003"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message")
                            .value("존재하지 않는 유저입니다."));

            verify(userFriendService, times(1)).searchFriends(1L, username);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("친구 추가 API 성공 - 대상 userId를 서비스에 전달")
    void create_friend_api_success() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of("userId", 2L);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        try {
            // when & then
            mockMvc.perform(post("/friends")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(""));

            ArgumentCaptor<CreateFriendRequestDto> argumentCaptor =
                    ArgumentCaptor.forClass(CreateFriendRequestDto.class);

            verify(userFriendService, times(1))
                    .createFriend(eq(1L), argumentCaptor.capture());

            assertThat(argumentCaptor.getValue().getUserId()).isEqualTo(2L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"userId\":null}"
    })
    @DisplayName("친구 추가 API 실패 - userId 누락 또는 null")
    void create_friend_api_fail_missing_user_id(String requestBody) throws Exception {
        // when & then
        mockMvc.perform(post("/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("userId는 필수입니다."));

        verifyNoInteractions(userFriendService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("친구 추가 API 실패 - userId가 0 또는 음수")
    void create_friend_api_fail_non_positive_user_id(Long userId) throws Exception {
        // given
        Map<String, Object> requestBody = Map.of("userId", userId);

        // when & then
        mockMvc.perform(post("/friends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("C001"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("userId는 양수여야 합니다."));

        verifyNoInteractions(userFriendService);
    }

    @Test
    @DisplayName("친구 추가 API 실패 - 자기 자신을 친구로 추가")
    void create_friend_api_fail_self_friend() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of("userId", 1L);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        try {
            doThrow(new ErrorException(ErrorCode.SELF_FRIEND_NOT_ALLOWED))
                    .when(userFriendService)
                    .createFriend(eq(1L), any(CreateFriendRequestDto.class));

            // when & then
            mockMvc.perform(post("/friends")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("F002"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value("자기 자신을 친구로 추가할 수 없습니다."));

            verify(userFriendService, times(1))
                    .createFriend(eq(1L), any(CreateFriendRequestDto.class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("친구 추가 API 실패 - 존재하지 않거나 삭제된 유저")
    void create_friend_api_fail_not_found_or_deleted_user() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of("userId", 2L);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        try {
            doThrow(new ErrorException(ErrorCode.USER_NOT_FOUND))
                    .when(userFriendService)
                    .createFriend(eq(1L), any(CreateFriendRequestDto.class));

            // when & then
            mockMvc.perform(post("/friends")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("U003"))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message")
                            .value("존재하지 않는 유저입니다."));

            verify(userFriendService, times(1))
                    .createFriend(eq(1L), any(CreateFriendRequestDto.class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("친구 추가 API 실패 - 이미 친구로 등록된 유저")
    void create_friend_api_fail_already_friend() throws Exception {
        // given
        Map<String, Object> requestBody = Map.of("userId", 2L);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        try {
            doThrow(new ErrorException(ErrorCode.ALREADY_FRIEND))
                    .when(userFriendService)
                    .createFriend(eq(1L), any(CreateFriendRequestDto.class));

            // when & then
            mockMvc.perform(post("/friends")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("F001"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message")
                            .value("이미 친구로 등록되어 있습니다."));

            verify(userFriendService, times(1))
                    .createFriend(eq(1L), any(CreateFriendRequestDto.class));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("친구 목록 조회 API 성공 - 이메일 포함")
    void find_friend_list_success() throws Exception {
        // given
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );

        FriendListResponseDto friendListResponseDto1 = FriendListResponseDto.of(
                2L,
                "email1@test.com",
                "friend1",
                "profileImage1"
        );

        FriendListResponseDto friendListResponseDto2 = FriendListResponseDto.of(
                3L,
                "email2@test.com",
                "friend2",
                "profileImage2"
        );

        when(userFriendService.findFriendListByOwnerId(1L))
                .thenReturn(List.of(friendListResponseDto1, friendListResponseDto2));

        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        // when & then
        try {
            mockMvc.perform(get("/friends"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].friendId").value(2))
                    .andExpect(jsonPath("$[0].email").value("email1@test.com"))
                    .andExpect(jsonPath("$[0].friendUsername").value("friend1"))
                    .andExpect(jsonPath("$[0].profileImageKey").value("profileImage1"))
                    .andExpect(jsonPath("$[1].friendId").value(3))
                    .andExpect(jsonPath("$[1].email").value("email2@test.com"))
                    .andExpect(jsonPath("$[1].friendUsername").value("friend2"))
                    .andExpect(jsonPath("$[1].profileImageKey").value("profileImage2"));

            verify(userFriendService, times(1)).findFriendListByOwnerId(1L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("친구 목록 조회 API 실패 - 존재하지 않거나 삭제된 유저")
    void find_friend_list_fail_not_found_or_deleted_user() throws Exception {
        // given
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
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
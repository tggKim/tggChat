package com.tgg.chat.domain.friend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgg.chat.common.security.jwt.JwtSecurityFilter;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.friend.dto.request.CreateFriendRequestDto;
import com.tgg.chat.domain.friend.service.UserFriendService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
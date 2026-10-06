package com.tgg.chat.domain.chat.controller;

import com.tgg.chat.common.messaging.redis.RedisPublisher;
import com.tgg.chat.common.security.jwt.JwtSecurityFilter;
import com.tgg.chat.common.security.principal.AuthenticatedUser;
import com.tgg.chat.domain.chat.dto.response.FindChatRoomMembersResponseDto;
import com.tgg.chat.domain.chat.dto.response.FindInvitableFriendsResponseDto;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import com.tgg.chat.domain.chat.service.ChatRoomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatRoomController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatRoomControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatRoomService chatRoomService;

    @MockitoBean
    RedisPublisher redisPublisher;

    @MockitoBean
    JwtSecurityFilter jwtSecurityFilter;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("초대 가능 친구 조회 API 성공 - 사용자 태그 포함")
    void find_invitable_friends_api_success_with_user_tag() throws Exception {
        // given
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        FindInvitableFriendsResponseDto responseDto = FindInvitableFriendsResponseDto.of(
                2L,
                "friend",
                "FRIEND23",
                "profileImage"
        );
        when(chatRoomService.findInvitableFriends(1L, 10L))
                .thenReturn(List.of(responseDto));

        // when & then
        try {
            mockMvc.perform(get("/chatRooms/10/invitableFriends"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].userId").value(2L))
                    .andExpect(jsonPath("$[0].username").value("friend"))
                    .andExpect(jsonPath("$[0].userTag").value("FRIEND23"))
                    .andExpect(jsonPath("$[0].profileImageKey").value("profileImage"));

            verify(chatRoomService, times(1)).findInvitableFriends(1L, 10L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("채팅방 참여자 조회 API 성공 - 사용자 태그 포함")
    void find_chat_room_members_api_success_with_user_tag() throws Exception {
        // given
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(1L, "sid");
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        Collections.emptyList()
                );
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        FindChatRoomMembersResponseDto responseDto = FindChatRoomMembersResponseDto.of(
                2L,
                "MEMBER23",
                "member",
                "profileImage",
                ChatRoomUserRole.MEMBER,
                true
        );
        when(chatRoomService.findChatRoomMembers(1L, 10L))
                .thenReturn(List.of(responseDto));

        // when & then
        try {
            mockMvc.perform(get("/chatRooms/10/members"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].userId").value(2L))
                    .andExpect(jsonPath("$[0].userTag").value("MEMBER23"))
                    .andExpect(jsonPath("$[0].username").value("member"))
                    .andExpect(jsonPath("$[0].profileImageKey").value("profileImage"))
                    .andExpect(jsonPath("$[0].chatRoomUserRole").value("MEMBER"))
                    .andExpect(jsonPath("$[0].canAddFriend").value(true));

            verify(chatRoomService, times(1)).findChatRoomMembers(1L, 10L);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

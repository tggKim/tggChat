package com.tgg.chat.domain.chat.service;

import com.tgg.chat.domain.chat.dto.response.FindChatRoomMembersResponseDto;
import com.tgg.chat.domain.chat.dto.response.FindInvitableFriendsResponseDto;
import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import com.tgg.chat.domain.chat.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.chat.repository.ChatMessageRepository;
import com.tgg.chat.domain.chat.repository.ChatRoomRepository;
import com.tgg.chat.domain.chat.repository.ChatRoomUserRepository;
import com.tgg.chat.domain.friend.repository.UserFriendRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    ChatRoomRepository chatRoomRepository;

    @Mock
    ChatRoomUserRepository chatRoomUserRepository;

    @Mock
    UserFriendRepository userFriendRepository;

    @Mock
    ChatMessageRepository chatMessageRepository;

    @InjectMocks
    ChatRoomService chatRoomService;

    @Test
    @DisplayName("초대 가능 친구 조회 성공 - 사용자 태그를 포함하고 이름순으로 반환")
    void find_invitable_friends_success_with_user_tag() {
        // given
        User owner = User.of("owner@test.com", "password", "owner", "OWNER234");
        ReflectionTestUtils.setField(owner, "userId", 1L);

        ChatRoom chatRoom = ChatRoom.of(ChatRoomType.GROUP);
        ReflectionTestUtils.setField(chatRoom, "chatRoomId", 10L);

        ChatRoomUser ownerMembership = ChatRoomUser.of(
                owner,
                chatRoom,
                ChatRoomUserRole.OWNER,
                ChatRoomUserStatus.ACTIVE
        );

        User friend2 = User.of("friend2@test.com", "password", "친구2", "FRIEND24");
        ReflectionTestUtils.setField(friend2, "userId", 3L);
        ReflectionTestUtils.setField(friend2, "profileImageKey", "profileImage2");

        User friend1 = User.of("friend1@test.com", "password", "친구1", "FRIEND23");
        ReflectionTestUtils.setField(friend1, "userId", 2L);
        ReflectionTestUtils.setField(friend1, "profileImageKey", "profileImage1");

        when(chatRoomUserRepository.findByChatRoomIdAndUserIdWithChatRoomAndUser(10L, 1L))
                .thenReturn(Optional.of(ownerMembership));
        when(userFriendRepository.findInvitableFriends(1L, 10L))
                .thenReturn(List.of(friend2, friend1));

        // when
        List<FindInvitableFriendsResponseDto> result =
                chatRoomService.findInvitableFriends(1L, 10L);

        // then
        assertThat(result)
                .extracting(
                        FindInvitableFriendsResponseDto::getUserId,
                        FindInvitableFriendsResponseDto::getUsername,
                        FindInvitableFriendsResponseDto::getUserTag,
                        FindInvitableFriendsResponseDto::getProfileImageKey
                )
                .containsExactly(
                        tuple(2L, "친구1", "FRIEND23", "profileImage1"),
                        tuple(3L, "친구2", "FRIEND24", "profileImage2")
                );

        verify(chatRoomUserRepository, times(1))
                .findByChatRoomIdAndUserIdWithChatRoomAndUser(10L, 1L);
        verify(userFriendRepository, times(1)).findInvitableFriends(1L, 10L);
    }

    @Test
    @DisplayName("채팅방 참여자 조회 성공 - 사용자 태그와 친구 추가 가능 여부를 반환")
    void find_chat_room_members_success_with_user_tag() {
        // given
        User owner = User.of("owner@test.com", "password", "나", "OWNER234");
        ReflectionTestUtils.setField(owner, "userId", 1L);

        User member = User.of("member@test.com", "password", "가나다", "MEMBER23");
        ReflectionTestUtils.setField(member, "userId", 2L);
        ReflectionTestUtils.setField(member, "profileImageKey", "profileImage");

        ChatRoom chatRoom = ChatRoom.of(ChatRoomType.GROUP);
        ReflectionTestUtils.setField(chatRoom, "chatRoomId", 10L);

        ChatRoomUser ownerMembership = ChatRoomUser.of(
                owner,
                chatRoom,
                ChatRoomUserRole.OWNER,
                ChatRoomUserStatus.ACTIVE
        );
        ChatRoomUser memberMembership = ChatRoomUser.of(
                member,
                chatRoom,
                ChatRoomUserRole.MEMBER,
                ChatRoomUserStatus.ACTIVE
        );

        when(chatRoomUserRepository.findByChatRoomIdAndUserIdWithChatRoomAndUser(10L, 1L))
                .thenReturn(Optional.of(ownerMembership));
        when(chatRoomUserRepository.findByChatRoomIdWithUser(10L))
                .thenReturn(List.of(ownerMembership, memberMembership));
        when(userFriendRepository.findActiveFriendsByIds(1L, List.of(2L, 1L)))
                .thenReturn(List.of());

        // when
        List<FindChatRoomMembersResponseDto> result =
                chatRoomService.findChatRoomMembers(1L, 10L);

        // then
        assertThat(result)
                .extracting(
                        FindChatRoomMembersResponseDto::getUserId,
                        FindChatRoomMembersResponseDto::getUserTag,
                        FindChatRoomMembersResponseDto::getUsername,
                        FindChatRoomMembersResponseDto::getProfileImageKey,
                        FindChatRoomMembersResponseDto::getChatRoomUserRole,
                        FindChatRoomMembersResponseDto::isCanAddFriend
                )
                .containsExactly(
                        tuple(2L, "MEMBER23", "가나다", "profileImage", ChatRoomUserRole.MEMBER, true),
                        tuple(1L, "OWNER234", "나", null, ChatRoomUserRole.OWNER, false)
                );

        verify(chatRoomUserRepository, times(1))
                .findByChatRoomIdAndUserIdWithChatRoomAndUser(10L, 1L);
        verify(chatRoomUserRepository, times(1)).findByChatRoomIdWithUser(10L);
        verify(userFriendRepository, times(1))
                .findActiveFriendsByIds(1L, List.of(2L, 1L));
    }
}

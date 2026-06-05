package com.tgg.chat.domain.friend.service;

import com.tgg.chat.domain.friend.dto.query.UserFriendRowDto;
import com.tgg.chat.domain.friend.dto.request.CreateFriendRequestDto;
import com.tgg.chat.domain.friend.dto.response.FriendListResponseDto;
import com.tgg.chat.domain.friend.entity.UserFriend;
import com.tgg.chat.domain.friend.repository.UserFriendMapper;
import com.tgg.chat.domain.friend.repository.UserFriendRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserFriendServiceTest {
    @Mock
    UserRepository userRepository;

    @Mock
    UserFriendRepository userFriendRepository;

    @Mock
    UserFriendMapper userFriendMapper;

    @InjectMocks
    UserFriendService userFriendService;

    @Test
    @DisplayName("친구 등록 성공")
    void create_friend_success() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "friendUsername");

        User owner = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(owner, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        User friend = User.of("friend@friend.com", "friendPassword", "friendUsername");
        ReflectionTestUtils.setField(friend, "userId", 2L);
        when(userRepository.findByUsername(requestDto.getUsername())).thenReturn(Optional.of(friend));

        when(userFriendRepository.existsByOwner_UserIdAndFriend_UserId(owner.getUserId(), friend.getUserId())).thenReturn(false);

        // when
        userFriendService.createFriend(1L, requestDto);

        // then
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findByUsername(requestDto.getUsername());
        verify(userFriendRepository, times(1)).existsByOwner_UserIdAndFriend_UserId(owner.getUserId(), friend.getUserId());

        ArgumentCaptor<UserFriend> argumentCaptor = ArgumentCaptor.forClass(UserFriend.class);
        verify(userFriendRepository, times(1)).save(argumentCaptor.capture());
        UserFriend userFriend = argumentCaptor.getValue();

        assertThat(userFriend.getOwner().getUserId()).isEqualTo(owner.getUserId());
        assertThat(userFriend.getFriend().getUserId()).isEqualTo(friend.getUserId());
    }

    @Test
    @DisplayName("친구 등록 실패 - 존재하지 않는 로그인 유저")
    void create_friend_fail_not_found_owner() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "friendUsername");

        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never()).findByUsername(anyString());
        verify(userFriendRepository, never()).existsByOwner_UserIdAndFriend_UserId(anyLong(), anyLong());
        verify(userFriendRepository, never()).save(any(UserFriend.class));
    }

    @Test
    @DisplayName("친구 등록 실패 - 삭제된 로그인 유저")
    void create_friend_fail_deleted_owner() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "friendUsername");

        User owner = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(owner, "deleted", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never()).findByUsername(anyString());
        verify(userFriendRepository, never()).existsByOwner_UserIdAndFriend_UserId(anyLong(), anyLong());
        verify(userFriendRepository, never()).save(any(UserFriend.class));
    }

    @Test
    @DisplayName("친구 등록 실패 - 존재하지 않는 친구")
    void create_friend_fail_not_found_friend() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "friendUsername");

        User owner = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        when(userRepository.findByUsername(requestDto.getUsername())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findByUsername(requestDto.getUsername());
        verify(userFriendRepository, never()).existsByOwner_UserIdAndFriend_UserId(anyLong(), anyLong());
        verify(userFriendRepository, never()).save(any(UserFriend.class));
    }

    @Test
    @DisplayName("친구 등록 실패 - 삭제된 친구")
    void create_friend_fail_deleted_friend() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "friendUsername");

        User owner = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        User friend = User.of("friend@friend.com", "friendPassword", "friendUsername");
        ReflectionTestUtils.setField(friend, "deleted", true);
        when(userRepository.findByUsername(requestDto.getUsername())).thenReturn(Optional.of(friend));

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findByUsername(requestDto.getUsername());
        verify(userFriendRepository, never()).existsByOwner_UserIdAndFriend_UserId(anyLong(), anyLong());
        verify(userFriendRepository, never()).save(any(UserFriend.class));
    }

    @Test
    @DisplayName("친구 등록 실패 - 자기 자신을 친구로 등록")
    void create_friend_fail_self_friend_not_allowed() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "friendUsername");

        User owner = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(owner, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        User friend = User.of("friend@friend.com", "friendPassword", "friendUsername");
        ReflectionTestUtils.setField(friend, "userId", 1L);
        when(userRepository.findByUsername(requestDto.getUsername())).thenReturn(Optional.of(friend));

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.SELF_FRIEND_NOT_ALLOWED);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findByUsername(requestDto.getUsername());
        verify(userFriendRepository, never()).existsByOwner_UserIdAndFriend_UserId(anyLong(), anyLong());
        verify(userFriendRepository, never()).save(any(UserFriend.class));
    }

    @Test
    @DisplayName("친구 등록 실패 - 이미 등록된 친구")
    void create_friend_fail_already_friend() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "username", "friendUsername");

        User owner = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(owner, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        User friend = User.of("friend@friend.com", "friendPassword", "friendUsername");
        ReflectionTestUtils.setField(friend, "userId", 2L);
        when(userRepository.findByUsername(requestDto.getUsername())).thenReturn(Optional.of(friend));

        when(userFriendRepository.existsByOwner_UserIdAndFriend_UserId(owner.getUserId(), friend.getUserId())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_FRIEND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findByUsername(requestDto.getUsername());
        verify(userFriendRepository, times(1)).existsByOwner_UserIdAndFriend_UserId(owner.getUserId(), friend.getUserId());
        verify(userFriendRepository, never()).save(any(UserFriend.class));
    }

    @Test
    @DisplayName("친구 목록조회 성공")
    void find_friend_list_success() {
        // given
        User findUser = User.of("test@test.com", "testPassword", "testUsername");
        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        UserFriendRowDto friend1 = new UserFriendRowDto();
        ReflectionTestUtils.setField(friend1, "friendId", 1L);
        ReflectionTestUtils.setField(friend1, "friendUsername", "friend1");
        UserFriendRowDto friend2 = new UserFriendRowDto();
        ReflectionTestUtils.setField(friend2, "friendId", 2L);
        ReflectionTestUtils.setField(friend2, "friendUsername", "friend2");
        when(userFriendMapper.findFriendListByOwnerId(1L)).thenReturn(List.of(friend1, friend2));

        // when
        List<FriendListResponseDto> result = userFriendService.findFriendListByOwnerId(1L);

        // then
        assertThat(result).hasSize(2)
                .extracting(FriendListResponseDto::getFriendId, FriendListResponseDto::getFriendUsername)
                .containsExactly(
                        tuple(1L, "friend1"),
                        tuple(2L, "friend2")
                );

        verify(userRepository, times(1)).findById(1L);
        verify(userFriendMapper, times(1)).findFriendListByOwnerId(1L);
    }
}
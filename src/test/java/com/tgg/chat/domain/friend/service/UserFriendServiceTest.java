package com.tgg.chat.domain.friend.service;

import com.tgg.chat.domain.friend.dto.request.CreateFriendRequestDto;
import com.tgg.chat.domain.friend.dto.response.FriendListResponseDto;
import com.tgg.chat.domain.friend.dto.response.SearchFriendResponseDto;
import com.tgg.chat.domain.friend.entity.UserFriend;
import com.tgg.chat.domain.friend.repository.UserFriendRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @InjectMocks
    UserFriendService userFriendService;

    @Test
    @DisplayName("친구 등록 성공 - 이름이 같아도 userId가 다른 유저는 등록 가능")
    void create_friend_success() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "userId", 2L);

        User owner = User.of("owner@owner.com", "ownerPassword", "sameUsername");
        ReflectionTestUtils.setField(owner, "userId", 1L);

        User friend = User.of("friend@friend.com", "friendPassword", "sameUsername");
        ReflectionTestUtils.setField(friend, "userId", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(userFriendRepository.existsByOwner_UserIdAndFriend_UserId(1L, 2L))
                .thenReturn(false);

        // when
        userFriendService.createFriend(1L, requestDto);

        // then
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findById(2L);
        verify(userFriendRepository, times(1))
                .existsByOwner_UserIdAndFriend_UserId(1L, 2L);

        ArgumentCaptor<UserFriend> argumentCaptor =
                ArgumentCaptor.forClass(UserFriend.class);
        verify(userFriendRepository, times(1)).save(argumentCaptor.capture());

        UserFriend userFriend = argumentCaptor.getValue();
        assertThat(userFriend.getOwner()).isSameAs(owner);
        assertThat(userFriend.getFriend()).isSameAs(friend);
    }

    @Test
    @DisplayName("친구 등록 실패 - 존재하지 않는 로그인 유저")
    void create_friend_fail_not_found_owner() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "userId", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never()).findById(2L);
        verifyNoInteractions(userFriendRepository);
    }

    @Test
    @DisplayName("친구 등록 실패 - 삭제된 로그인 유저")
    void create_friend_fail_deleted_owner() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "userId", 2L);

        User owner = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(owner, "userId", 1L);
        ReflectionTestUtils.setField(owner, "deleted", true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never()).findById(2L);
        verifyNoInteractions(userFriendRepository);
    }

    @Test
    @DisplayName("친구 등록 실패 - 존재하지 않는 친구")
    void create_friend_fail_not_found_friend() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "userId", 2L);

        User owner = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(owner, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findById(2L);
        verifyNoInteractions(userFriendRepository);
    }

    @Test
    @DisplayName("친구 등록 실패 - 삭제된 친구")
    void create_friend_fail_deleted_friend() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "userId", 2L);

        User owner = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(owner, "userId", 1L);

        User friend = User.of("friend@friend.com", "friendPassword", "friendUsername");
        ReflectionTestUtils.setField(friend, "userId", 2L);
        ReflectionTestUtils.setField(friend, "deleted", true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findById(2L);
        verifyNoInteractions(userFriendRepository);
    }

    @Test
    @DisplayName("친구 등록 실패 - 자기 자신을 친구로 등록")
    void create_friend_fail_self_friend_not_allowed() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "userId", 1L);

        User owner = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(owner, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SELF_FRIEND_NOT_ALLOWED);

        // 로그인 유저 조회와 대상 유저 조회에서 같은 ID를 각각 조회한다.
        verify(userRepository, times(2)).findById(1L);
        verifyNoInteractions(userFriendRepository);
    }

    @Test
    @DisplayName("친구 등록 실패 - 이미 등록된 친구")
    void create_friend_fail_already_friend() {
        // given
        CreateFriendRequestDto requestDto = new CreateFriendRequestDto();
        ReflectionTestUtils.setField(requestDto, "userId", 2L);

        User owner = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(owner, "userId", 1L);

        User friend = User.of("friend@friend.com", "friendPassword", "friendUsername");
        ReflectionTestUtils.setField(friend, "userId", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(userFriendRepository.existsByOwner_UserIdAndFriend_UserId(1L, 2L))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userFriendService.createFriend(1L, requestDto))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_FRIEND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findById(2L);
        verify(userFriendRepository, times(1))
                .existsByOwner_UserIdAndFriend_UserId(1L, 2L);
        verify(userFriendRepository, never()).save(any(UserFriend.class));
    }

    @Test
    @DisplayName("친구 검색 성공 - 동명이인 목록과 식별 정보를 반환")
    void search_friends_success() {
        // given
        String username = "김민재";

        User loginUser = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(loginUser, "userId", 1L);

        User user1 = User.of("minjae1@test.com", "password1", username);
        ReflectionTestUtils.setField(user1, "userId", 2L);
        ReflectionTestUtils.setField(user1, "profileImageKey", "profileImage1");

        User user2 = User.of("minjae2@test.com", "password2", username);
        ReflectionTestUtils.setField(user2, "userId", 3L);
        // user2는 프로필 이미지가 없는 상태

        when(userRepository.findById(1L)).thenReturn(Optional.of(loginUser));
        when(userRepository.findFriendCandidatesByUsername(1L, username))
                .thenReturn(List.of(user1, user2));

        // when
        List<SearchFriendResponseDto> result =
                userFriendService.searchFriends(1L, username);

        // then
        assertThat(result)
                .hasSize(2)
                .extracting(
                        SearchFriendResponseDto::getUserId,
                        SearchFriendResponseDto::getUsername,
                        SearchFriendResponseDto::getEmail,
                        SearchFriendResponseDto::getProfileImageKey
                )
                .containsExactly(
                        tuple(2L, username, "minjae1@test.com", "profileImage1"),
                        tuple(3L, username, "minjae2@test.com", null)
                );

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1))
                .findFriendCandidatesByUsername(1L, username);
        verifyNoInteractions(userFriendRepository);
    }

    @Test
    @DisplayName("친구 검색 성공 - 검색 결과가 없으면 빈 목록 반환")
    void search_friends_success_empty_result() {
        // given
        String username = "김민재";

        User loginUser = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(loginUser, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(loginUser));
        when(userRepository.findFriendCandidatesByUsername(1L, username))
                .thenReturn(List.of());

        // when
        List<SearchFriendResponseDto> result =
                userFriendService.searchFriends(1L, username);

        // then
        assertThat(result).isEmpty();

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1))
                .findFriendCandidatesByUsername(1L, username);
        verifyNoInteractions(userFriendRepository);
    }

    @Test
    @DisplayName("친구 검색 성공 - 검색어가 정확히 50자이면 조회 가능")
    void search_friends_success_username_length_50() {
        // given
        String username = "가".repeat(50);

        User loginUser = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(loginUser, "userId", 1L);

        User candidate = User.of("candidate@test.com", "password", username);
        ReflectionTestUtils.setField(candidate, "userId", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(loginUser));
        when(userRepository.findFriendCandidatesByUsername(1L, username))
                .thenReturn(List.of(candidate));

        // when
        List<SearchFriendResponseDto> result =
                userFriendService.searchFriends(1L, username);

        // then
        assertThat(result)
                .hasSize(1)
                .extracting(SearchFriendResponseDto::getUsername)
                .containsExactly(username);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1))
                .findFriendCandidatesByUsername(1L, username);
        verifyNoInteractions(userFriendRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("친구 검색 실패 - 검색어가 null, 빈 문자열 또는 공백")
    void search_friends_fail_blank_username(String username) {
        // when & then
        assertThatThrownBy(() -> userFriendService.searchFriends(1L, username))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SEARCH_USERNAME);

        verifyNoInteractions(userRepository, userFriendRepository);
    }

    @Test
    @DisplayName("친구 검색 실패 - 검색어가 50자 초과")
    void search_friends_fail_username_too_long() {
        // given
        String username = "가".repeat(51);

        // when & then
        assertThatThrownBy(() -> userFriendService.searchFriends(1L, username))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SEARCH_USERNAME);

        verifyNoInteractions(userRepository, userFriendRepository);
    }

    @Test
    @DisplayName("친구 검색 실패 - 존재하지 않는 로그인 유저")
    void search_friends_fail_not_found_login_user() {
        // given
        String username = "김민재";

        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userFriendService.searchFriends(1L, username))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never())
                .findFriendCandidatesByUsername(anyLong(), anyString());
        verifyNoInteractions(userFriendRepository);
    }

    @Test
    @DisplayName("친구 검색 실패 - 삭제된 로그인 유저")
    void search_friends_fail_deleted_login_user() {
        // given
        String username = "김민재";

        User loginUser = User.of("owner@owner.com", "ownerPassword", "ownerUsername");
        ReflectionTestUtils.setField(loginUser, "userId", 1L);
        ReflectionTestUtils.setField(loginUser, "deleted", true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(loginUser));

        // when & then
        assertThatThrownBy(() -> userFriendService.searchFriends(1L, username))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, never())
                .findFriendCandidatesByUsername(anyLong(), anyString());
        verifyNoInteractions(userFriendRepository);
    }

    @Test
    @DisplayName("친구 목록조회 성공 - 이메일 반환 및 이름, userId 순 정렬")
    void find_friend_list_success() {
        // given
        User findUser = User.of("test@test.com", "testPassword", "testUsername");
        ReflectionTestUtils.setField(findUser, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        User friend1 = User.of("email1@test.com", "password1", "friend2");
        ReflectionTestUtils.setField(friend1, "userId", 2L);
        ReflectionTestUtils.setField(friend1, "profileImageKey", "profileImage1");

        User friend2 = User.of("email2@test.com", "password2", "friend1");
        ReflectionTestUtils.setField(friend2, "userId", 3L);
        ReflectionTestUtils.setField(friend2, "profileImageKey", "profileImage2");

        User friend3 = User.of("email3@test.com", "password3", "friend1");
        ReflectionTestUtils.setField(friend3, "userId", 4L);
        ReflectionTestUtils.setField(friend3, "profileImageKey", "profileImage3");

        // 정렬되지 않은 순서로 반환
        when(userFriendRepository.findActiveFriends(1L))
                .thenReturn(List.of(friend3, friend1, friend2));

        // when
        List<FriendListResponseDto> result =
                userFriendService.findFriendListByOwnerId(1L);

        // then
        assertThat(result)
                .hasSize(3)
                .extracting(
                        FriendListResponseDto::getFriendId,
                        FriendListResponseDto::getEmail,
                        FriendListResponseDto::getFriendUsername,
                        FriendListResponseDto::getProfileImageKey
                )
                .containsExactly(
                        tuple(3L, "email2@test.com", "friend1", "profileImage2"),
                        tuple(4L, "email3@test.com", "friend1", "profileImage3"),
                        tuple(2L, "email1@test.com", "friend2", "profileImage1")
                );

        verify(userRepository, times(1)).findById(1L);
        verify(userFriendRepository, times(1)).findActiveFriends(1L);
    }

    @Test
    @DisplayName("친구 목록조회 실패 - 존재하지 않는 로그인 유저")
    void find_friend_list_fail_not_found_login_user() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userFriendService.findFriendListByOwnerId(1L))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userFriendRepository, never()).findActiveFriends(anyLong());
    }

    @Test
    @DisplayName("친구 목록조회 실패 - 삭제된 로그인 유저")
    void find_friend_list_fail_deleted_login_user() {
        // given
        User findUser = User.of("test@test.com", "encoded-password", "testUsername");
        ReflectionTestUtils.setField(findUser, "deleted", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(findUser));

        // when & then
        assertThatThrownBy(() -> userFriendService.findFriendListByOwnerId(1L))
                .isInstanceOf(ErrorException.class)
                .extracting(ex -> ((ErrorException)ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, times(1)).findById(1L);
        verify(userFriendRepository, never()).findActiveFriends(anyLong());
    }
}
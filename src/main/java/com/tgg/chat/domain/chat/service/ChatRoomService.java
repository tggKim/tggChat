package com.tgg.chat.domain.chat.service;

import com.tgg.chat.common.messaging.event.ChatEvent;
import com.tgg.chat.common.messaging.event.ChatRoomListEvent;
import com.tgg.chat.domain.chat.dto.internal.*;
import com.tgg.chat.domain.chat.dto.request.CreateDirectChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.request.CreateGroupChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.request.InviteUserRequestDto;
import com.tgg.chat.domain.chat.dto.request.LeaveChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.response.*;
import com.tgg.chat.domain.chat.entity.ChatMessage;
import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.enums.ChatMessageType;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import com.tgg.chat.domain.chat.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.chat.repository.*;
import com.tgg.chat.domain.friend.repository.UserFriendMapper;
import com.tgg.chat.domain.friend.repository.UserFriendRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserMapper;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMapper chatRoomMapper;

    private final ChatRoomUserRepository chatRoomUserRepository;
    private final ChatRoomUserMapper chatRoomUserMapper;

    private final UserFriendRepository userFriendRepository;
    private final UserFriendMapper userFriendMapper;

    private final ChatMessageRepository chatMessageRepository;

    private final ChatRoomJoinLeaveService chatRoomJoinLeaveService;

    // 채팅방의 유저별 메시지 읽음 범위 조회
    @Transactional(readOnly = true)
    public List<ChatRoomReadStatusResponseDto> findReadStatuses(Long userId, Long chatRoomId) {
        // 유저가 채팅방에 속한 유저인지 검증
        ChatRoomUser findChatRoomUser = chatRoomUserRepository.findByChatRoomIdAndUserIdWithUser(chatRoomId, userId)
                .orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));

        // 요청한 유저가 채팅방에서 나간 상태면 예외
        if(findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        // User 추출 후 삭제된 유저인지 검증
        User user = findChatRoomUser.getUser();
        if(user.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        List<ChatRoomUser> chatRoomUsers = chatRoomUserRepository.findActiveChatRoomUsers(chatRoomId);
        return chatRoomUsers.stream().map(chatRoomUser -> {
            return ChatRoomReadStatusResponseDto.of(
                    chatRoomUser.getUser().getUserId(),
                    chatRoomUser.getUnreadStartMessageId()
            );
        }).toList();
    }

    // 1대1 채팅방 생성
    @Transactional
    public CreateDirectChatRoomResult createDirectChatRoom(Long userId, CreateDirectChatRoomRequestDto requestDto) {
        User findUser = userRepository.findById(userId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        if(findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        Long friendUserId = requestDto.getFriendId();

        // 자신과 1대1 채팅방을 만들 수 없음
        if (userId.equals(friendUserId)) {
            throw new ErrorException(ErrorCode.CANNOT_CREATE_CHAT_ROOM_WITH_SELF);
        }

        // 존재하지 않거나 친구가 아닌 유저와는 채팅방 생성활 수 없다.
        if(!userFriendRepository.existsActiveFriend(userId, friendUserId)) {
            throw new ErrorException(ErrorCode.CANNOT_CREATE_CHAT_ROOM_WITH_INVALID_USER);
        }

        // 1대1 채팅방은 유저간에 유일해야 하므로 유니크 제약 조건에 걸릴 수 있도록 아래처럼 계산이 필요
        Long maxUseId = Math.max(userId, friendUserId);
        Long minUserId = Math.min(userId, friendUserId);

        // 1대1 채팅방을 조회
        Optional<ChatRoom> chatRoomOptional = chatRoomRepository.findDirectChatRoom(maxUseId, minUserId);
        if(chatRoomOptional.isEmpty()) { // 1대1 채팅방이 존재하지 않는다면 ChatRoom 생성하고 ChatRoomUser 생성하면 된다.
            User friendUser = userRepository.findById(friendUserId).orElseThrow(() -> new ErrorException(ErrorCode.CANNOT_CREATE_CHAT_ROOM_WITH_INVALID_USER));
            User user1 = userId > friendUserId ? findUser : friendUser;
            User user2 = userId > friendUserId ? friendUser : findUser;

            // 채팅방 생성
            ChatRoom chatRoom = ChatRoom.of(ChatRoomType.DIRECT, user1, user2);
            ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

            // ChatRoom이 방금 생성되었으므로 ChatRoomUser의 중복 검사는 필요없다.
            // ChatRoomUser 생성한다. 1대1 채팅방은 두 유저의 권한이 모두 MEMBER 이다.
            ChatRoomUser chatRoomUser1 = ChatRoomUser.of(user1, savedChatRoom, ChatRoomUserRole.MEMBER, ChatRoomUserStatus.ACTIVE);
            ChatRoomUser chatRoomUser2 = ChatRoomUser.of(user2, savedChatRoom, ChatRoomUserRole.MEMBER, ChatRoomUserStatus.ACTIVE);
            chatRoomUserRepository.save(chatRoomUser1);
            chatRoomUserRepository.save(chatRoomUser2);

            // 응답 DTO 생성
            CreateDirectChatRoomResponseDto responseDto = CreateDirectChatRoomResponseDto.of(savedChatRoom.getChatRoomId());

            // ChatRoomListEvent 리스트 생성
            List<ChatRoomListEvent> chatRoomListEvents = new ArrayList<>();

            List<String> user2ProfileImageKeys = new ArrayList<>();
            user2ProfileImageKeys.add(user2.getProfileImageKey());
            chatRoomListEvents.add(ChatRoomListEvent.roomAdded(
                    savedChatRoom.getChatRoomId(),
                    ChatRoomType.DIRECT,
                    user1.getUserId(),
                    user2.getUsername(),
                    2L,
                    chatRoomUser1.getJoinedAt(),
                    user2ProfileImageKeys
            ));

            List<String> user1ProfileImageKeys = new ArrayList<>();
            user1ProfileImageKeys.add(user1.getProfileImageKey());
            chatRoomListEvents.add(ChatRoomListEvent.roomAdded(
                    savedChatRoom.getChatRoomId(),
                    ChatRoomType.DIRECT,
                    user2.getUserId(),
                    user1.getUsername(),
                    2L,
                    chatRoomUser2.getJoinedAt(),
                    user1ProfileImageKeys
            ));

            return CreateDirectChatRoomResult.of(responseDto, chatRoomListEvents);
        } else { // 1대1 채팅방이 존재한다면, 해당 ChatRoom에 대한 ChatRoomUser 들의 상태를 ACTIVE로 바꾼다.
            ChatRoom savedChatRoom = chatRoomOptional.get();

            // 채팅방에 대한 ChatRoomUser 들 조회
            List<ChatRoomUser> chatRoomUsers = chatRoomUserRepository.findByChatRoomIdWithUser(savedChatRoom.getChatRoomId());

            // ChatRoomListEvent 리스트 생성
            List<ChatRoomListEvent> chatRoomListEvents = new ArrayList<>();

            ChatRoomUser firstChatRoomUser = chatRoomUsers.get(0);
            ChatRoomUser secondChatRoomUser = chatRoomUsers.get(1);
            User firstUser = firstChatRoomUser.getUser();
            User secondUser = secondChatRoomUser.getUser();

            // 응답 DTO 생성
            CreateDirectChatRoomResponseDto responseDto = CreateDirectChatRoomResponseDto.of(savedChatRoom.getChatRoomId());

            // 채팅방의 가장 최근 메시지의 messageId를 조회
            Long boundaryMessageId = chatMessageRepository.findLatestMessageId(savedChatRoom.getChatRoomId())
                    .map(messageId -> messageId + 1)
                    .orElse(0L);

            if (firstChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
                firstChatRoomUser.joinChatRoom(boundaryMessageId);

                List<String> secondUserProfileImageKeys = new ArrayList<>();
                secondUserProfileImageKeys.add(secondUser.getProfileImageKey());
                chatRoomListEvents.add(ChatRoomListEvent.roomAdded(
                        savedChatRoom.getChatRoomId(),
                        ChatRoomType.DIRECT,
                        firstUser.getUserId(),
                        secondUser.getUsername(),
                        2L,
                        firstChatRoomUser.getJoinedAt(),
                        secondUserProfileImageKeys
                ));
            }

            if (secondChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
                secondChatRoomUser.joinChatRoom(boundaryMessageId);

                List<String> firstUserProfileImageKeys = new ArrayList<>();
                firstUserProfileImageKeys.add(firstUser.getProfileImageKey());
                chatRoomListEvents.add(ChatRoomListEvent.roomAdded(
                        savedChatRoom.getChatRoomId(),
                        ChatRoomType.DIRECT,
                        secondUser.getUserId(),
                        firstUser.getUsername(),
                        2L,
                        secondChatRoomUser.getJoinedAt(),
                        firstUserProfileImageKeys
                ));
            }

            return CreateDirectChatRoomResult.of(responseDto, chatRoomListEvents);
        }
    }

    // 단체 채팅방 생성
    @Transactional
    public CreateGroupChatRoomResult createGroupChatRoom(Long userId, CreateGroupChatRoomRequestDto requestDto) {
    	// 유저의 삭제여부 검증
        User findUser = userRepository.findById(userId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        if(findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        // 필드 추출, 리스트에서 중복 id들 제거
        List<Long> friendIds = new ArrayList<>(new HashSet<>(requestDto.getFriendIds()));

        // 추가할 친구가 1명 이상이어야 한다.
        if(friendIds.isEmpty()) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_MEMBER_REQUIRED);
        }

        // 자기 자신과 단체 채팅방을 만들 수 없다.
        if(friendIds.contains(userId)) {
            throw new ErrorException(ErrorCode.CANNOT_CREATE_CHAT_ROOM_WITH_SELF);
        }

        // 존재하지 않거나 친구가 아닌 유저와는 채팅방 생성활 수 없다.
        List<User> userFriends = userFriendRepository.findActiveFriendsByIds(userId, friendIds);
        if(userFriends.size() != friendIds.size()) {
            throw new ErrorException(ErrorCode.CANNOT_CREATE_CHAT_ROOM_WITH_INVALID_USER);
        }

    	// 채팅방 생성
    	ChatRoom chatRoom;
    	if(requestDto.getChatRoomName() == null || requestDto.getChatRoomName().isBlank()) {
            chatRoom = ChatRoom.of(ChatRoomType.GROUP);
        } else {
            chatRoom = ChatRoom.of(ChatRoomType.GROUP, requestDto.getChatRoomName().strip());
        }
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

    	// ChatRoomUser들 생성, 로그인 유저는 방장 권한을 가진다.
        List<User> users = new ArrayList<>(userFriends);
        users.add(findUser);
        List<ChatRoomUser> chatRoomUsers = users.stream()
                .map(user -> {
                    ChatRoomUserRole chatRoomUserRole = user.getUserId().equals(userId) ? ChatRoomUserRole.OWNER : ChatRoomUserRole.MEMBER;
                    return ChatRoomUser.of(
                            user,
                            savedChatRoom,
                            chatRoomUserRole,
                            ChatRoomUserStatus.ACTIVE
                    );
                })
                .toList();
        chatRoomUserRepository.saveAll(chatRoomUsers);

        // ChatRoomListEvent 리스트 생성
        List<ChatRoomListEvent> chatRoomListEvents = chatRoomUsers.stream()
                .map(receiverChatRoomUser -> {
                    User receiver = receiverChatRoomUser.getUser();

                    String roomName;
                    List<User> others = users.stream()
                            .filter(user -> !receiver.getUserId().equals(user.getUserId()))
                            .sorted((user1, user2) -> user1.getUsername().compareTo(user2.getUsername()))
                            .toList();

                    List<String> profileImageKeys = others.stream()
                            .map(user -> user.getProfileImageKey())
                            .toList();

                    if(savedChatRoom.getRoomName() == null) {
                        int count = Math.min(others.size(), 10);

                        roomName = others.stream()
                                .limit(count)
                                .map(user -> user.getUsername())
                                .collect(Collectors.joining(", "));

                        int leftCount = others.size() - count;
                        if (leftCount > 0) {
                            roomName = roomName + " 외 " + leftCount + "명";
                        }
                    } else {
                        roomName = savedChatRoom.getRoomName();
                    }

                    return ChatRoomListEvent.roomAdded(
                            savedChatRoom.getChatRoomId(),
                            ChatRoomType.GROUP,
                            receiver.getUserId(),
                            roomName,
                            (long) users.size(),
                            receiverChatRoomUser.getJoinedAt(),
                            profileImageKeys
                    );
                })
                .toList();

        CreateGroupChatRoomResponseDto responseDto = CreateGroupChatRoomResponseDto.of(savedChatRoom.getChatRoomId());

        return CreateGroupChatRoomResult.of(responseDto, chatRoomListEvents);
    }

    // 1대1 채팅방 초대
    @Transactional
    public InviteUserToDirectChatRoomResult inviteUserToDirectChatRoom(Long userId, InviteUserRequestDto requestDto) {
        // 필드 값 추출, 리스트에서 중복 id들 제거
        List<Long> friendIds = new ArrayList<>(new HashSet<>(requestDto.getFriendIds()));
        Long chatRoomId = requestDto.getChatRoomId();

        // 채팅방에 초대할 친구가 1명 이상이어야 한다.
        if(friendIds.isEmpty()) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_INVITE_MEMBER_REQUIRED);
        }

        // 자기자신을 채팅방에 초대할 수 없습니다.
        if(friendIds.contains(userId)) {
            throw new ErrorException(ErrorCode.CANNOT_INVITE_CHAT_ROOM_WITH_SELF);
        }

        // 유저가 채팅방에 속한 유저인지 검증
        ChatRoomUser findChatRoomUser = chatRoomUserRepository.findByChatRoomIdAndUserIdWithChatRoomAndUser(chatRoomId, userId)
                .orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));

        // 요청한 유저가 채팅방에서 나간 상태면 예외
        if(findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        // User 추출 후 삭제된 유저인지 검증
        User findUser = findChatRoomUser.getUser();
        if(findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        // 1대1 채팅방인지 확인
        ChatRoom findChatRoom = findChatRoomUser.getChatRoom();
        if(findChatRoom.getChatRoomType() != ChatRoomType.DIRECT) {
            throw new ErrorException(ErrorCode.GROUP_CHAT_ROOM_INVITE_API_REQUIRED);
        }

        // 기존 DIRECT 채팅방의 삭제되지 않은 사용자들을 모두 조회
        List<ChatRoomUser> existingDirectChatRoomUsers = chatRoomUserRepository.findByChatRoomIdWithUser(chatRoomId);

        // 기존 상대가 삭제된 상태이면 그룹으로 전환할 수 없다.
        if (existingDirectChatRoomUsers.size() != 2) {
            throw new ErrorException(
                    ErrorCode.DIRECT_CHAT_ROOM_PARTICIPANT_DELETED
            );
        }

        // 기존 DIRECT 채팅방 사용자 ID 추출
        Set<Long> existingDirectUserIds = existingDirectChatRoomUsers.stream()
                .map(existingDirectChatRoomUser ->
                        existingDirectChatRoomUser.getUser().getUserId()
                )
                .collect(Collectors.toSet());

        // 요청 목록에서 기존 DIRECT 사용자를 제외한 신규 사용자 ID 분리
        List<Long> newInviteeIds = friendIds.stream()
                .filter(friendId ->
                        !existingDirectUserIds.contains(friendId)
                )
                .toList();

        // 기존 DIRECT 사용자 외의 신규 사용자가 반드시 필요
        if (newInviteeIds.isEmpty()) {
            throw new ErrorException(
                    ErrorCode.DIRECT_CHAT_ROOM_INVITE_REQUIRES_NEW_MEMBER
            );
        }

        // 신규 초대 사용자만 친구 관계와 삭제 여부 검증
        List<User> newInviteeUsers = userFriendRepository.findActiveFriendsByIds(userId, newInviteeIds);

        if (newInviteeUsers.size() != newInviteeIds.size()) {
            throw new ErrorException(ErrorCode.CANNOT_INVITE_CHAT_ROOM_WITH_INVALID_USER);
        }

        // 요청 포함 여부와 관계없이 기존 DIRECT 사용자 중 LEFT 사용자 판별
        List<ChatRoomUser> rejoiningChatRoomUsers = existingDirectChatRoomUsers.stream()
                        .filter(existingDirectChatRoomUser -> existingDirectChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT)
                        .toList();

        // 1대1 채팅방을 그룹채팅방으로 변경하고, 초대한 유저를 방장으로 승격
        findChatRoom.convertToGroup();
        findChatRoomUser.setChatRoomUserRole(ChatRoomUserRole.OWNER);

        List<User> sortedUsers = newInviteeUsers.stream()
                .sorted((user1, user2) -> user1.getUsername().compareTo(user2.getUsername()))
                .toList();

        int displayCount = Math.min(sortedUsers.size(), 10);

        String displayedNames = sortedUsers.stream()
                .limit(displayCount)
                .map(user -> user.getUsername() + "님")
                .collect(Collectors.joining(", "));

        int remainingCount = sortedUsers.size() - displayCount;

        String joinMessage;
        if (remainingCount > 0) {
            joinMessage = displayedNames + " 외 " + remainingCount + "명이 채팅방에 참여했습니다.";
        } else {
            joinMessage = displayedNames + "이 채팅방에 참여했습니다.";
        }

        ChatMessage savedChatMessage = chatMessageRepository.save(
                ChatMessage.of(findChatRoom, findUser, joinMessage, ChatMessageType.JOIN_TEXT)
        );

        // 복귀해야 할 유저 복귀 처리
        rejoiningChatRoomUsers.forEach(rejoiningChatRoomUser -> {
            rejoiningChatRoomUser.joinChatRoom(savedChatMessage.getChatMessageId());
        });

        // 새롭게 초대해야 할 유저 초대
        List<ChatRoomUser> newChatRoomUsers = newInviteeUsers.stream()
                .map(newInviteeUser -> {
                    ChatRoomUser chatRoomUser = ChatRoomUser.of(
                            newInviteeUser, findChatRoom, ChatRoomUserRole.MEMBER, ChatRoomUserStatus.ACTIVE
                    );

                    chatRoomUser.joinChatRoom(savedChatMessage.getChatMessageId());

                    return chatRoomUser;
                })
                .toList();
        chatRoomUserRepository.saveAll(newChatRoomUsers);

        // 방이 추가될 유저 구분
        Set<Long> roomAddedUserIds = Stream.concat(
                        rejoiningChatRoomUsers.stream(),
                        newChatRoomUsers.stream()
                )
                .map(chatRoomUser -> chatRoomUser.getUser().getUserId())
                .collect(Collectors.toSet());

        // 현재 채팅방의 유저들 조회
        List<ChatRoomUser> activeChatRoomUsers = chatRoomUserRepository.findActiveChatRoomUsers(chatRoomId);

        // MESSAGE_SENT 채팅방 목록 이벤트를 받을 유저 ID 추출
        List<Long> eventUserIds = activeChatRoomUsers.stream()
                .map(activeChatRoomUser ->
                        activeChatRoomUser.getUser().getUserId()
                )
                .toList();

        // ChatEvent 생성
        ChatEvent chatEvent = ChatEvent.of(
                findChatRoom.getChatRoomId(),
                userId,
                findUser.getUsername(),
                findUser.getProfileImageKey(),
                null,
                savedChatMessage.getContent(),
                savedChatMessage.getChatMessageId(),
                savedChatMessage.getChatMessageType(),
                savedChatMessage.getCreatedAt(),
                eventUserIds
        );

        // 전체 ACTIVE 사용자를 이름순으로 한 번만 정렬
        List<User> sortedActiveUsers = activeChatRoomUsers.stream()
                .map(ChatRoomUser::getUser)
                .sorted((user1, user2) ->
                        user1.getUsername().compareTo(user2.getUsername())
                )
                .toList();

        // ChatRoomListEvent 리스트 생성
        List<ChatRoomListEvent> chatRoomListEvents = activeChatRoomUsers.stream()
                .map(activeChatRoomUser -> {
                    Long receiverUserId = activeChatRoomUser.getUser().getUserId();
                    List<User> otherUsers = sortedActiveUsers.stream()
                            .filter(otherUser ->
                                    !otherUser.getUserId().equals(receiverUserId)
                            )
                            .toList();

                    String roomName;
                    if(activeChatRoomUser.getCustomRoomName() != null) {
                        roomName = activeChatRoomUser.getCustomRoomName();
                    } else {
                        int count = Math.min(otherUsers.size(), 10);

                        String names = otherUsers.stream()
                                .limit(count)
                                .map(user -> user.getUsername())
                                .collect(Collectors.joining(", "));

                        int leftCount = otherUsers.size() - count;
                        if (leftCount > 0) {
                            roomName = names + " 외 " + leftCount + "명";
                        } else {
                            roomName = names;
                        }
                    }

                    List<String> profileImageKeys = otherUsers.stream()
                            .map(otherUser -> {
                                return otherUser.getProfileImageKey();
                            })
                            .toList();

                    if(roomAddedUserIds.contains(receiverUserId)) {
                        return ChatRoomListEvent.roomAdded(
                                findChatRoom.getChatRoomId(),
                                findChatRoom.getChatRoomType(),
                                activeChatRoomUser.getUser().getUserId(),
                                roomName,
                                (long)activeChatRoomUsers.size(),
                                savedChatMessage.getCreatedAt(),
                                profileImageKeys
                        );
                    } else {
                        return ChatRoomListEvent.roomChanged(
                                findChatRoom.getChatRoomId(),
                                findChatRoom.getChatRoomType(),
                                receiverUserId,
                                roomName,
                                (long)activeChatRoomUsers.size(),
                                profileImageKeys
                        );
                    }
                })
                .toList();

        return InviteUserToDirectChatRoomResult.of(chatRoomListEvents, chatEvent);
    }

    // 단체 채팅방 초대
    @Transactional
    public InviteUserToGroupChatRoomResult inviteUserToGroupChatRoom(Long userId, InviteUserRequestDto requestDto) {
        // 필드 값 추출, 리스트에서 중복 id들 제거
        List<Long> friendIds = new ArrayList<>(new HashSet<>(requestDto.getFriendIds()));
        Long chatRoomId = requestDto.getChatRoomId();

        // 채팅방에 초대할 친구가 1명 이상이어야 한다.
        if(friendIds.isEmpty()) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_INVITE_MEMBER_REQUIRED);
        }

        // 자기자신을 채팅방에 초대할 수 없습니다.
        if(friendIds.contains(userId)) {
            throw new ErrorException(ErrorCode.CANNOT_INVITE_CHAT_ROOM_WITH_SELF);
        }

        // 유저가 채팅방에 속한 유저인지 검증
        ChatRoomUser findChatRoomUser = chatRoomUserRepository.findByChatRoomIdAndUserIdWithChatRoomAndUser(chatRoomId, userId)
                .orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));

        // 요청한 유저가 채팅방에서 나간 상태면 예외
        if(findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        // User 추출 후 삭제된 유저인지 검증
        User findUser = findChatRoomUser.getUser();
        if(findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        // 단체 채팅방인지 확인
        ChatRoom findChatRoom = findChatRoomUser.getChatRoom();
        if(findChatRoom.getChatRoomType() != ChatRoomType.GROUP) {
            throw new ErrorException(ErrorCode.DIRECT_CHAT_ROOM_INVITE_API_REQUIRED);
        }

        // 존재하지 않거나 친구가 아닌 유저는 초대할 수 없습니다.
        List<User> userFriends = userFriendRepository.findActiveFriendsByIds(userId, friendIds);
        if(userFriends.size() != friendIds.size()) {
            throw new ErrorException(ErrorCode.CANNOT_INVITE_CHAT_ROOM_WITH_INVALID_USER);
        }

        // 기존 채팅방의 ChatRoomUser들을 조회
        List<ChatRoomUser> existingInviteeChatRoomUsers = chatRoomUserRepository.findByChatRoomIdAndFriendIds(chatRoomId, friendIds);

        // 복귀해야될 유저 판별
        List<ChatRoomUser> rejoiningChatRoomUsers = existingInviteeChatRoomUsers.stream()
                .filter(rejoiningChatRoomUser -> {
                    return rejoiningChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT;
                })
                .toList();

        // 새롭게 초대해야할 유저 판별
        Set<Long> existingInviteeIds  = existingInviteeChatRoomUsers.stream()
                .map(existingInviteeChatRoomUser -> existingInviteeChatRoomUser.getUser().getUserId())
                .collect(Collectors.toSet());
        List<User> newInviteeUsers = userFriends.stream()
                .filter(userFriend -> !existingInviteeIds.contains(userFriend.getUserId()))
                .toList();

        // 복귀하거나 새로 초대할 유저가 없으면 예외
        if (rejoiningChatRoomUsers.isEmpty() && newInviteeUsers.isEmpty()) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_INVITEES_ALREADY_ACTIVE);
        }

        // 복귀 메시지 생성 후 저장
        List<User> sortedUsers = Stream.concat(
                        rejoiningChatRoomUsers.stream()
                                .map(rejoiningChatRoomUser -> rejoiningChatRoomUser.getUser())
                                .toList()
                                .stream(),
                        newInviteeUsers.stream()
                ).sorted((user1, user2) -> user1.getUsername().compareTo(user2.getUsername()))
                .toList();

        int displayCount = Math.min(sortedUsers.size(), 10);

        String displayedNames = sortedUsers.stream()
                .limit(displayCount)
                .map(user -> user.getUsername() + "님")
                .collect(Collectors.joining(", "));

        int remainingCount = sortedUsers.size() - displayCount;

        String joinMessage;
        if (remainingCount > 0) {
            joinMessage = displayedNames + " 외 " + remainingCount + "명이 채팅방에 참여했습니다.";
        } else {
            joinMessage = displayedNames + "이 채팅방에 참여했습니다.";
        }

        ChatMessage savedChatMessage = chatMessageRepository.save(
                ChatMessage.of(findChatRoom, findUser, joinMessage, ChatMessageType.JOIN_TEXT)
        );

        // 복귀해야 할 유저 복귀 처리
        rejoiningChatRoomUsers.forEach(rejoiningChatRoomUser -> {
            rejoiningChatRoomUser.joinChatRoom(savedChatMessage.getChatMessageId());
        });

        // 새롭게 초대해야 할 유저 초대
        List<ChatRoomUser> newChatRoomUsers = newInviteeUsers.stream()
                .map(newInviteeUser -> {
                    ChatRoomUser chatRoomUser = ChatRoomUser.of(
                            newInviteeUser, findChatRoom, ChatRoomUserRole.MEMBER, ChatRoomUserStatus.ACTIVE
                    );

                    chatRoomUser.joinChatRoom(savedChatMessage.getChatMessageId());

                    return chatRoomUser;
                })
                .toList();
        chatRoomUserRepository.saveAll(newChatRoomUsers);

        // 방이 추가될 유저 구분
        Set<Long> roomAddedUserIds = Stream.concat(
                        rejoiningChatRoomUsers.stream(),
                        newChatRoomUsers.stream()
                )
                .map(chatRoomUser -> chatRoomUser.getUser().getUserId())
                .collect(Collectors.toSet());

        // 현재 채팅방의 유저들 조회
        List<ChatRoomUser> activeChatRoomUsers = chatRoomUserRepository.findActiveChatRoomUsers(chatRoomId);

        // MESSAGE_SENT 채팅방 목록 이벤트를 받을 유저 ID 추출
        List<Long> eventUserIds = activeChatRoomUsers.stream()
                .map(activeChatRoomUser ->
                        activeChatRoomUser.getUser().getUserId()
                )
                .toList();

        // ChatEvent 생성
        ChatEvent chatEvent = ChatEvent.of(
                findChatRoom.getChatRoomId(),
                userId,
                findUser.getUsername(),
                findUser.getProfileImageKey(),
                null,
                savedChatMessage.getContent(),
                savedChatMessage.getChatMessageId(),
                savedChatMessage.getChatMessageType(),
                savedChatMessage.getCreatedAt(),
                eventUserIds
        );

        // 전체 ACTIVE 사용자를 이름순으로 한 번만 정렬
        List<User> sortedActiveUsers = activeChatRoomUsers.stream()
                .map(ChatRoomUser::getUser)
                .sorted((user1, user2) ->
                        user1.getUsername().compareTo(user2.getUsername())
                )
                .toList();

        // ChatRoomListEvent 리스트 생성
        List<ChatRoomListEvent> chatRoomListEvents = activeChatRoomUsers.stream()
                        .map(activeChatRoomUser -> {
                            Long receiverUserId = activeChatRoomUser.getUser().getUserId();
                            List<User> otherUsers = sortedActiveUsers.stream()
                                    .filter(otherUser ->
                                            !otherUser.getUserId().equals(receiverUserId)
                                    )
                                    .toList();

                            String roomName;
                            if(activeChatRoomUser.getCustomRoomName() != null) {
                                roomName = activeChatRoomUser.getCustomRoomName();
                            }
                            else if(findChatRoom.getRoomName() != null) {
                                roomName = findChatRoom.getRoomName();
                            } else {
                                int count = Math.min(otherUsers.size(), 10);

                                String names = otherUsers.stream()
                                        .limit(count)
                                        .map(user -> user.getUsername())
                                        .collect(Collectors.joining(", "));

                                int leftCount = otherUsers.size() - count;
                                if (leftCount > 0) {
                                    roomName = names + " 외 " + leftCount + "명";
                                } else {
                                    roomName = names;
                                }
                            }

                            List<String> profileImageKeys = otherUsers.stream()
                                            .map(otherUser -> {
                                                return otherUser.getProfileImageKey();
                                            })
                                            .toList();

                            if(roomAddedUserIds.contains(receiverUserId)) {
                                return ChatRoomListEvent.roomAdded(
                                        findChatRoom.getChatRoomId(),
                                        findChatRoom.getChatRoomType(),
                                        receiverUserId,
                                        roomName,
                                        (long)activeChatRoomUsers.size(),
                                        savedChatMessage.getCreatedAt(),
                                        profileImageKeys
                                );
                            } else {
                                return ChatRoomListEvent.roomChanged(
                                        findChatRoom.getChatRoomId(),
                                        findChatRoom.getChatRoomType(),
                                        receiverUserId,
                                        roomName,
                                        (long)activeChatRoomUsers.size(),
                                        profileImageKeys
                                );
                            }
                        })
                        .toList();

        return InviteUserToGroupChatRoomResult.of(chatRoomListEvents, chatEvent);
    }

    // 채팅방 목록 조회
    @Transactional(readOnly = true)
    public ChatRoomListResponseDto findAllChatRooms(Long userId) {
    	User user = userRepository.findById(userId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
    	
		List<ChatRoomListItemReseponseDto> chatRooms = chatRoomMapper.findAllChatRoomsByUserId(userId)
				.stream()
				.map(ChatRoomListItemReseponseDto::from)
				.toList();
		 
      return ChatRoomListResponseDto.of(userId, user.getUsername(), chatRooms);
    }
    
    // 채팅방 나가기
    @Transactional
    public LeaveChatRoomResult leaveChatRoom(Long userId, LeaveChatRoomRequestDto requestDto) {
    	Long chatRoomId = requestDto.getChatRoomId();
    	Long nextOwnerId = requestDto.getNextOwnerId();
    	
    	// 채팅방이 존재하지 않거나, 채팅방의 유저가 아닐 시 예외
    	ChatRoomUser chatRoomUser = chatRoomUserRepository.findByChatRoomIdAndUserIdWithChatRoomAndUser(chatRoomId, userId)
    			.orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
    	
    	ChatRoom chatRoom = chatRoomUser.getChatRoom(); 
    	
    	// 1대1 채팅방은 생성시 모두 MEMBER
    	// 유저가 OWNER 이라면 단체 채팅방이므로 채팅방의 타입 검사는 필요 x, 권한 양도 필요.
    	if(chatRoomUser.getChatRoomUserRole() == ChatRoomUserRole.OWNER) {
    		// 나 자신에게 권한을 양도할 수 없음
    		if(userId.equals(nextOwnerId)) {
    			throw new ErrorException(ErrorCode.CHAT_ROOM_NEXT_OWNER_INVALID);
    		}
    		
			// 권한을 양도할 유저의 ChatRoomUser 조회
            // 채팅방이 존재하는 것은 위에서 검증 되었으므로 권한을 양도할 유저가 같은 채팅방 소속인지 검사하는 것
    		ChatRoomUser nextOwnerChatRoomUser = chatRoomUserRepository.findByChatRoomIdAndUserIdWithUser(chatRoomId, nextOwnerId)
        			.orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_NEXT_OWNER_INVALID));
    		
    		// 권한을 양도할 유저의 삭제 여부 체크
    		if(nextOwnerChatRoomUser.getUser().getDeleted()) {
    			throw new ErrorException(ErrorCode.CHAT_ROOM_NEXT_OWNER_INVALID);
    		}
    		
    		// 권한을 양도할 유저의 상태 체크
    		if(nextOwnerChatRoomUser.getChatRoomUserStatus() != ChatRoomUserStatus.ACTIVE) {
    			throw new ErrorException(ErrorCode.CHAT_ROOM_NEXT_OWNER_INVALID);
    		}
    		
    		// 권한 양도
    		nextOwnerChatRoomUser.setChatRoomUserRole(ChatRoomUserRole.OWNER);
    		chatRoomUser.setChatRoomUserRole(ChatRoomUserRole.MEMBER);
    	}

        // 수정사항 flush
        chatRoomUserRepository.flush();

        /**
         * 나가기 메시지 저장, 전송 시작
         * ChatRoom에 대한 락 시작
         **/
        Long seq = chatRoomMapper.getLastSeqLock(chatRoomId);

        List<Long> eventUserIds = chatRoomUserRepository.findActiveUserIds(chatRoomId);

        ChatEventResult chatEventResult = chatRoomJoinLeaveService.processLeaveEvent(List.of(chatRoomUser), eventUserIds, chatRoomId, seq);
        List<ChatEvent> chatEvents = chatEventResult.getChatEvents();
        ChatMessage flagChatMessage = chatEventResult.getFlagChatMessage();

        if(flagChatMessage != null) {
            // chatRoom 의 lastSeq 증가, addNumber 는 1증감이 필요
            chatRoomMapper.updateLastSeq(chatEventResult.getLastSeq() , flagChatMessage.getContent(), flagChatMessage.getCreatedAt(), chatRoom.getChatRoomId());
        }

        return null;
    }
}

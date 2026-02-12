package com.tgg.chat.domain.chat.service;

import com.tgg.chat.common.redis.pubsub.ChatEvent;
import com.tgg.chat.domain.chat.dto.query.UserIdUsernameQueryDto;
import com.tgg.chat.domain.chat.dto.request.CreateDirectChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.request.CreateGroupChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.request.InviteUserRequestDto;
import com.tgg.chat.domain.chat.dto.request.LeaveChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.response.ChatRoomListResponseDto;
import com.tgg.chat.domain.chat.dto.response.CreateDirectChatRoomResponseDto;
import com.tgg.chat.domain.chat.dto.response.CreateGroupChatRoomResponseDto;
import com.tgg.chat.domain.chat.entity.ChatMessage;
import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.enums.ChatMessageType;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import com.tgg.chat.domain.chat.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.chat.repository.*;
import com.tgg.chat.domain.friend.repository.UserFriendMapper;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserMapper;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.stream.Collectors;

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

    private final UserFriendMapper userFriendMapper;

    private final ChatMessageRepository chatMessageRepository;

    // 1대1 채팅방 생성
    @Transactional
    public Map<String, Object> createDirectChatRoom(Long userId, CreateDirectChatRoomRequestDto requestDto) {

        Long friendUserId = requestDto.getFriendId();

        // 자신과 1대1 채팅방을 만들 수 없음
        if (userId.equals(friendUserId)) {
            throw new ErrorException(ErrorCode.CANNOT_CREATE_CHAT_ROOM_WITH_SELF);
        }

        // 존재하지 않거나 친구가 아닌 유저와는 채팅방 생성활 수 없다.
        int friendCount = userFriendMapper.countActiveFriendsByIds(userId, List.of(friendUserId));
        if(friendCount != 1) {
            throw new ErrorException(ErrorCode.CANNOT_CREATE_CHAT_ROOM_WITH_INVALID_USER);
        }

        // 1대1 채팅방은 유저간에 유일해야 하므로 유니크 제약 조건에 걸릴 수 있도록 아래처럼 계산이 필요
        Long maxUseId = Math.max(userId, friendUserId);
        Long minUserId = Math.min(userId, friendUserId);
        User user1 = userRepository.getReferenceById(maxUseId);
        User user2 = userRepository.getReferenceById(minUserId);

        // 1대1 채팅방을 조회
        Optional<ChatRoom> chatRoomOptional = chatRoomRepository.findByChatRoomTypeAndDirectUser1AndDirectUser2(ChatRoomType.DIRECT, user1, user2);
        CreateDirectChatRoomResponseDto responseDto = null;
        List<ChatEvent> chatEvents = new ArrayList<>();
        if(chatRoomOptional.isEmpty()) { // 1대1 채팅방이 존재하지 않는다면 ChatRoom 생성하고 ChatRoomUser 생성하면 된다.
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
            responseDto = CreateDirectChatRoomResponseDto.of(savedChatRoom.getChatRoomId());
        } else { // 1대1 채팅방이 존재한다면, 해당 ChatRoom에 대한 ChatRoomUser 들의 상태를 ACTIVE로 바꾼다.
            ChatRoom chatRoom = chatRoomOptional.get();

            // 채팅방별 ChatMessage의 최대 seq 조회
            Long seq = chatRoomMapper.getLastSeqLock(chatRoom.getChatRoomId());

            long addNumber = 1L;

            boolean flag = false;
            ChatMessage flagChatMessage = null;

            List<ChatRoomUser> chatRoomUsers = chatRoomUserRepository.findByChatRoomIdWithUser(chatRoom.getChatRoomId());
            for(ChatRoomUser findChatRoomUser : chatRoomUsers) {
                if(findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
                    findChatRoomUser.setJoinedAt();
                    findChatRoomUser.setChatRoomUserStatus(ChatRoomUserStatus.ACTIVE);
                    findChatRoomUser.setLastReadSeq(seq);
                    findChatRoomUser.setHistoryStartSeq(seq);

                    User findUser = findChatRoomUser.getUser();

                    ChatMessage joinChatMessage = ChatMessage.of(chatRoom, findUser, seq + addNumber, findUser.getUsername() + "가 채팅에 참여했습니다.", ChatMessageType.JOIN_TEXT);
                    ChatMessage savedJoinChatMessage = chatMessageRepository.save(joinChatMessage);

                    // 1대1 채팅방 멤버는 2명이므로 2로 고정
                    ChatEvent joinChatEvent = ChatEvent.of(
                            chatRoom.getChatRoomId(),
                            findChatRoomUser.getUser().getUserId(),
                            findUser.getUsername() + "가 채팅에 참여했습니다.",
                            seq + addNumber,
                            ChatMessageType.JOIN_TEXT,
                            savedJoinChatMessage.getCreatedAt(),
                            2L
                    );

                    chatEvents.add(joinChatEvent);

                    addNumber++;

                    flag = true;
                    flagChatMessage = savedJoinChatMessage;
                }
            }

            if(flag) {
                // chatRoom 의 lastSeq 증가, addNumber 는 1증감이 필요
                chatRoomMapper.updateLastSeq(seq + (addNumber - 1) , flagChatMessage.getContent(), flagChatMessage.getCreatedAt(), chatRoom.getChatRoomId());
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("chatEvents", chatEvents);
        payload.put("responseDto", responseDto);

        return payload;

    }

    // 단체 채팅방 생성
    @Transactional
    public CreateGroupChatRoomResponseDto createGroupChatRoom(Long userId, CreateGroupChatRoomRequestDto requestDto) {
    	
    	// 필드 추출, 리스트에서 중복 id들 제거
    	List<Long> friendIds = requestDto.getFriendIds() == null ? List.of() : requestDto.getFriendIds();
        friendIds = new ArrayList<>(new HashSet<>(friendIds));
    	String chatRoomName = requestDto.getChatRoomName();

        // 추가할 친구가 1명 이상이어야 한다.
        if(friendIds.isEmpty()) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_MEMBER_REQUIRED);
        }

        // 자기 자신과 단체 채팅방을 만들 수 없다.
        if(friendIds.contains(userId)) {
            throw new ErrorException(ErrorCode.CANNOT_CREATE_CHAT_ROOM_WITH_SELF);
        }

        // 존재하지 않거나 친구가 아닌 유저와는 채팅방 생성활 수 없다.
        int friendCount = userFriendMapper.countActiveFriendsByIds(userId, friendIds);
        if(friendCount != friendIds.size()) {
            throw new ErrorException(ErrorCode.CANNOT_CREATE_CHAT_ROOM_WITH_INVALID_USER);
        }
    	
    	// 채팅방 생성
    	ChatRoom chatRoom = ChatRoom.of(ChatRoomType.GROUP, chatRoomName);
    	ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
    	
    	// ChatRoomUser들 생성, 로그인 유저는 방장 권한을 가진다.
        friendIds.add(userId);
    	List<ChatRoomUser> chatRoomUsers = friendIds.stream()
    			.map(id -> {
		    		User user = userRepository.getReferenceById(id);
		    		ChatRoomUserRole chatRoomUserRole = id.equals(userId) ? ChatRoomUserRole.OWNER : ChatRoomUserRole.MEMBER;
		    		return ChatRoomUser.of(user, savedChatRoom, chatRoomUserRole, ChatRoomUserStatus.ACTIVE);
    			})
    			.collect(Collectors.toList());
    	chatRoomUserRepository.saveAll(chatRoomUsers);
    	
    	// 응답 DTO 생성
    	return CreateGroupChatRoomResponseDto.of(savedChatRoom.getChatRoomId());
    	
    }

    // 채팅방 목록 조회
    public List<ChatRoomListResponseDto> findAllChatRooms(Long userId) {

        // 채팅방 목록 조회후 응답 DTO로 변환하여 return
        return chatRoomMapper.findAllChatRoomsByUserId(userId)
                .stream()
                .map(ChatRoomListResponseDto::from)
                .toList();

    }

    // 채팅방 초대
    @Transactional
    public List<ChatEvent> inviteUserToChatRoom(Long userId, InviteUserRequestDto requestDto) {

        // 필드 값 추출, 리스트에서 중복 id들 제거
        List<Long> friendIds = requestDto.getFriendIds() == null ? List.of() : requestDto.getFriendIds();
        friendIds = new ArrayList<>(new HashSet<>(friendIds));
        Long chatRoomId = requestDto.getChatRoomId();

        // 채팅방에 초대할 친구가 1명 이상이어야 한다.
        if(friendIds.isEmpty()) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_INVITE_MEMBER_REQUIRED);
        }

        // 자기자신을 채팅방에 초대할 수 없습니다.
        if(friendIds.contains(userId)) {
            throw new ErrorException(ErrorCode.CANNOT_INVITE_CHAT_ROOM_WITH_SELF);
        }

        // 요청 유저가 채팅방에 속하면서 방장인지 검사
        // ChatRoomUser가 있다면 ChatRoom도 있는 것이므로 체크하지 않는다.
        if(!chatRoomUserMapper.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_INVITE_PERMISSION_DENIED);
        }

        // 존재하지 않거나 친구가 아닌 유저는 초대할 수 없습니다.
        int friendCount = userFriendMapper.countActiveFriendsByIds(userId, friendIds);
        if(friendCount != friendIds.size()) {
            throw new ErrorException(ErrorCode.CANNOT_INVITE_CHAT_ROOM_WITH_INVALID_USER);
        }

        // chatRoom 조회
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_NOT_EXISTS));
        
        // 채팅방별 ChatMessage의 최대 seq 조회
        Long seq = chatRoomMapper.getLastSeqLock(chatRoomId);
        
        // 채팅방에 참여중인 인원들 수 조회
        Long memberCount = chatRoomUserMapper.getMemberCount(chatRoomId);

        long addNumber = 1L;

        boolean flag = false;
        ChatMessage flagChatMessage = null;
        List<ChatEvent> chatEvents = new ArrayList<>();
        
        // 이미 ChatRoomUser 가 존재시 상태가 ACTIVE면 예외, LEFT면 ACTIVE로 수정
        List<ChatRoomUser> chatRoomUsers = chatRoomUserRepository.findByChatRoomIdAndFriendIds(chatRoomId, friendIds);
        List<Long> existingFriendIds = new ArrayList<>();
        for(ChatRoomUser findChatRoomUser : chatRoomUsers) {
        	if(findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.ACTIVE) {
                throw new ErrorException(ErrorCode.CHAT_ROOM_INVITE_ALREADY_MEMBER);
            } else {
            	findChatRoomUser.setJoinedAt();
            	findChatRoomUser.setChatRoomUserStatus(ChatRoomUserStatus.ACTIVE);
            	findChatRoomUser.setLastReadSeq(seq);
            	findChatRoomUser.setHistoryStartSeq(seq);
                existingFriendIds.add(findChatRoomUser.getUser().getUserId());
                
                User findUser = findChatRoomUser.getUser();

                ChatMessage joinChatMessage = ChatMessage.of(chatRoom, findUser, seq + addNumber, findUser.getUsername() + "가 채팅에 참여했습니다.", ChatMessageType.JOIN_TEXT);
                ChatMessage savedJoinChatMessage = chatMessageRepository.save(joinChatMessage);

                // 1대1 채팅방 멤버는 2명이므로 2로 고정
                ChatEvent joinChatEvent = ChatEvent.of(
                        chatRoom.getChatRoomId(),
                        findChatRoomUser.getUser().getUserId(),
                        findUser.getUsername() + "가 채팅에 참여했습니다.",
                        seq + addNumber,
                        ChatMessageType.JOIN_TEXT,
                        savedJoinChatMessage.getCreatedAt(),
                        memberCount++
                );

                chatEvents.add(joinChatEvent);

                addNumber++;

                flag = true;
                flagChatMessage = savedJoinChatMessage;
            }
        }
        
        if(flag) {
            // chatRoom 의 lastSeq 수정, addNumber 는 1증감이 필요
            chatRoomMapper.updateLastSeq(seq + (addNumber - 1) , flagChatMessage.getContent(), flagChatMessage.getCreatedAt(), chatRoomId);
        }

        // 저장해야하는 friendId 들 분류
        List<Long> insertFriendIds = friendIds.stream()
                .filter(id -> !existingFriendIds.contains(id))
                .toList();

        // lastReadSeq 수정하고 ChatRoomUser 저장
        long currentSeq = seq + (addNumber - 1);
        List<ChatRoomUser> newEntities = insertFriendIds.stream()
                .map(friendId -> {
                    ChatRoomUser chatRoomUser = ChatRoomUser.of(
                            userRepository.getReferenceById(friendId),
                            chatRoom,
                            ChatRoomUserRole.MEMBER,
                            ChatRoomUserStatus.ACTIVE
                    );
                    chatRoomUser.setLastReadSeq(currentSeq);
                    chatRoomUser.setHistoryStartSeq(currentSeq);
                    return chatRoomUser;
                })
                .toList();
        chatRoomUserRepository.saveAll(newEntities);
        
        Map<Long, String> userNameById = userMapper.getUserNames(insertFriendIds).stream()
        	      .collect(Collectors.toMap(
        	          UserIdUsernameQueryDto::getUserId,
        	          UserIdUsernameQueryDto::getUsername
        	      ));
        
        for(ChatRoomUser findChatRoomUser : newEntities) {
            User findUser = findChatRoomUser.getUser();
            String username = userNameById.get(findUser.getUserId());

            ChatMessage joinChatMessage = ChatMessage.of(chatRoom, findUser, seq + addNumber,  username + "가 채팅에 참여했습니다.", ChatMessageType.JOIN_TEXT);
            ChatMessage savedJoinChatMessage = chatMessageRepository.save(joinChatMessage);

            // 1대1 채팅방 멤버는 2명이므로 2로 고정
            ChatEvent joinChatEvent = ChatEvent.of(
                    chatRoom.getChatRoomId(),
                    findUser.getUserId(),
                    username + "가 채팅에 참여했습니다.",
                    seq + addNumber,
                    ChatMessageType.JOIN_TEXT,
                    savedJoinChatMessage.getCreatedAt(),
                    memberCount++
            );

            chatEvents.add(joinChatEvent);

            addNumber++;
            
            flagChatMessage = savedJoinChatMessage;
        }
        
        if(newEntities.size() > 0) {
        	// chatRoom 의 lastSeq 수정, addNumber 는 1증감이 필요
            chatRoomMapper.updateLastSeq(seq + (addNumber - 1) , flagChatMessage.getContent(), flagChatMessage.getCreatedAt(), chatRoomId);
        }

        return chatEvents;
    }
    
    // 채팅방 나가기
    @Transactional
    public ChatEvent leaveChatRoom(Long userId, LeaveChatRoomRequestDto requestDto) {
    	
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

        // 1대1 채팅방, 단체 채팅방에서 일반유저는  ChatRoomUserStatus 수정
        chatRoomUser.setChatRoomUserStatus(ChatRoomUserStatus.LEFT);

        // 수정사항 flush
        chatRoomUserRepository.flush();

        // 채팅방별 ChatMessage의 최대 seq 조회
        Long seq = chatRoomMapper.getLastSeqLock(chatRoomId);

        // 메시지 db에 저장
        User user = chatRoomUser.getUser();
        ChatMessage chatMessage = ChatMessage.of(chatRoom, user, seq + 1, user.getUsername() + "가 채팅에서 나갔습니다.", ChatMessageType.LEAVE_TEXT);
        ChatMessage savedChatMessage = chatMessageRepository.save(chatMessage);

        // 채팅방에 참여중인 인원들 수 조회
        Long memberCount = chatRoomUserMapper.getMemberCount(chatRoomId);

        ChatEvent chatEvent = ChatEvent.of(
                chatRoomId,
                userId,
                user.getUsername() + "가 채팅에서 나갔습니다.",
                seq + 1,
                ChatMessageType.LEAVE_TEXT,
                savedChatMessage.getCreatedAt(),
                memberCount
        );

        // chatRoom 의 lastSeq 증가
        chatRoomMapper.updateLastSeq(seq + 1, user.getUsername() + "가 채팅에서 나갔습니다.", savedChatMessage.getCreatedAt(), chatRoomId);

        return chatEvent;

    }

}

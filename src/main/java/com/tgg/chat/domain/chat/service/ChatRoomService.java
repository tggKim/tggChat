package com.tgg.chat.domain.chat.service;

import com.tgg.chat.common.redis.pubsub.ChatEvent;
import com.tgg.chat.domain.chat.dto.internal.ChatEventResult;
import com.tgg.chat.domain.chat.dto.query.ChatRoomListRowDto;
import com.tgg.chat.domain.chat.dto.query.UserIdUsernameQueryDto;
import com.tgg.chat.domain.chat.dto.request.CreateDirectChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.request.CreateGroupChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.request.InviteUserRequestDto;
import com.tgg.chat.domain.chat.dto.request.LeaveChatRoomRequestDto;
import com.tgg.chat.domain.chat.dto.response.ChatRoomListItemReseponseDto;
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

    private final ChatMessageService chatMessageService;

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
        ChatEventResult chatEventResult = null;
        ChatRoom savedChatRoom = null;
        if(chatRoomOptional.isEmpty()) { // 1대1 채팅방이 존재하지 않는다면 ChatRoom 생성하고 ChatRoomUser 생성하면 된다.
            // 채팅방 생성
            ChatRoom chatRoom = ChatRoom.of(ChatRoomType.DIRECT, user1, user2);
            savedChatRoom = chatRoomRepository.save(chatRoom);

            // 채팅방별 ChatMessage의 최대 seq 조회
            // chatRoom에 대한 락 시작
            Long seq = chatRoomMapper.getLastSeqLock(chatRoom.getChatRoomId());

            // 메시지 이벤트를 받을 유저 리스트 생성
            List<Long> eventUserIds = List.of(user1.getUserId(), user2.getUserId());

            // ChatRoom이 방금 생성되었으므로 ChatRoomUser의 중복 검사는 필요없다.
            // ChatRoomUser 생성한다. 1대1 채팅방은 두 유저의 권한이 모두 MEMBER 이다.
            ChatRoomUser chatRoomUser1 = ChatRoomUser.of(user1, savedChatRoom, ChatRoomUserRole.MEMBER, ChatRoomUserStatus.ACTIVE);
            ChatRoomUser chatRoomUser2 = ChatRoomUser.of(user2, savedChatRoom, ChatRoomUserRole.MEMBER, ChatRoomUserStatus.ACTIVE);
            chatRoomUserRepository.save(chatRoomUser1);
            chatRoomUserRepository.save(chatRoomUser2);
            List<ChatRoomUser> newEntities = List.of(chatRoomUser1, chatRoomUser2);

            // 유저들에 대한 입장 메시지 저장하고 전송
            chatEventResult = chatMessageService.chatRoomJoinEvent(newEntities, eventUserIds, savedChatRoom.getChatRoomId(), seq);

            // 응답 DTO 생성
            responseDto = CreateDirectChatRoomResponseDto.of(savedChatRoom.getChatRoomId());


        } else { // 1대1 채팅방이 존재한다면, 해당 ChatRoom에 대한 ChatRoomUser 들의 상태를 ACTIVE로 바꾼다.
            savedChatRoom = chatRoomOptional.get();

            // 채팅방별 ChatMessage의 최대 seq 조회
            // chatRoom에 대한 락 시작
            Long seq = chatRoomMapper.getLastSeqLock(savedChatRoom.getChatRoomId());

            // 메시지 이벤트를 받을 유저 리스트 생성
            List<Long> eventUserIds = chatRoomUserRepository.findAllUserIds(savedChatRoom.getChatRoomId());
            if (eventUserIds.size() != 2) {
                throw new ErrorException(ErrorCode.CHAT_PARTNER_DELETED);
            }

            List<ChatRoomUser> chatRoomUsers = chatRoomUserRepository.findByChatRoomIdWithUser(savedChatRoom.getChatRoomId());

            // 유저들에 대한 입장 메시지 저장하고 전송
            chatEventResult = chatMessageService.chatRoomRejoinEvent(chatRoomUsers, eventUserIds, savedChatRoom.getChatRoomId(), seq);

            // 응답 DTO 생성
            responseDto = CreateDirectChatRoomResponseDto.of(savedChatRoom.getChatRoomId());
        }

        ChatMessage flagChatMessage = chatEventResult.getFlagChatMessage();
        if(flagChatMessage != null) {
            // chatRoom 의 lastSeq 증가, addNumber 는 1증감이 필요
            chatRoomMapper.updateLastSeq(chatEventResult.getLastSeq() , flagChatMessage.getContent(), flagChatMessage.getCreatedAt(), savedChatRoom.getChatRoomId());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("chatEvents", chatEventResult.getChatEvents());
        payload.put("responseDto", responseDto);

        return payload;

    }

    // 단체 채팅방 생성
    @Transactional
    public Map<String, Object> createGroupChatRoom(Long userId, CreateGroupChatRoomRequestDto requestDto) {
    	
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

        // 채팅방별 ChatMessage의 최대 seq 조회
        // chatRoom에 대한 락 시작
        Long seq = chatRoomMapper.getLastSeqLock(savedChatRoom.getChatRoomId());

    	// ChatRoomUser들 생성, 로그인 유저는 방장 권한을 가진다.
        friendIds.add(userId);
        List<ChatRoomUser> newEntities = friendIds.stream()
                .map(id -> {
                    ChatRoomUserRole chatRoomUserRole = id.equals(userId) ? ChatRoomUserRole.OWNER : ChatRoomUserRole.MEMBER;
                    ChatRoomUser chatRoomUser = ChatRoomUser.of(
                            userRepository.getReferenceById(id),
                            savedChatRoom,
                            chatRoomUserRole,
                            ChatRoomUserStatus.ACTIVE
                    );
                    return chatRoomUser;
                })
                .toList();
        chatRoomUserRepository.saveAll(newEntities);

        // friendId들 저장 수행
        ChatEventResult chatEventResult = chatMessageService.chatRoomJoinEvent(newEntities, friendIds, savedChatRoom.getChatRoomId(), seq);
        ChatMessage flagChatMessage = chatEventResult.getFlagChatMessage();
        List<ChatEvent> chatEvents = chatEventResult.getChatEvents();

        if(flagChatMessage != null) {
            // chatRoom 의 lastSeq 증가, addNumber 는 1증감이 필요
            chatRoomMapper.updateLastSeq(chatEventResult.getLastSeq() , flagChatMessage.getContent(), flagChatMessage.getCreatedAt(), savedChatRoom.getChatRoomId());
        }

        CreateGroupChatRoomResponseDto responseDto = CreateGroupChatRoomResponseDto.of(savedChatRoom.getChatRoomId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("chatEvents", chatEvents);
        payload.put("responseDto", responseDto);

    	return payload;
    	
    }

    // 채팅방 목록 조회
    public ChatRoomListResponseDto findAllChatRooms(Long userId) {
    	User user = userRepository.findById(userId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
    	
		List<ChatRoomListItemReseponseDto> chatRooms = chatRoomMapper.findAllChatRoomsByUserId(userId)
				.stream()
				.map(ChatRoomListItemReseponseDto::from)
				.toList();
		 
      return ChatRoomListResponseDto.of(userId, user.getUsername(), chatRooms);
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

        // 요청 유저가 단체 채팅방에 속하면서 방장인지 검사
        // ChatRoomUser가 있다면 ChatRoom도 있는 것이므로 체크하지 않는다.
        if(!chatRoomUserMapper.existsByChatRoomIdAndUserId(chatRoomId, userId)) {
            throw new ErrorException(ErrorCode.CHAT_ROOM_INVITE_PERMISSION_DENIED);
        }

        // 존재하지 않거나 친구가 아닌 유저는 초대할 수 없습니다.
        int friendCount = userFriendMapper.countActiveFriendsByIds(userId, friendIds);
        if(friendCount != friendIds.size()) {
            throw new ErrorException(ErrorCode.CANNOT_INVITE_CHAT_ROOM_WITH_INVALID_USER);
        }

        // 채팅방별 ChatMessage의 최대 seq 조회
        // chatRoom에 대한 락 시작
        Long seq = chatRoomMapper.getLastSeqLock(chatRoomId);

        // 채팅방 목록에서 메시지 전송 프레임을 받을 유저id 목록
        List<Long> eventUserIds = chatRoomUserRepository.findActiveUserIds(chatRoomId);
        eventUserIds.addAll(friendIds);
        eventUserIds = eventUserIds.stream().distinct().toList();

        // ChatRoomUser 가 LEFT면 ACTIVE로 수정 후 메시지 저장
        List<ChatRoomUser> chatRoomUsers = chatRoomUserRepository.findByChatRoomIdAndFriendIds(chatRoomId, friendIds);
        ChatEventResult chatEventResult1 = chatMessageService.chatRoomRejoinEvent(chatRoomUsers, eventUserIds, chatRoomId, seq);
        ChatMessage flagChatMessage1 = chatEventResult1.getFlagChatMessage();
        List<ChatEvent> chatEvents1 = chatEventResult1.getChatEvents();

        // 저장해야하는 friendId 들 분류
        Set<Long> existingFriendIds = new HashSet<>();
        chatRoomUsers.forEach(chatRoomUser -> existingFriendIds.add(chatRoomUser.getUser().getUserId()));
        List<Long> insertFriendIds = friendIds.stream()
                .filter(id -> !existingFriendIds.contains(id))
                .toList();

        // friendId들 저장
        ChatRoom chatRoom = chatRoomRepository.getReferenceById(chatRoomId);
        List<ChatRoomUser> newEntities = insertFriendIds.stream()
                .map(friendId -> {
                    ChatRoomUser chatRoomUser = ChatRoomUser.of(
                            userRepository.getReferenceById(friendId),
                            chatRoom,
                            ChatRoomUserRole.MEMBER,
                            ChatRoomUserStatus.ACTIVE
                    );
                    chatRoomUser.setLastReadSeq(seq);
                    chatRoomUser.setHistoryStartSeq(seq);
                    return chatRoomUser;
                })
                .toList();
        chatRoomUserRepository.saveAll(newEntities);

        // friendId들 저장 수행
        ChatEventResult chatEventResult2 = chatMessageService.chatRoomJoinEvent(newEntities, eventUserIds, chatRoomId, chatEventResult1.getLastSeq());
        ChatMessage flagChatMessage2 = chatEventResult2.getFlagChatMessage();
        List<ChatEvent> chatEvents2 = chatEventResult2.getChatEvents();

        ChatMessage flagChatMessage = flagChatMessage2 != null ? flagChatMessage2 : flagChatMessage1;
        if(flagChatMessage != null) {
            // chatRoom 의 lastSeq 증가, addNumber 는 1증감이 필요
            chatRoomMapper.updateLastSeq(chatEventResult2.getLastSeq() , flagChatMessage.getContent(), flagChatMessage.getCreatedAt(), chatRoomId);
        }

        chatEvents1.addAll(chatEvents2);

        return chatEvents1;
    }


    
    // 채팅방 나가기
    @Transactional
    public List<ChatEvent> leaveChatRoom(Long userId, LeaveChatRoomRequestDto requestDto) {
    	
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

        ChatEventResult chatEventResult = chatMessageService.processLeaveEvent(List.of(chatRoomUser), eventUserIds, chatRoomId, seq);
        List<ChatEvent> chatEvents = chatEventResult.getChatEvents();
        ChatMessage flagChatMessage = chatEventResult.getFlagChatMessage();

        if(flagChatMessage != null) {
            // chatRoom 의 lastSeq 증가, addNumber 는 1증감이 필요
            chatRoomMapper.updateLastSeq(chatEventResult.getLastSeq() , flagChatMessage.getContent(), flagChatMessage.getCreatedAt(), chatRoom.getChatRoomId());
        }

        return chatEvents;
    }
}

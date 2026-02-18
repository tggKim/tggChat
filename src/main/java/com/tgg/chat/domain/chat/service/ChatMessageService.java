package com.tgg.chat.domain.chat.service;

import com.tgg.chat.common.redis.pubsub.ChatEvent;
import com.tgg.chat.domain.chat.dto.internal.ChatEventResult;
import com.tgg.chat.domain.chat.dto.query.UserIdUsernameQueryDto;
import com.tgg.chat.domain.chat.entity.ChatMessage;
import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.enums.ChatMessageType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import com.tgg.chat.domain.chat.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.chat.repository.ChatMessageRepository;
import com.tgg.chat.domain.chat.repository.ChatRoomMapper;
import com.tgg.chat.domain.chat.repository.ChatRoomRepository;
import com.tgg.chat.domain.chat.repository.ChatRoomUserRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserMapper;
import com.tgg.chat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final UserMapper userMapper;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomUserRepository chatRoomUserRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public ChatEventResult chatRoomJoinEvent(
            List<ChatRoomUser> insertChatRoomUsers,
            List<Long> eventUserIds,
            Long chatRoomId,
            Long seq
    ) {
        ChatRoom chatRoom = chatRoomRepository.getReferenceById(chatRoomId);

        List<ChatEvent> chatEvents = new ArrayList<>();

        long addNumber = 1L;

        ChatMessage lastChatMessage = null;

        // 저장해야할 friendId가 있으면 실행
        if(!insertChatRoomUsers.isEmpty()) {
            List<Long> insertFriendIds = insertChatRoomUsers.stream()
                    .map(insertChatRoomUser -> insertChatRoomUser.getUser().getUserId())
                    .toList();

            Map<Long, String> userNameById = userMapper.getUserNames(insertFriendIds).stream()
                    .collect(Collectors.toMap(
                            UserIdUsernameQueryDto::getUserId,
                            UserIdUsernameQueryDto::getUsername
                    ));

            for(ChatRoomUser insertChatRoomUser : insertChatRoomUsers) {
                // 입장 Message 저장하고 ChatEvent 생성
                User user = insertChatRoomUser.getUser();
                String username = userNameById.get(user.getUserId());
                lastChatMessage = processJoinOrExitEvent(chatRoom, user, username, seq + addNumber, ChatMessageType.JOIN_TEXT, chatEvents, eventUserIds);
                addNumber++;
            }
        }

        return ChatEventResult.of(chatEvents, seq + (addNumber - 1), lastChatMessage);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ChatEventResult chatRoomRejoinEvent(
            List<ChatRoomUser> chatRoomUsers,
            List<Long> eventUserIds,
            Long chatRoomId,
            Long seq
    ) {
        ChatRoom chatRoom = chatRoomRepository.getReferenceById(chatRoomId);

        List<ChatEvent> chatEvents = new ArrayList<>();

        long addNumber = 1L;

        ChatMessage flagChatMessage = null;

        for(ChatRoomUser chatRoomUser : chatRoomUsers) {
            if(chatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
                // ChatRoomUser 의 상태 ACTIVE로 변경
                chatRoomUser.joinChatRoom(seq);

                // 입장 Message 저장하고 ChatEvent 생성
                User findUser = chatRoomUser.getUser();
                flagChatMessage = processJoinOrExitEvent(chatRoom, findUser, findUser.getUsername(), seq + addNumber, ChatMessageType.JOIN_TEXT, chatEvents, eventUserIds);
                addNumber++;
            }
        }

        return ChatEventResult.of(chatEvents, seq + (addNumber - 1), flagChatMessage);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ChatEventResult processLeaveEvent(
            List<ChatRoomUser> chatRoomUsers,
            List<Long> eventUserIds,
            Long chatRoomId,
            Long seq
    ) {
        ChatRoom chatRoom = chatRoomRepository.getReferenceById(chatRoomId);

        List<ChatEvent> chatEvents = new ArrayList<>();

        long addNumber = 1L;

        ChatMessage flagChatMessage = null;

        for(ChatRoomUser findChatRoomUser : chatRoomUsers) {
            if(findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.ACTIVE) {
                findChatRoomUser.setChatRoomUserStatus(ChatRoomUserStatus.LEFT);

                // 입장 Message 저장하고 ChatEvent 생성
                User findUser = findChatRoomUser.getUser();
                flagChatMessage = processJoinOrExitEvent(chatRoom, findUser, findUser.getUsername(), seq + addNumber, ChatMessageType.LEAVE_TEXT, chatEvents, eventUserIds);
                addNumber++;
            }
        }

        return ChatEventResult.of(chatEvents, seq + (addNumber - 1), flagChatMessage);
    }

    private ChatMessage processJoinOrExitEvent(
            ChatRoom chatRoom,
            User user,
            String username,
            Long seq,
            ChatMessageType chatMessageType,
            List<ChatEvent> chatEvents,
            List<Long> eventUserIds
    ) {
        String content = null;
        if(chatMessageType == ChatMessageType.JOIN_TEXT) {
            content = username + "가 채팅에 참여했습니다.";
        } else {
            content = username + "가 채팅에서 나갔습니다.";
        }

        ChatMessage joinChatMessage = ChatMessage.of(chatRoom, user, seq, content, chatMessageType);
        ChatMessage savedJoinChatMessage = chatMessageRepository.save(joinChatMessage);

        // 참여를 알리는 메시지는 읽음 처리 필요없으므로 0값 보낸다
        ChatEvent chatEvent = ChatEvent.of(
                chatRoom.getChatRoomId(),
                user.getUserId(),
                content,
                seq,
                chatMessageType,
                savedJoinChatMessage.getCreatedAt(),
                0L,
                eventUserIds
        );

        chatEvents.add(chatEvent);

        return savedJoinChatMessage;
    }
}

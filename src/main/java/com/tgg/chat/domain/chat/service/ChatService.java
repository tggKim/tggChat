package com.tgg.chat.domain.chat.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tgg.chat.common.redis.pubsub.ChatEvent;
import com.tgg.chat.common.redis.pubsub.RedisPublisher;
import com.tgg.chat.domain.chat.dto.request.ChatMessageRequest;
import com.tgg.chat.domain.chat.room.entity.ChatRoom;
import com.tgg.chat.domain.chat.room.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.room.enums.ChatRoomType;
import com.tgg.chat.domain.chat.room.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.chat.room.repository.ChatRoomUserRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {
	
    private final RedisPublisher redisPublisher;
    private final ChatRoomUserRepository chatRoomUserRepository;

    public void sendMessage(Long userId, Long chatRoomId, ChatMessageRequest message) {

        // 채팅방의 존재여부와, 유저가 채팅방에 속한 유저인지 검증
        ChatRoomUser chatRoomUser = chatRoomUserRepository.findWithAllDetails(chatRoomId, userId)
                .orElseThrow(() -> new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED));
        
        // ChatRoom, User 추출
        ChatRoom chatRoom = chatRoomUser.getChatRoom();
        User user = chatRoomUser.getUser();
        
        // 삭제된 유저인지 검증
        if(user.getDeleted()) {
        	throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }
        
        if(chatRoom.getChatRoomType() == ChatRoomType.DIRECT) {
        	
        	// 메시지 db에 저장
        	
        	// 1대1 채팅방의 2명의 유저의 상태가 LEFT면 ACTIVE로 수정
        	List<ChatRoomUser> chatRoomUsers = chatRoomUserRepository.findByChatRoom(chatRoom);
            chatRoomUsers.forEach(findChatRoomUser -> {
                if(findChatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
                	findChatRoomUser.setJoinedAt();
                	findChatRoomUser.setChatRoomUserStatus(ChatRoomUserStatus.ACTIVE);
                	findChatRoomUser.setLastReadSeq(chatRoom.getLastMessageSeq());
                }
            });
        	
        } else {
        	
        	// 단체 채팅방이면 요청한 유저의 상태가 LEFT면 예외
        	if(chatRoomUser.getChatRoomUserStatus() == ChatRoomUserStatus.LEFT) {
        		throw new ErrorException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
            }
        	
        }

        ChatEvent chatEvent = ChatEvent.of(chatRoomId, userId, message.getContent());
		redisPublisher.publishChatEvent(chatEvent);
		
    }
    
}

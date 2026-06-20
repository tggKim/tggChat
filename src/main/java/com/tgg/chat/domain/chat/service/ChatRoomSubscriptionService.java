package com.tgg.chat.domain.chat.service;

import com.tgg.chat.domain.chat.repository.ChatRoomUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRoomSubscriptionService {
    private final ChatRoomUserRepository chatRoomUserRepository;

    public boolean canSubscribe(Long userId, Long chatRoomId) {
        return chatRoomUserRepository.existsActiveMember(chatRoomId, userId);
    }
}

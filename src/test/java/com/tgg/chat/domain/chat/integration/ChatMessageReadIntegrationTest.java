package com.tgg.chat.domain.chat.integration;

import com.tgg.chat.domain.chat.dto.request.ReadChatMessagesRequestDto;
import com.tgg.chat.domain.chat.entity.ChatMessage;
import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.enums.ChatMessageType;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import com.tgg.chat.domain.chat.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.chat.service.ChatMessageService;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.support.SqlTestContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import({SqlTestContainerConfig.class, ChatMessageService.class})
public class ChatMessageReadIntegrationTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private ChatMessageService service;

    @DisplayName("최신 메시지를 읽은 후 이전 메시지 읽음 요청이 처리되어도 읽음 위치는 후퇴하지 않는다")
    @Test
    void olderReadDoesNotMoveCursorBackwards() {

        // given
        User user = em.persist(User.of("reader@example.test", "test-password", "reader", "READER23"));

        ChatRoom room = em.persist(ChatRoom.of(ChatRoomType.GROUP));
        ChatRoomUser member = em.persist(ChatRoomUser.of(user, room, ChatRoomUserRole.OWNER, ChatRoomUserStatus.ACTIVE));

        ChatMessage older = em.persist(ChatMessage.of(room, user, "older", ChatMessageType.TEXT));
        ChatMessage latest = em.persistAndFlush(ChatMessage.of(room, user, "latest", ChatMessageType.TEXT));
        em.clear();

        ReadChatMessagesRequestDto olderRequest = new ReadChatMessagesRequestDto();
        ReflectionTestUtils.setField(olderRequest, "readMessageId", older.getChatMessageId());
        ReadChatMessagesRequestDto latestRequest = new ReadChatMessagesRequestDto();
        ReflectionTestUtils.setField(latestRequest, "readMessageId", latest.getChatMessageId());

        // when
        service.readMessage(user.getUserId(), room.getChatRoomId(), latestRequest);
        service.readMessage(user.getUserId(), room.getChatRoomId(), olderRequest);

        // then
        em.clear();
        ChatRoomUser saved = em.find(ChatRoomUser.class, member.getChatRoomUserId());

        assertThat(saved.getUnreadStartMessageId()).isEqualTo(latest.getChatMessageId() + 1);
    }

}

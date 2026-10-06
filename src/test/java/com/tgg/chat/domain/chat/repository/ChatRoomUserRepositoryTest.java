package com.tgg.chat.domain.chat.repository;

import com.tgg.chat.domain.chat.entity.ChatRoom;
import com.tgg.chat.domain.chat.entity.ChatRoomUser;
import com.tgg.chat.domain.chat.enums.ChatRoomType;
import com.tgg.chat.domain.chat.enums.ChatRoomUserRole;
import com.tgg.chat.domain.chat.enums.ChatRoomUserStatus;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.support.SqlTestContainerConfig;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import({SqlTestContainerConfig.class})
class ChatRoomUserRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ChatRoomUserRepository repository;

    private User user;
    private User otherUser;
    private ChatRoom room;
    private ChatRoom otherRoom;
    private ChatRoomUser member;

    @BeforeEach
    void setUp() {
        user = em.persist(User.of("a@example.test", "password", "userA", "USERA234"));
        otherUser = em.persist(User.of("b@example.test", "password", "userB", "USERB234"));
        room = em.persist(ChatRoom.of(ChatRoomType.GROUP));
        otherRoom = em.persist(ChatRoom.of(ChatRoomType.GROUP));

        member = em.persist(ChatRoomUser.of(user, room, ChatRoomUserRole.OWNER, ChatRoomUserStatus.ACTIVE));
        em.persist(ChatRoomUser.of(otherUser, otherRoom, ChatRoomUserRole.OWNER, ChatRoomUserStatus.ACTIVE));
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findByChatRoomIdAndUserIdWithUser - 채팅방과 사용자가 일치하면 참여 정보와 사용자를 함께 조회한다")
    void findsMemberWithUser() {
        ChatRoomUser found = repository
                .findByChatRoomIdAndUserIdWithUser(room.getChatRoomId(), user.getUserId())
                .orElseThrow();

        assertThat(found.getChatRoomUserId()).isEqualTo(member.getChatRoomUserId());
        assertThat(Hibernate.isInitialized(found.getUser())).isTrue();
        assertThat(found.getUser().getUserId()).isEqualTo(user.getUserId());
    }

    @Test
    @DisplayName("findByChatRoomIdAndUserIdWithUser - 해당 채팅방에 참여하지 않은 사용자라면 빈 결과를 반환한다")
    void returnsEmptyForOtherUser() {
        assertThat(repository.findByChatRoomIdAndUserIdWithUser(
                room.getChatRoomId(), otherUser.getUserId())).isEmpty();
    }

}

package com.tgg.chat.domain.chat.repository;

import com.tgg.chat.domain.chat.service.ChatMessageService;
import com.tgg.chat.support.SqlTestContainerConfig;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import({SqlTestContainerConfig.class})
class ChatMessageRepositoryTest {

}
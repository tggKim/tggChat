package com.tgg.chat.domain.user.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class UserTagGenerator {

    private static final String TAG_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int TAG_LENGTH = 8;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generate() {
        StringBuilder tag = new StringBuilder(TAG_LENGTH);

        for (int i = 0; i < TAG_LENGTH; i++) {
            int index = SECURE_RANDOM.nextInt(TAG_CHARACTERS.length());

            tag.append(TAG_CHARACTERS.charAt(index));
        }

        return tag.toString();
    }
}

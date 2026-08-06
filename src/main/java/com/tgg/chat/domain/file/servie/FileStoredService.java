package com.tgg.chat.domain.file.servie;

import com.tgg.chat.domain.file.entity.StoredFile;
import com.tgg.chat.domain.file.repository.StoredFileRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStoredService {
    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;

    private final Path fileRootPath;

    public FileStoredService(
            @Value("${file_root_path}") String fileRootPath,
            StoredFileRepository storedFileRepository,
            UserRepository userRepository
    ) {
        this.storedFileRepository = storedFileRepository;
        this.fileRootPath = Path.of(fileRootPath);
        this.userRepository = userRepository;
    }

    public void saveUserProfile(Long userId, MultipartFile userProfileImage) {
        User findUser = userRepository.findById(userId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        if(findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }


    }

    private String getRandomFileName() {
        return UUID.randomUUID().toString();
    }
}

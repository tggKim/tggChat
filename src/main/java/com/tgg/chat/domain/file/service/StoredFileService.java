package com.tgg.chat.domain.file.service;

import com.tgg.chat.domain.file.entity.StoredFile;
import com.tgg.chat.domain.file.repository.StoredFileRepository;
import com.tgg.chat.domain.user.entity.User;
import com.tgg.chat.domain.user.repository.UserRepository;
import com.tgg.chat.exception.ErrorCode;
import com.tgg.chat.exception.ErrorException;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
public class StoredFileService {
    private static final Set<String> ALLOWED_IMAGE_FORMATS = Set.of("jpeg", "png", "gif", "webp");

    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;

    private final Path fileRootPath;

    public StoredFileService(
            @Value("${file_root_path}") String fileRootPath,
            StoredFileRepository storedFileRepository,
            UserRepository userRepository
    ) {
        this.storedFileRepository = storedFileRepository;
        this.fileRootPath = Path.of(fileRootPath);
        this.userRepository = userRepository;
    }

    @Transactional
    public void saveUserProfile(Long userId, MultipartFile userProfileImage) {
        User findUser = userRepository.findById(userId).orElseThrow(() -> new ErrorException(ErrorCode.USER_NOT_FOUND));
        if(findUser.getDeleted()) {
            throw new ErrorException(ErrorCode.USER_NOT_FOUND);
        }

        String previousProfileImageKey = findUser.getProfileImageKey();

        // 새로운 fileKey를 생성 후 유저의 정보 업데이트
        String newProfileImageKey = "user:" + userId + ":" + UUID.randomUUID();
        findUser.updateProfileImageKey(newProfileImageKey);

        // 이미지와 썸네일 이미지 저장
        String imageFormat;
        BufferedImage firstFrame;
        try(
                InputStream inputStream = userProfileImage.getInputStream();
                ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)
        ) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if(!readers.hasNext()) {
                throw new ErrorException(ErrorCode.UNSUPPORTED_IMAGE_FORMAT);
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(imageInputStream);
                imageFormat = reader.getFormatName().toLowerCase(Locale.ROOT);

                if (!ALLOWED_IMAGE_FORMATS.contains(imageFormat)) {
                    throw new ErrorException(ErrorCode.UNSUPPORTED_IMAGE_FORMAT);
                }

                // GIF와 WebP가 애니메이션이어도 첫 프레임만 읽는다.
                firstFrame = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String originalExtension = imageFormat.equals("jpeg") ? ".jpg" : "." + imageFormat;
        String originalContentType = "image/" + imageFormat;

        String imageName = UUID.randomUUID() + originalExtension;
        String thumbnailImageName = UUID.randomUUID() + ".jpg";
        Path imagePath = fileRootPath.resolve(imageName);
        Path thumbnailImagePath = fileRootPath.resolve(thumbnailImageName);
        long thumbnailFileSize;
        try {
            userProfileImage.transferTo(imagePath);

            Thumbnails.of(firstFrame)
                    .size(320, 320)
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .outputQuality(0.9)
                    .toFile(thumbnailImagePath.toFile());

            thumbnailFileSize = Files.size(thumbnailImagePath);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(imagePath);
            } catch (IOException cleanupException) {
                log.warn("프로필 이미지 저장 실패 후 원본 파일 정리 실패: {}", imagePath, cleanupException);
            }

            try {
                Files.deleteIfExists(thumbnailImagePath);
            } catch (IOException cleanupException) {
                log.warn("프로필 이미지 저장 실패 후 썸네일 파일 정리 실패: {}", thumbnailImagePath, cleanupException);
            }

            throw new RuntimeException(e);
        }

        // 원본 이미지에 대한 StoredFile 저장
        storedFileRepository.save(
                StoredFile.of(
                        newProfileImageKey,
                        imageName,
                        userProfileImage.getOriginalFilename(),
                        originalContentType,
                        userProfileImage.getSize(),
                        1)
        );

        // 썸네일 이미지에 대한 StoredFile 저장
        storedFileRepository.save(
                StoredFile.of(
                        newProfileImageKey,
                        thumbnailImageName,
                        userProfileImage.getOriginalFilename(),
                        "image/jpeg",
                        thumbnailFileSize,
                        2)
        );

        // 기존의 파일이 있었다면 실제파일과 StoredFile 모두 삭제
        if(previousProfileImageKey != null) {
            List<StoredFile> previousStoredFiles = storedFileRepository.findAllByFileKey(previousProfileImageKey);

            previousStoredFiles.forEach(
                    previousStoredFile -> {
                        Path deletePath = fileRootPath.resolve(previousStoredFile.getStoredFileName());
                        try {
                            Files.deleteIfExists(deletePath);
                        } catch (IOException e) {
                            // 기존파일 삭제시 에러가 난다고 해서 새로운 파일 저장에 영향을 주면 안된다.
                            log.warn("기존 프로필 파일 삭제 실패: {}", deletePath, e);
                        }
                    }
            );

            storedFileRepository.deleteAll(previousStoredFiles);
        }
    }
}

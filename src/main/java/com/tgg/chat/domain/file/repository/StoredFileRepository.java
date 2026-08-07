package com.tgg.chat.domain.file.repository;

import com.tgg.chat.domain.file.entity.StoredFile;
import com.tgg.chat.domain.file.enums.StoredFileVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    List<StoredFile> findAllByFileKey(String fileKey);

    Optional<StoredFile> findByFileKeyAndStoredFileVariant(String fileKey, StoredFileVariant storedFileVariant);
}

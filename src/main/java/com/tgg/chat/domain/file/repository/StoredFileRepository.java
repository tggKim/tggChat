package com.tgg.chat.domain.file.repository;

import com.tgg.chat.domain.file.entity.StoredFile;
import com.tgg.chat.domain.file.enums.StoredFileVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
    List<StoredFile> findAllByFileKey(String fileKey);

    Optional<StoredFile> findByFileKeyAndStoredFileVariant(String fileKey, StoredFileVariant storedFileVariant);

    Optional<StoredFile> findByFileKeyAndFileOrderAndStoredFileVariant(String fileKey, Integer fileOrder, StoredFileVariant storedFileVariant);

    @Query("""
            select sf
            from StoredFile sf
            where sf.fileKey IN :storedFileKeys
            and sf.storedFileVariant = com.tgg.chat.domain.file.enums.StoredFileVariant.ORIGINAL
            order by sf.fileKey, fileOrder
            """)
    List<StoredFile> findOriginalMessageFilesByFileKeys(List<String> storedFileKeys);
}

package com.tgg.chat.domain.file.servie;

import com.tgg.chat.domain.file.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileStoredService {
    private final StoredFileRepository storedFileRepository;


}

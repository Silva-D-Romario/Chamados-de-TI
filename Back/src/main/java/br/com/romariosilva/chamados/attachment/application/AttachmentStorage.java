package br.com.romariosilva.chamados.attachment.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class AttachmentStorage {

    private final Path directory;

    public AttachmentStorage(@Value("${app.attachments.directory}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        String storedName = UUID.randomUUID().toString();
        try {
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(storedName));
            return storedName;
        } catch (IOException exception) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Não foi possível armazenar o anexo", exception);
        }
    }

    public Resource load(String storedName) {
        try {
            Resource resource = new UrlResource(directory.resolve(storedName).toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(NOT_FOUND, "Arquivo não encontrado");
            }
            return resource;
        } catch (IOException exception) {
            throw new ResponseStatusException(NOT_FOUND, "Arquivo não encontrado", exception);
        }
    }

    public void delete(String storedName) {
        try {
            Files.deleteIfExists(directory.resolve(storedName));
        } catch (IOException exception) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Não foi possível remover o anexo", exception);
        }
    }
}

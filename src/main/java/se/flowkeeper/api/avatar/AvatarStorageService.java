package se.flowkeeper.api.avatar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Owns the avatar image files on disk — a fixed volume mounted into the API
 * container (flowkeeper-infra), not the database. Filenames are always
 * server-generated (a random UUID plus a fixed extension), never taken from
 * client input, so path traversal isn't reachable through the upload path;
 * {@link #load} still re-validates the shape of whatever filename it's
 * given before touching the filesystem, since that one does take a client
 * -supplied path variable (GET /api/v1/avatars/{filename}).
 */
@Service
public class AvatarStorageService {

	private static final Logger log = LoggerFactory.getLogger(AvatarStorageService.class);

	private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
		"image/jpeg", "jpg",
		"image/png", "png",
		"image/webp", "webp"
	);

	private static final Pattern FILENAME_PATTERN = Pattern.compile("^[0-9a-f-]{36}\\.(jpg|png|webp)$");

	private final Path storageDir;

	public AvatarStorageService(@Value("${app.avatars.storage-dir}") String storageDir) {
		this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(this.storageDir);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create avatar storage directory: " + this.storageDir, e);
		}
	}

	/** Validates and saves the uploaded image, returning its generated filename (not a URL). */
	public String store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ValidationException("Avatar file is empty");
		}
		String extension = EXTENSION_BY_CONTENT_TYPE.get(file.getContentType());
		if (extension == null) {
			throw new ValidationException("Avatar must be a JPEG, PNG, or WEBP image");
		}

		String filename = UUID.randomUUID() + "." + extension;
		try {
			Files.copy(file.getInputStream(), storageDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to store avatar", e);
		}
		return filename;
	}

	/** Best-effort delete of a previous avatar — never fails the request that's replacing it. */
	public void delete(String filename) {
		if (filename == null || !FILENAME_PATTERN.matcher(filename).matches()) {
			return;
		}
		try {
			Files.deleteIfExists(storageDir.resolve(filename));
		} catch (IOException e) {
			log.warn("Could not delete old avatar file {}: {}", filename, e.getMessage());
		}
	}

	public Resource load(String filename) {
		if (!FILENAME_PATTERN.matcher(filename).matches()) {
			throw new ResourceNotFoundException("Avatar not found");
		}
		Path path = storageDir.resolve(filename);
		if (!Files.isRegularFile(path)) {
			throw new ResourceNotFoundException("Avatar not found");
		}
		return new FileSystemResource(path);
	}

	public MediaType mediaTypeFor(String filename) {
		if (filename.endsWith(".png")) {
			return MediaType.IMAGE_PNG;
		}
		if (filename.endsWith(".webp")) {
			return MediaType.valueOf("image/webp");
		}
		return MediaType.IMAGE_JPEG;
	}

}

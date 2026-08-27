package se.flowkeeper.api.avatar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarStorageServiceTest {

	@TempDir
	Path storageDir;

	private AvatarStorageService service() {
		return new AvatarStorageService(storageDir.toString());
	}

	@Test
	void storeSavesAJpegAndReturnsAUuidFilename() throws IOException {
		MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[] {1, 2, 3});

		String filename = service().store(file);

		assertThat(filename).matches("^[0-9a-f-]{36}\\.jpg$");
		assertThat(Files.readAllBytes(storageDir.resolve(filename))).containsExactly(1, 2, 3);
	}

	@Test
	void storeAcceptsPngAndWebp() {
		AvatarStorageService service = service();

		assertThat(service.store(new MockMultipartFile("file", "me.png", "image/png", new byte[] {1})))
			.endsWith(".png");
		assertThat(service.store(new MockMultipartFile("file", "me.webp", "image/webp", new byte[] {1})))
			.endsWith(".webp");
	}

	@Test
	void storeRejectsAnUnsupportedContentType() {
		MockMultipartFile file = new MockMultipartFile("file", "me.gif", "image/gif", new byte[] {1});

		assertThatThrownBy(() -> service().store(file)).isInstanceOf(ValidationException.class);
	}

	@Test
	void storeRejectsAnEmptyFile() {
		MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[0]);

		assertThatThrownBy(() -> service().store(file)).isInstanceOf(ValidationException.class);
	}

	@Test
	void loadReturnsTheStoredFile() {
		AvatarStorageService service = service();
		String filename = service.store(new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[] {9}));

		Resource resource = service.load(filename);

		assertThat(resource.exists()).isTrue();
		assertThat(service.mediaTypeFor(filename)).isEqualTo(MediaType.IMAGE_JPEG);
	}

	@Test
	void loadRejectsAFilenameThatDoesNotMatchTheGeneratedShape() {
		AvatarStorageService service = service();

		assertThatThrownBy(() -> service.load("../../etc/passwd")).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.load("not-a-uuid.jpg")).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void loadRejectsAWellFormedFilenameThatWasNeverStored() {
		assertThatThrownBy(() -> service().load("11111111-1111-1111-1111-111111111111.jpg"))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void deleteRemovesAPreviouslyStoredFile() {
		AvatarStorageService service = service();
		String filename = service.store(new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[] {9}));

		service.delete(filename);

		assertThat(Files.exists(storageDir.resolve(filename))).isFalse();
	}

	@Test
	void deleteIgnoresAFilenameThatDoesNotMatchTheGeneratedShape() {
		// Never touches the filesystem outside storageDir for a malformed name — silently no-ops instead.
		service().delete("../../etc/passwd");
	}

}

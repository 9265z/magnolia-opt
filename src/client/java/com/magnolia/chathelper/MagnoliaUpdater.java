package com.magnolia.chathelper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

final class MagnoliaUpdater {
	private static final URI LATEST_RELEASE = URI.create(
			"https://api.github.com/repos/9265z/magnolia-opt/releases/latest");
	private static final String RELEASE_ASSET = "MagnoliaOPT.jar";
	private static final long MAX_DOWNLOAD_BYTES = 150L * 1024L * 1024L;
	private static final Gson GSON = new Gson();
	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private final AtomicBoolean busy = new AtomicBoolean();
	private final String currentVersion;
	private volatile State state = State.IDLE;
	private volatile Release release;
	private volatile String detail = "Update check has not run yet";

	MagnoliaUpdater() {
		currentVersion = FabricLoader.getInstance().getModContainer("magnolia_chat_helper")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");
	}

	void check() {
		if (!busy.compareAndSet(false, true)) {
			return;
		}
		state = State.CHECKING;
		detail = "Checking GitHub Releases...";
		CompletableFuture.runAsync(() -> {
			try {
				Release latest = fetchLatestRelease();
				if (ModVersion.compare(latest.version(), currentVersion) > 0) {
					release = latest;
					state = State.AVAILABLE;
					detail = "Magnolia OPT " + latest.version() + " is available";
				} else {
					release = null;
					state = State.CURRENT;
					detail = "Magnolia OPT " + currentVersion + " is current";
				}
			} catch (Exception exception) {
				state = State.ERROR;
				detail = friendlyError(exception);
				MagnoliaChatHelperClient.LOGGER.warn("Magnolia OPT update check failed", exception);
			} finally {
				busy.set(false);
			}
		});
	}

	void installAvailable() {
		Release candidate = release;
		if (candidate == null || state != State.AVAILABLE || !busy.compareAndSet(false, true)) {
			return;
		}
		state = State.DOWNLOADING;
		detail = "Downloading Magnolia OPT " + candidate.version() + "...";
		CompletableFuture.runAsync(() -> {
			Path temporary = null;
			try {
				Path activeJar = activeJar().orElseThrow(() ->
						new IOException("The active Magnolia OPT JAR could not be located"));
				temporary = activeJar.resolveSibling("MagnoliaOPT.jar.download");
				download(candidate, temporary);
				verifyDigest(temporary, candidate.sha256());
				verifyModJar(temporary, candidate.version());
				Files.copy(temporary, activeJar, StandardCopyOption.REPLACE_EXISTING);
				state = State.INSTALLED;
				detail = "Installed " + candidate.version() + "; restart Minecraft to activate it";
			} catch (Exception exception) {
				state = State.ERROR;
				detail = friendlyError(exception);
				MagnoliaChatHelperClient.LOGGER.error("Magnolia OPT update installation failed", exception);
			} finally {
				if (temporary != null) {
					try {
						Files.deleteIfExists(temporary);
					} catch (IOException exception) {
						MagnoliaChatHelperClient.LOGGER.warn("Could not remove updater temporary file {}", temporary, exception);
					}
				}
				busy.set(false);
			}
		});
	}

	String currentVersion() {
		return currentVersion;
	}

	String availableVersion() {
		Release candidate = release;
		return candidate == null ? "none" : candidate.version();
	}

	State state() {
		return state;
	}

	String detail() {
		return detail;
	}

	boolean updateAvailable() {
		return state == State.AVAILABLE;
	}

	private Release fetchLatestRelease() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
				.timeout(Duration.ofSeconds(12))
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.header("User-Agent", "MagnoliaOPT/" + currentVersion)
				.GET()
				.build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 404) {
			throw new IOException("No public Magnolia OPT release is available yet");
		}
		if (response.statusCode() != 200) {
			throw new IOException("GitHub returned HTTP " + response.statusCode());
		}
		JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
		String version = requiredString(root, "tag_name").replaceFirst("^[vV]", "");
		if (!requiresDownloadAsset(version, currentVersion)) {
			return new Release(version, null, "", -1L);
		}
		JsonArray assets = root.getAsJsonArray("assets");
		if (assets == null) {
			throw new IOException("The release has no downloadable assets");
		}
		JsonObject selected = null;
		for (JsonElement element : assets) {
			JsonObject asset = element.getAsJsonObject();
			String name = requiredString(asset, "name");
			if (RELEASE_ASSET.equals(name)) {
				selected = asset;
				break;
			}
			if (selected == null && isReleaseAssetName(name)) {
				selected = asset;
			}
		}
		if (selected == null) {
			throw new IOException("The release is missing a Magnolia OPT JAR");
		}
		long size = selected.has("size") ? selected.get("size").getAsLong() : -1L;
		if (size <= 0 || size > MAX_DOWNLOAD_BYTES) {
			throw new IOException("The release asset has an unsafe size");
		}
		String digest = requiredString(selected, "digest");
		if (!digest.toLowerCase(Locale.ROOT).startsWith("sha256:")) {
			throw new IOException("The release asset has no SHA-256 verification digest");
		}
		return new Release(version, URI.create(requiredString(selected, "browser_download_url")),
				digest.substring("sha256:".length()), size);
	}

	static boolean requiresDownloadAsset(String releaseVersion, String installedVersion) {
		return ModVersion.compare(releaseVersion, installedVersion) > 0;
	}

	static boolean isReleaseAssetName(String name) {
		return name != null && (RELEASE_ASSET.equals(name)
				|| name.matches("(?i)MagnoliaOPT-[0-9][0-9A-Za-z._-]*\\.jar"));
	}

	private void download(Release candidate, Path target) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(candidate.downloadUrl())
				.timeout(Duration.ofSeconds(45))
				.header("Accept", "application/octet-stream")
				.header("User-Agent", "MagnoliaOPT/" + currentVersion)
				.GET()
				.build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			throw new IOException("Download returned HTTP " + response.statusCode());
		}
		Files.deleteIfExists(target);
		try (InputStream input = response.body(); var output = Files.newOutputStream(target)) {
			long written = input.transferTo(output);
			if (written != candidate.size()) {
				throw new IOException("Download size did not match the signed release metadata");
			}
		}
	}

	private static void verifyDigest(Path jar, String expected) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream input = Files.newInputStream(jar)) {
			byte[] buffer = new byte[64 * 1024];
			for (int read; (read = input.read(buffer)) >= 0;) {
				if (read > 0) {
					digest.update(buffer, 0, read);
				}
			}
		}
		String actual = HexFormat.of().formatHex(digest.digest());
		if (!actual.equalsIgnoreCase(expected)) {
			throw new IOException("SHA-256 verification failed; the downloaded file was not installed");
		}
	}

	private static void verifyModJar(Path jar, String expectedVersion) throws IOException {
		try (JarFile archive = new JarFile(jar.toFile())) {
			var entry = archive.getJarEntry("fabric.mod.json");
			if (entry == null) {
				throw new IOException("Downloaded file is not a Fabric mod");
			}
			try (InputStream input = archive.getInputStream(entry)) {
				JsonObject metadata = GSON.fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
				if (!"magnolia_chat_helper".equals(requiredString(metadata, "id"))) {
					throw new IOException("Downloaded file has the wrong mod id");
				}
				if (!expectedVersion.equals(requiredString(metadata, "version"))) {
					throw new IOException("Downloaded file version does not match the release tag");
				}
			}
		}
	}

	private Optional<Path> activeJar() {
		return FabricLoader.getInstance().getModContainer("magnolia_chat_helper")
				.flatMap(container -> container.getOrigin().getPaths().stream().findFirst())
				.map(path -> path.toAbsolutePath().normalize())
				.filter(Files::isRegularFile);
	}

	private static String requiredString(JsonObject object, String member) throws IOException {
		if (object == null || !object.has(member) || object.get(member).isJsonNull()) {
			throw new IOException("Release metadata is missing " + member);
		}
		return object.get(member).getAsString();
	}

	private static String friendlyError(Exception exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return "Update failed: " + exception.getClass().getSimpleName();
		}
		return "Update failed: " + message;
	}

	enum State {
		IDLE,
		CHECKING,
		CURRENT,
		AVAILABLE,
		DOWNLOADING,
		INSTALLED,
		ERROR
	}

	private record Release(String version, URI downloadUrl, String sha256, long size) {
	}
}

package com.cburch.logisim.update;

import com.cburch.logisim.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Public Releases only. Never reads developer credentials or executes downloaded programs. */
public final class GitHubUpdateClient {
  public static final String REPOSITORY = "xuan-xuan7/LoongSim";
  public static final URI RELEASES_PAGE = URI.create("https://github.com/" + REPOSITORY + "/releases");
  private static final URI API = URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases?per_page=100");
  private static final int METADATA_LIMIT = 2 * 1024 * 1024;
  private static final long MAX_INSTALLER_SIZE = 5L * 1024 * 1024 * 1024;
  private static final Set<String> HOSTS = Set.of("api.github.com", "github.com",
      "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com");
  private final ConnectionFactory connections;

  @FunctionalInterface interface ConnectionFactory {
    HttpURLConnection open(URI uri) throws IOException;
  }

  public enum Failure { UNAVAILABLE, RATE_LIMITED, RESPONSE, CHECKSUM, SIZE }

  public static final class UpdateException extends IOException {
    private final Failure failure;
    UpdateException(Failure failure) {
      super("Update request failed: " + failure);
      this.failure = failure;
    }
    public Failure getFailure() { return failure; }
  }

  public GitHubUpdateClient() { this(uri -> (HttpURLConnection) uri.toURL().openConnection()); }
  GitHubUpdateClient(ConnectionFactory connections) { this.connections = connections; }

  public Optional<ReleaseUpdate> check() throws IOException {
    return parseReleases(readText(API, METADATA_LIMIT));
  }

  Optional<ReleaseUpdate> parseReleases(String json) throws IOException {
    try {
      JsonReader reader = new JsonReader(new StringReader(json));
      reader.setStrictness(Strictness.STRICT);
      JsonElement root = JsonParser.parseReader(reader);
      if (!root.isJsonArray() || reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException();
      ReleaseUpdate latest = null;
      for (JsonElement element : root.getAsJsonArray()) {
        JsonObject release = element.getAsJsonObject();
        if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()) continue;
        String tag = release.get("tag_name").getAsString();
        Optional<ReleaseVersion> version = ReleaseVersion.parse(tag);
        if (version.isEmpty()) continue;
        URI page = URI.create(release.get("html_url").getAsString());
        if (!validGitHubUrl(page) || !page.getPath().equals("/" + REPOSITORY + "/releases/tag/" + tag))
          throw new IllegalArgumentException();
        JsonElement body = release.get("body");
        String notes = body == null || body.isJsonNull() ? "" : body.getAsString();
        ReleaseUpdate candidate = new ReleaseUpdate(version.get(), notes, page,
            findInstaller(release.getAsJsonArray("assets"), tag, version.get()));
        if (latest == null || candidate.getVersion().compareTo(latest.getVersion()) > 0) latest = candidate;
      }
      return Optional.ofNullable(latest);
    } catch (IOException | RuntimeException failure) {
      throw new UpdateException(Failure.RESPONSE);
    }
  }

  private ReleaseUpdate.Installer findInstaller(JsonArray assets, String tag, ReleaseVersion version) {
    if (assets == null) return null;
    Pattern namePattern = Pattern.compile("LoongSim-" + Pattern.quote(version.toString())
        + "-master-windows-x64-setup(?:-unsigned|-signed)?\\.exe");
    ReleaseUpdate.Installer selected = null;
    for (JsonElement element : assets) {
      JsonObject asset = element.getAsJsonObject();
      String name = asset.get("name").getAsString();
      if (!namePattern.matcher(name).matches() || !"uploaded".equals(asset.get("state").getAsString())) continue;
      long size = Long.parseLong(asset.get("size").getAsString());
      if (size <= 0 || size > MAX_INSTALLER_SIZE) continue;
      URI url = URI.create(asset.get("browser_download_url").getAsString());
      if (!validAssetUrl(url, tag, name)) continue;
      String digest = asset.has("digest") && !asset.get("digest").isJsonNull() ? asset.get("digest").getAsString() : "";
      String hash = digest.matches("sha256:[a-fA-F0-9]{64}") ? digest.substring(7).toLowerCase(Locale.ROOT) : null;
      URI checksum = null;
      if (hash == null) {
        for (JsonElement item : assets) {
          JsonObject sidecar = item.getAsJsonObject();
          if (!(name + ".sha256").equals(sidecar.get("name").getAsString())
              || !"uploaded".equals(sidecar.get("state").getAsString())) continue;
          URI location = URI.create(sidecar.get("browser_download_url").getAsString());
          if (validAssetUrl(location, tag, name + ".sha256")) checksum = location;
        }
      }
      if (hash == null && checksum == null) continue;
      ReleaseUpdate.Installer candidate = new ReleaseUpdate.Installer(name, url, size, hash, checksum);
      if (selected == null || selected.getName().contains("-unsigned")) selected = candidate;
    }
    return selected;
  }

  private static boolean validGitHubUrl(URI uri) {
    return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
        && uri.getUserInfo() == null && uri.getPort() == -1 && uri.getQuery() == null && uri.getFragment() == null;
  }

  private static boolean validAssetUrl(URI uri, String tag, String name) {
    return validGitHubUrl(uri) && uri.getPath().equals("/" + REPOSITORY + "/releases/download/" + tag + "/" + name);
  }

  private HttpURLConnection open(URI initial) throws IOException {
    URI uri = initial;
    for (int redirects = 0; redirects <= 5; redirects++) {
      if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost())
          || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443))
        throw new UpdateException(Failure.RESPONSE);
      checkCancelled(() -> false);
      HttpURLConnection connection = connections.open(uri);
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(15000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty("User-Agent", "LoongSim/" + Main.LOONGSIM_VERSION_NAME);
      connection.setRequestProperty("Accept", uri.getHost().equals("api.github.com") ? "application/vnd.github+json" : "application/octet-stream");
      if (uri.getHost().equals("api.github.com")) connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
      try {
        int code = connection.getResponseCode();
        if (code == 200) return connection;
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
          String location = connection.getHeaderField("Location");
          if (location == null) throw new UpdateException(Failure.RESPONSE);
          try { uri = uri.resolve(location); }
          catch (IllegalArgumentException e) { throw new UpdateException(Failure.RESPONSE); }
        } else if (code == 404 || code == 401) throw new UpdateException(Failure.UNAVAILABLE);
        else if (code == 403 || code == 429) throw new UpdateException(Failure.RATE_LIMITED);
        else throw new UpdateException(Failure.RESPONSE);
      } catch (IOException | RuntimeException failure) {
        connection.disconnect();
        throw failure;
      }
      connection.disconnect();
    }
    throw new UpdateException(Failure.RESPONSE);
  }

  private String readText(URI uri, int limit) throws IOException {
    HttpURLConnection connection = open(uri);
    try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) {
        checkCancelled(() -> false);
        if (output.size() + count > limit) throw new UpdateException(Failure.RESPONSE);
        output.write(buffer, 0, count);
      }
      return output.toString(StandardCharsets.UTF_8);
    } finally { connection.disconnect(); }
  }

  public Path download(ReleaseUpdate.Installer installer, Path directory,
      BiConsumer<Long, Long> progress, BooleanSupplier cancelled) throws IOException {
    checkCancelled(cancelled);
    String expectedHash = installer.sha256();
    if (expectedHash == null) {
      String checksum = readText(installer.checksumUrl(), 4096).replace("\uFEFF", "").trim();
      String[] fields = checksum.split("\\s+", 2);
      if (fields.length != 2 || !fields[0].matches("[A-Fa-f0-9]{64}")
          || !fields[1].replaceFirst("^\\*", "").equals(installer.getName())) throw new UpdateException(Failure.CHECKSUM);
      expectedHash = fields[0].toLowerCase(Locale.ROOT);
    }
    Path target = directory.resolve(installer.getName());
    if (Files.exists(target)) throw new IOException("Update installer already exists: " + target);
    Path partial = Files.createTempFile(directory, ".loongsim-update-", ".part");
    HttpURLConnection connection = null;
    try {
      connection = open(installer.url());
      long length = connection.getContentLengthLong();
      if (length >= 0 && length != installer.getSize()) throw new UpdateException(Failure.SIZE);
      MessageDigest digest;
      try { digest = MessageDigest.getInstance("SHA-256"); }
      catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
      long received = 0;
      try (InputStream input = connection.getInputStream(); var output = Files.newOutputStream(partial)) {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
          checkCancelled(cancelled);
          received += count;
          if (received > installer.getSize()) throw new UpdateException(Failure.SIZE);
          output.write(buffer, 0, count);
          digest.update(buffer, 0, count);
          progress.accept(received, installer.getSize());
        }
      }
      if (received != installer.getSize()) throw new UpdateException(Failure.SIZE);
      StringBuilder hash = new StringBuilder();
      for (byte b : digest.digest()) hash.append(String.format("%02x", b & 0xff));
      if (!expectedHash.contentEquals(hash)) throw new UpdateException(Failure.CHECKSUM);
      checkCancelled(cancelled);
      try { Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE); }
      catch (AtomicMoveNotSupportedException unsupported) { Files.move(partial, target); }
      return target;
    } finally {
      if (connection != null) connection.disconnect();
      Files.deleteIfExists(partial);
    }
  }

  private static void checkCancelled(BooleanSupplier cancelled) {
    if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CancellationException();
  }
}

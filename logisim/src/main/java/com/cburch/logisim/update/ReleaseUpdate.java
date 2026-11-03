package com.cburch.logisim.update;

import java.net.URI;
import java.util.Optional;

public final class ReleaseUpdate {
  private final ReleaseVersion version;
  private final String notes;
  private final URI page;
  private final Installer installer;

  ReleaseUpdate(ReleaseVersion version, String notes, URI page, Installer installer) {
    this.version = version;
    this.notes = notes;
    this.page = page;
    this.installer = installer;
  }

  public ReleaseVersion getVersion() { return version; }
  public String getNotes() { return notes; }
  public URI getPage() { return page; }
  public Optional<Installer> getInstaller() { return Optional.ofNullable(installer); }

  public static final class Installer {
    private final String name;
    private final URI url;
    private final long size;
    private final String sha256;
    private final URI checksumUrl;

    Installer(String name, URI url, long size, String sha256, URI checksumUrl) {
      this.name = name;
      this.url = url;
      this.size = size;
      this.sha256 = sha256;
      this.checksumUrl = checksumUrl;
    }

    public String getName() { return name; }
    public long getSize() { return size; }
    URI url() { return url; }
    String sha256() { return sha256; }
    URI checksumUrl() { return checksumUrl; }
  }
}

package com.cburch.logisim.update;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Real HTTP streams on loopback; no GitHub account, real installer, or installer execution. */
public class GitHubUpdateClientTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  private static final String TAG = "v1.2.0";
  private static final String NAME = "LoongSim-1.2.0-master-windows-x64-setup-unsigned.exe";
  private static final String DOWNLOAD = "/xuan-xuan7/LoongSim/releases/download/" + TAG + "/" + NAME;
  private final byte[] binary = new byte[180000];
  private final Map<String, Reply> replies = new ConcurrentHashMap<>();
  private final AtomicReference<String> userAgent = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private HttpServer server;
  private GitHubUpdateClient client;

  private static final class Reply {
    final int status;
    final byte[] bytes;
    final String location;
    final boolean chunked;
    Reply(int status, byte[] bytes, String location, boolean chunked) {
      this.status=status;this.bytes=bytes;this.location=location;this.chunked=chunked;
    }
  }

  @Before public void start() throws Exception {
    for (int i=0;i<binary.length;i++) binary[i]=(byte)(i*31);
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/", exchange -> {
      userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      Reply reply=replies.getOrDefault(exchange.getRequestURI().getPath(),new Reply(404,new byte[0],null,false));
      if(reply.location!=null)exchange.getResponseHeaders().add("Location",reply.location);
      exchange.sendResponseHeaders(reply.status,reply.chunked?0:reply.bytes.length);
      try(var output=exchange.getResponseBody()){output.write(reply.bytes);}catch(IOException cancelled){}finally{exchange.close();}
    });
    server.start();
    client=new GitHubUpdateClient(uri -> (HttpURLConnection)new URL("http://127.0.0.1:"
        +server.getAddress().getPort()+uri.getRawPath()+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery())).openConnection());
  }

  @After public void stop(){server.stop(0);}

  private void reply(String path,int status,byte[] bytes){replies.put(path,new Reply(status,bytes,null,false));}
  private void metadata(JsonObject... releases){JsonArray array=new JsonArray();for(var r:releases)array.add(r);reply("/repos/xuan-xuan7/LoongSim/releases",200,array.toString().getBytes(StandardCharsets.UTF_8));}
  private String sha(byte[] bytes) throws Exception {StringBuilder text=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))text.append(String.format("%02x",b&255));return text.toString();}
  private JsonObject asset(String name,String digest) {
    JsonObject asset=new JsonObject();asset.addProperty("name",name);asset.addProperty("size",binary.length);
    asset.addProperty("state","uploaded");asset.addProperty("browser_download_url","https://github.com/xuan-xuan7/LoongSim/releases/download/"+TAG+"/"+name);
    if(digest!=null)asset.addProperty("digest",digest);return asset;
  }
  private JsonObject release(String tag) throws Exception {
    JsonObject release=new JsonObject();release.addProperty("tag_name",tag);release.addProperty("html_url","https://github.com/xuan-xuan7/LoongSim/releases/tag/"+tag);
    release.addProperty("body","更新说明：修复中文标签\n保留电路。<script>plain text</script>");
    release.addProperty("draft",false);release.addProperty("prerelease",false);
    JsonArray assets=new JsonArray();assets.add(asset(NAME,"sha256:"+sha(binary)));release.add("assets",assets);return release;
  }
  private ReleaseUpdate.Installer installer(JsonObject release) throws Exception {metadata(release);return client.check().orElseThrow().getInstaller().orElseThrow();}
  private Path directory() throws Exception{return temporary.newFolder().toPath();}
  private void expectFailure(GitHubUpdateClient.Failure kind,Action action) throws Exception {
    try{action.run();fail("Expected failure "+kind);}catch(GitHubUpdateClient.UpdateException e){assertEquals(kind,e.getFailure());}
  }
  @FunctionalInterface private interface Action {void run() throws Exception;}
  private void empty(Path path) throws Exception{try(var files=Files.list(path)){assertEquals("Partial or unverified installer survived",0,files.count());}}

  @Test public void versionComparisonIsNumericAndRejectsLegacyAndPreviewVersions() {
    assertTrue(ReleaseVersion.parse("v1.10.0").orElseThrow().compareTo(ReleaseVersion.parse("1.9.99").orElseThrow())>0);
    assertEquals(0,ReleaseVersion.parse("1.2.0+build.7").orElseThrow().compareTo(ReleaseVersion.parse("v1.2.0").orElseThrow()));
    for(String text:new String[]{"1.2.0-logisim-only","v1.2.0-rc1","garbage","1.2","01.2.3","1.2.3.4"})assertTrue(text,ReleaseVersion.parse(text).isEmpty());
  }
  @Test public void highestStableVersionWinsRegardlessOfPublishedOrder() throws Exception {
    JsonObject draft=release("v9.0.0");draft.addProperty("draft",true);
    JsonObject preview=release("v8.0.0");preview.addProperty("prerelease",true);
    metadata(release("v1.9.0"),draft,release("v1.10.0"),preview,release("v99.0.0-logisim-only"),release("v1.2.0"));
    assertEquals("1.10.0",client.check().orElseThrow().getVersion().toString());
  }
  @Test public void currentOrOlderReleaseIsNotNewer() throws Exception {
    metadata(release("v1.1.0"));var version=client.check().orElseThrow().getVersion();
    assertTrue(version.compareTo(ReleaseVersion.parse("1.1.0").orElseThrow())<=0);
    assertTrue(version.compareTo(ReleaseVersion.parse("1.2.0").orElseThrow())<0);
  }
  @Test public void emptyRepositoryHasNoStableRelease() throws Exception{metadata();assertTrue(client.check().isEmpty());}
  @Test public void originalUnicodeAndMultilineNotesSurvive() throws Exception{metadata(release(TAG));assertTrue(client.check().orElseThrow().getNotes().contains("中文标签\n"));}
  @Test public void correctInstallerIsDownloadedVerifiedAndNotExecuted() throws Exception {
    var installer=installer(release(TAG));reply(DOWNLOAD,200,binary);Path directory=directory();AtomicBoolean progressed=new AtomicBoolean();
    Path file=client.download(installer,directory,(received,total)->{assertEquals(binary.length,total.longValue());progressed.set(true);},()->false);
    assertArrayEquals(binary,Files.readAllBytes(file));assertTrue(progressed.get());assertEquals(NAME,file.getFileName().toString());
    assertTrue(userAgent.get().startsWith("LoongSim/"));assertNull("Never send developer credentials",authorization.get());
  }
  @Test public void checksumSidecarSupportsReleasesWithoutApiDigest() throws Exception {
    var release=release(TAG);release.getAsJsonArray("assets").get(0).getAsJsonObject().remove("digest");
    release.getAsJsonArray("assets").add(asset(NAME+".sha256",null));
    reply(DOWNLOAD+".sha256",200,(sha(binary)+"  "+NAME+"\n").getBytes(StandardCharsets.UTF_8));reply(DOWNLOAD,200,binary);
    assertArrayEquals(binary,Files.readAllBytes(client.download(installer(release),directory(),(a,b)->{},()->false)));
  }
  @Test public void installersWithoutChecksumAreNotOffered() throws Exception {
    var release=release(TAG);release.getAsJsonArray("assets").get(0).getAsJsonObject().remove("digest");metadata(release);
    assertTrue(client.check().orElseThrow().getInstaller().isEmpty());
  }
  @Test public void wrongChecksumDeletesPartialDownload() throws Exception {
    var release=release(TAG);release.getAsJsonArray("assets").get(0).getAsJsonObject().addProperty("digest","sha256:"+"0".repeat(64));
    var installer=installer(release);reply(DOWNLOAD,200,binary);Path directory=directory();
    expectFailure(GitHubUpdateClient.Failure.CHECKSUM,()->client.download(installer,directory,(a,b)->{},()->false));empty(directory);
  }
  @Test public void wrongChecksumFilenameIsRejected() throws Exception {
    var release=release(TAG);release.getAsJsonArray("assets").get(0).getAsJsonObject().remove("digest");release.getAsJsonArray("assets").add(asset(NAME+".sha256",null));
    var installer=installer(release);reply(DOWNLOAD+".sha256",200,(sha(binary)+"  other.exe").getBytes(StandardCharsets.UTF_8));Path directory=directory();
    expectFailure(GitHubUpdateClient.Failure.CHECKSUM,()->client.download(installer,directory,(a,b)->{},()->false));empty(directory);
  }
  @Test public void truncatedDownloadIsRejectedAndRemoved() throws Exception {
    var installer=installer(release(TAG));reply(DOWNLOAD,200,new byte[12]);Path directory=directory();
    expectFailure(GitHubUpdateClient.Failure.SIZE,()->client.download(installer,directory,(a,b)->{},()->false));empty(directory);
  }
  @Test public void oversizedChunkedDownloadIsRejected() throws Exception {
    var release=release(TAG);release.getAsJsonArray("assets").get(0).getAsJsonObject().addProperty("size",10);
    var installer=installer(release);replies.put(DOWNLOAD,new Reply(200,binary,null,true));Path directory=directory();
    expectFailure(GitHubUpdateClient.Failure.SIZE,()->client.download(installer,directory,(a,b)->{},()->false));empty(directory);
  }
  @Test public void cancellingDuringDownloadLeavesNoInstaller() throws Exception {
    var installer=installer(release(TAG));reply(DOWNLOAD,200,binary);Path directory=directory();AtomicBoolean cancel=new AtomicBoolean();
    try{client.download(installer,directory,(a,b)->cancel.set(true),cancel::get);fail("Cancellation ignored");}catch(CancellationException expected){}empty(directory);
  }
  @Test public void existingFilesAreNeverOverwritten() throws Exception {
    var installer=installer(release(TAG));Path directory=directory();Path existing=directory.resolve(NAME);Files.writeString(existing,"keep");
    try{client.download(installer,directory,(a,b)->{},()->false);fail("Existing file replaced");}catch(IOException expected){}assertEquals("keep",Files.readString(existing));
  }
  @Test public void signedPackageIsPreferredToUnsigned() throws Exception {
    var release=release(TAG);release.getAsJsonArray("assets").add(asset(NAME.replace("-unsigned","-signed"),"sha256:"+sha(binary)));
    assertTrue(installer(release).getName().endsWith("-setup-signed.exe"));
  }
  @Test public void differentRepositoryOrNonHttpsAssetsAreNotOffered() throws Exception {
    for(String url:new String[]{"https://github.com/other/LoongSim/releases/download/"+TAG+"/"+NAME,"http://github.com"+DOWNLOAD,"https://evil.example"+DOWNLOAD}) {
      var release=release(TAG);release.getAsJsonArray("assets").get(0).getAsJsonObject().addProperty("browser_download_url",url);metadata(release);
      assertTrue(client.check().orElseThrow().getInstaller().isEmpty());
    }
  }
  @Test public void legacyOrWrongPlatformAssetIsNotOffered() throws Exception {
    var release=release(TAG);release.getAsJsonArray("assets").get(0).getAsJsonObject().addProperty("name","LoongSim-1.2.0-logisim-only-windows-x64.exe");metadata(release);
    assertTrue(client.check().orElseThrow().getInstaller().isEmpty());
  }
  @Test public void githubHttpsAssetRedirectIsFollowed() throws Exception {
    var installer=installer(release(TAG));replies.put(DOWNLOAD,new Reply(302,new byte[0],"https://release-assets.githubusercontent.com/binary",false));reply("/binary",200,binary);
    assertArrayEquals(binary,Files.readAllBytes(client.download(installer,directory(),(a,b)->{},()->false)));
  }
  @Test public void insecureOrForeignRedirectIsRejected() throws Exception {
    var installer=installer(release(TAG));
    for(String url:new String[]{"http://release-assets.githubusercontent.com/binary","https://example.com/binary"}){
      replies.put(DOWNLOAD,new Reply(302,new byte[0],url,false));Path directory=directory();
      expectFailure(GitHubUpdateClient.Failure.RESPONSE,()->client.download(installer,directory,(a,b)->{},()->false));empty(directory);
    }
  }
  @Test public void privateRepositoryIsReportedAsUnavailable() throws Exception {reply("/repos/xuan-xuan7/LoongSim/releases",404,new byte[0]);expectFailure(GitHubUpdateClient.Failure.UNAVAILABLE,()->client.check());}
  @Test public void offlineConnectionIsNotReportedAsUpToDate() throws Exception {
    GitHubUpdateClient offline=new GitHubUpdateClient(uri->{throw new java.net.ConnectException("offline");});
    try{offline.check();fail("Offline check returned a release result");}catch(java.net.ConnectException expected){}
  }
  @Test public void timeoutRemainsANetworkFailure() throws Exception {
    GitHubUpdateClient timeout=new GitHubUpdateClient(uri->new HttpURLConnection(uri.toURL()) {
      @Override public void connect(){}
      @Override public void disconnect(){}
      @Override public boolean usingProxy(){return false;}
      @Override public int getResponseCode() throws IOException{throw new java.net.SocketTimeoutException("timed out");}
    });
    try{timeout.check();fail("Timed out check returned a release result");}catch(java.net.SocketTimeoutException expected){}
  }
  @Test public void rateLimitsAreDistinctFromNoUpdates() throws Exception {for(int code:new int[]{403,429}){reply("/repos/xuan-xuan7/LoongSim/releases",code,new byte[0]);expectFailure(GitHubUpdateClient.Failure.RATE_LIMITED,()->client.check());}}
  @Test public void malformedJsonCannotBecomeAnUpToDateMessage() throws Exception {for(String json:new String[]{"{}","[broken]","[] trailing","null"}){reply("/repos/xuan-xuan7/LoongSim/releases",200,json.getBytes(StandardCharsets.UTF_8));expectFailure(GitHubUpdateClient.Failure.RESPONSE,()->client.check());}}
  @Test public void metadataSizeIsBounded() throws Exception {reply("/repos/xuan-xuan7/LoongSim/releases",200,new byte[2*1024*1024+1]);expectFailure(GitHubUpdateClient.Failure.RESPONSE,()->client.check());}
  @Test public void wrongReleasePageIsRejected() throws Exception {var release=release(TAG);release.addProperty("html_url","https://example.com/download");metadata(release);expectFailure(GitHubUpdateClient.Failure.RESPONSE,()->client.check());}
}

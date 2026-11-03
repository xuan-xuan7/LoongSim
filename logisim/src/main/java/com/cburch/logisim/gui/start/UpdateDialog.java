package com.cburch.logisim.gui.start;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.Main;
import com.cburch.logisim.update.GitHubUpdateClient;
import com.cburch.logisim.update.ReleaseUpdate;
import com.cburch.logisim.update.ReleaseVersion;
import com.cburch.logisim.util.ApplicationIcon;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

/** Explicit, user-initiated updates. The installer is handed to the user after verification. */
public final class UpdateDialog extends JDialog {
  private static final long serialVersionUID = 1L;
  private static UpdateDialog active;
  private final GitHubUpdateClient client = new GitHubUpdateClient();
  private final JLabel status = new JLabel(S.get("updateChecking"));
  private final JTextArea details = new JTextArea(12, 52);
  private final JProgressBar progress = new JProgressBar(0, 100);
  private final JButton download = new JButton(S.get("updateDownload"));
  private final JButton page = new JButton(S.get("updateReleasePage"));
  private final JButton close = new JButton(S.get("updateCancel"));
  private SwingWorker<?, ?> worker;
  private ReleaseUpdate release;
  private Path downloaded;

  public static void show(Window owner) {
    if (active != null && active.isDisplayable()) { active.toFront(); return; }
    active = new UpdateDialog(owner);
    active.setVisible(true);
    active.check();
  }

  private UpdateDialog(Window owner) {
    super(owner, S.get("updateTitle"), ModalityType.MODELESS);
    ApplicationIcon.attach(this);
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    JPanel content = new JPanel(new BorderLayout(10, 12));
    content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
    JPanel heading = new JPanel(new BorderLayout(0, 8));
    heading.add(new JLabel(S.fmt("updateCurrent", Main.LOONGSIM_VERSION_NAME)), BorderLayout.NORTH);
    heading.add(status, BorderLayout.CENTER);
    content.add(heading, BorderLayout.NORTH);
    details.setEditable(false);
    details.setLineWrap(true);
    details.setWrapStyleWord(true);
    details.setFont(status.getFont());
    JScrollPane scroll = new JScrollPane(details);
    scroll.setPreferredSize(new Dimension(560, 250));
    content.add(scroll, BorderLayout.CENTER);
    JPanel bottom = new JPanel(new BorderLayout(0, 8));
    progress.setIndeterminate(true);
    bottom.add(progress, BorderLayout.NORTH);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    buttons.add(page); buttons.add(download); buttons.add(close);
    bottom.add(buttons, BorderLayout.SOUTH);
    content.add(bottom, BorderLayout.SOUTH);
    setContentPane(content);
    download.setEnabled(false);
    download.addActionListener(e -> { if (downloaded == null) chooseDownload(); else openFolder(); });
    page.addActionListener(e -> browse(release == null ? GitHubUpdateClient.RELEASES_PAGE : release.getPage()));
    close.addActionListener(e -> closeWindow());
    addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent e) { closeWindow(); }
    });
    pack(); setLocationRelativeTo(owner);
  }

  private void check() {
    SwingWorker<Optional<ReleaseUpdate>, Void> task = new SwingWorker<>() {
      @Override protected Optional<ReleaseUpdate> doInBackground() throws Exception { return client.check(); }
      @Override protected void done() {
        if (!isDisplayable()) return;
        progress.setIndeterminate(false); progress.setVisible(false);
        close.setText(S.get("updateClose"));
        try {
          release = get().orElse(null);
          ReleaseVersion current = ReleaseVersion.parse(Main.LOONGSIM_VERSION_NAME).orElseThrow();
          if (release == null) {
            status.setText(S.get("updateNoRelease"));
            details.setText(S.get("updateNoReleaseDetail"));
          } else if (release.getVersion().compareTo(current) <= 0) {
            status.setText(S.get("updateUpToDate"));
            details.setText(S.fmt("updateLatest", release.getVersion()));
          } else {
            status.setText(S.fmt("updateAvailable", release.getVersion()));
            details.setText(release.getNotes().isBlank() ? S.get("updateNoNotes") : release.getNotes());
            boolean supported = System.getProperty("os.name", "").startsWith("Windows")
                && List.of("amd64", "x86_64").contains(System.getProperty("os.arch", "").toLowerCase(Locale.ROOT));
            download.setEnabled(supported && release.getInstaller().isPresent());
            if (!download.isEnabled()) details.append("\n\n" + S.get("updateNoInstaller"));
          }
          details.setCaretPosition(0);
        } catch (CancellationException ignored) {
        } catch (InterruptedException e) { Thread.currentThread().interrupt();
        } catch (ExecutionException | RuntimeException e) { showFailure(e); }
      }
    };
    worker = task; task.execute();
  }

  private void chooseDownload() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(S.get("updateSaveDirectory"));
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    Path home = Path.of(System.getProperty("user.home"));
    Path downloads = home.resolve("Downloads");
    chooser.setCurrentDirectory((Files.isDirectory(downloads) ? downloads : home).toFile());
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
    Path directory;
    try { directory = Files.createTempDirectory(chooser.getSelectedFile().toPath(), "LoongSim-" + release.getVersion() + "-"); }
    catch (IOException | RuntimeException e) { showFailure(e); return; }
    ReleaseUpdate.Installer installer = release.getInstaller().orElseThrow();
    download.setEnabled(false); page.setEnabled(false);
    close.setText(S.get("updateCancel"));
    progress.setVisible(true); progress.setValue(0); progress.setStringPainted(true);
    status.setText(S.get("updateDownloading"));
    SwingWorker<Path, Long> task = new SwingWorker<>() {
      @Override protected Path doInBackground() throws Exception {
        return client.download(installer, directory, (received, total) -> publish(received), this::isCancelled);
      }
      @Override protected void process(List<Long> values) {
        if (!isDisplayable()) return;
        long received = values.get(values.size() - 1);
        progress.setValue((int) (received * 100 / installer.getSize()));
        progress.setString(String.format("%.1f / %.1f MB", received / 1048576.0, installer.getSize() / 1048576.0));
      }
      @Override protected void done() {
        if (!isDisplayable()) return;
        close.setText(S.get("updateClose")); page.setEnabled(true); download.setEnabled(true);
        progress.setVisible(false);
        try {
          downloaded = get();
          status.setText(S.get("updateDownloaded"));
          details.setText(S.fmt("updateInstallInstructions", downloaded.toString()));
          details.setCaretPosition(0);
          download.setText(S.get("updateOpenFolder"));
        } catch (CancellationException ignored) {
        } catch (InterruptedException e) { Thread.currentThread().interrupt();
        } catch (ExecutionException | RuntimeException e) {
          showFailure(e);
          try { Files.deleteIfExists(directory); } catch (IOException ignored) { }
        }
      }
    };
    worker = task; task.execute();
  }

  private void closeWindow() {
    if (worker != null && !worker.isDone()) worker.cancel(true);
    dispose();
    if (active == this) active = null;
  }

  private void showFailure(Exception exception) {
    Throwable cause = exception instanceof ExecutionException ? exception.getCause() : exception;
    String key = "updateErrorNetwork";
    if (cause instanceof GitHubUpdateClient.UpdateException) {
      switch (((GitHubUpdateClient.UpdateException) cause).getFailure()) {
        case UNAVAILABLE: key = "updateErrorUnavailable"; break;
        case RATE_LIMITED: key = "updateErrorRateLimit"; break;
        case CHECKSUM: key = "updateErrorChecksum"; break;
        case SIZE: key = "updateErrorSize"; break;
        default: key = "updateErrorResponse";
      }
    }
    status.setText(S.get("updateFailed")); details.setText(S.get(key)); details.setCaretPosition(0);
  }

  private void browse(URI uri) {
    try {
      if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) throw new IOException();
      Desktop.getDesktop().browse(uri);
    } catch (IOException | RuntimeException e) {
      JOptionPane.showMessageDialog(this, S.fmt("updateOpenLinkManually", uri), S.get("updateTitle"), JOptionPane.INFORMATION_MESSAGE);
    }
  }

  private void openFolder() {
    try {
      if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) throw new IOException();
      Desktop.getDesktop().open(downloaded.getParent().toFile());
    } catch (IOException | RuntimeException e) {
      JOptionPane.showMessageDialog(this, downloaded.toString(), S.get("updateTitle"), JOptionPane.INFORMATION_MESSAGE);
    }
  }
}

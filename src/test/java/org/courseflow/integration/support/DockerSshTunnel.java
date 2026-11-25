package org.courseflow.integration.support;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.nio.file.Path;

public final class DockerSshTunnel implements AutoCloseable {

  private static final int LOCAL_PORT = 23750;
  private static final int SSH_PORT = 22;
  private static final int TIMEOUT_MS = 10_000;

  private final Session session;

  private DockerSshTunnel(Session session) {
    this.session = session;
  }

  public static DockerSshTunnel open() throws Exception {

    String host = System.getenv("SSH_HOST");

    // Local Docker or GitHub Actions
    if (host == null || host.isBlank()) {
      return new DockerSshTunnel(null);
    }

    String user = requireEnvironment("SSH_USER");
    String password = requireEnvironment("SSH_PASSWORD");

    JSch jsch = new JSch();

    jsch.setKnownHosts(Path.of(System.getProperty("user.home"), ".ssh", "known_hosts").toString());

    Session session = jsch.getSession(user, host, SSH_PORT);

    session.setPassword(password);
    session.setConfig("StrictHostKeyChecking", "yes");

    try {
      session.connect(TIMEOUT_MS);

      session.setSocketForwardingL(
          "127.0.0.1", LOCAL_PORT, "/var/run/docker.sock", null, TIMEOUT_MS);

      return new DockerSshTunnel(session);

    } catch (Exception exception) {
      session.disconnect();
      throw exception;
    }
  }

  private static String requireEnvironment(String name) {

    String value = System.getenv(name);

    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing environment variable: " + name);
    }

    return value;
  }

  @Override
  public void close() {
    if (session != null) {
      session.disconnect();
    }
  }
}

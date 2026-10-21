package br.com.imarui.ima.authentication.core;

import static org.junit.jupiter.api.Assertions.*;

import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.authentication.infra.mail.SmtpChallengeMail;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.*;

class SmtpChallengeMailTest {
  @Test
  void smtpAdapterDeliversTypedIiAmTemplateToIsolatedLocalServer() throws Exception {
    try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      server.setSoTimeout(10000);
      var executor = Executors.newSingleThreadExecutor();
      try {
        Future<String> message =
            executor.submit(
                () -> {
                  try (var socket = server.accept();
                      var input =
                          new BufferedReader(
                              new InputStreamReader(
                                  socket.getInputStream(), StandardCharsets.UTF_8));
                      var output =
                          new PrintWriter(
                              new OutputStreamWriter(
                                  socket.getOutputStream(), StandardCharsets.UTF_8),
                              true)) {
                    output.print("220 local.test SMTP\r\n");
                    output.flush();
                    boolean data = false;
                    var content = new StringBuilder();
                    String line;
                    while ((line = input.readLine()) != null) {
                      if (data) {
                        if (line.equals(".")) {
                          data = false;
                          output.print("250 accepted\r\n");
                          output.flush();
                        } else content.append(line).append("\r\n");
                      } else if (line.startsWith("EHLO") || line.startsWith("HELO")) {
                        output.print("250 local.test\r\n");
                        output.flush();
                      } else if (line.equals("DATA")) {
                        data = true;
                        output.print("354 data\r\n");
                        output.flush();
                      } else if (line.equals("QUIT")) {
                        output.print("221 bye\r\n");
                        output.flush();
                        break;
                      } else {
                        output.print("250 ok\r\n");
                        output.flush();
                      }
                    }
                    return content.toString();
                  }
                });
        var sender = new JavaMailSenderImpl();
        sender.setHost(InetAddress.getLoopbackAddress().getHostAddress());
        sender.setPort(server.getLocalPort());
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "5000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "5000");
        var beans = new StaticListableBeanFactory();
        beans.addBean("smtp", sender);
        var adapter =
            new SmtpChallengeMail(
                beans.getBeanProvider(JavaMailSender.class),
                "https://frontend.example.test",
                "iiam@example.test");
        adapter.send(
            "recipient@example.test", "local-test-single-use-secret", Purpose.ACCOUNT_ACTIVATION);
        var mime =
            new jakarta.mail.internet.MimeMessage(
                jakarta.mail.Session.getInstance(new Properties()),
                new ByteArrayInputStream(
                    message.get(10, TimeUnit.SECONDS).getBytes(StandardCharsets.UTF_8)));
        assertTrue(mime.getSubject().contains("IIAM"));
        assertTrue(mime.getSubject().contains("Primeiro acesso"));
        String body = (String) mime.getContent();
        assertTrue(
            body.contains(
                "https://frontend.example.test/first-access#token=local-test-single-use-secret"));
        assertFalse(body.contains("Jeep"));
        var unsafe =
            new SmtpChallengeMail(
                beans.getBeanProvider(JavaMailSender.class),
                "http://frontend.example.test",
                "iiam@example.test");
        assertThrows(
            IllegalStateException.class,
            () -> unsafe.send("recipient@example.test", "not-sent", Purpose.PASSWORD_RESET));
      } finally {
        executor.shutdownNow();
      }
    }
  }
}

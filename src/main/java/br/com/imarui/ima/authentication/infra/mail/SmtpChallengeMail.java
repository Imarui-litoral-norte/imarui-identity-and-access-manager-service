package br.com.imarui.ima.authentication.infra.mail;

import br.com.imarui.ima.authentication.core.domain.model.AuthenticationChallenge.Purpose;
import br.com.imarui.ima.authentication.core.port.*;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@Profile("!test & !dev")
public class SmtpChallengeMail implements ChallengeMail, NotificationPort {
  private final ObjectProvider<JavaMailSender> mail;
  private final String baseUrl;
  private final String from;

  public SmtpChallengeMail(
      ObjectProvider<JavaMailSender> mail,
      @Value("${security.auth.frontend-url:}") String baseUrl,
      @Value("${spring.mail.username:}") String from) {
    this.mail = mail;
    this.baseUrl = baseUrl;
    this.from = from;
  }

  public void send(String contact, String token, Purpose purpose) {
    var uri = URI.create(baseUrl);
    if (!"https".equals(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null
        || uri.getQuery() != null)
      throw new IllegalStateException("Configure an HTTPS frontend URL.");
    String url =
        baseUrl.replaceAll("/$", "")
            + (purpose == Purpose.ACCOUNT_ACTIVATION ? "/first-access" : "/reset-password")
            + "#token="
            + token;
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(contact);
    message.setSubject(
        "IIAM — "
            + (purpose == Purpose.ACCOUNT_ACTIVATION
                ? "Primeiro acesso"
                : "Recuperação de acesso"));
    message.setText("Conclua a solicitação pelo link de uso único: " + url);
    mail.getObject().send(message);
  }

  public void sendPasswordResetLink(String email, String link) {
    var base = URI.create(baseUrl);
    var target = URI.create(link);
    if (!"https".equals(target.getScheme())
        || !Objects.equals(base.getHost(), target.getHost())
        || target.getUserInfo() != null)
      throw new IllegalArgumentException("Untrusted notification URL.");
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject("IIAM — Recuperação de acesso");
    message.setText("Conclua a solicitação pelo link de uso único: " + link);
    mail.getObject().send(message);
  }
}

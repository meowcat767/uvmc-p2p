package unix.verse.p2p.net.quic;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

record PeerCertificate(PrivateKey privateKey, X509Certificate certificate) {
  private static final Duration BACKDATE = Duration.ofDays(1);
  private static final Duration VALIDITY = Duration.ofDays(365);

  static PeerCertificate generate(String commonName) throws GeneralSecurityException {
    SecureRandom random = new SecureRandom();
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"), random);
    KeyPair keyPair = generator.generateKeyPair();

    Instant now = Instant.now();
    X500Name subject = new X500Name("CN=" + commonName);
    JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
        subject,
        new BigInteger(63, random).add(BigInteger.ONE),
        Date.from(now.minus(BACKDATE)),
        Date.from(now.plus(VALIDITY)),
        subject,
        keyPair.getPublic());

    try {
      ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
          .build(keyPair.getPrivate());
      X509Certificate certificate = new JcaX509CertificateConverter()
          .getCertificate(builder.build(signer));
      return new PeerCertificate(keyPair.getPrivate(), certificate);
    } catch (OperatorCreationException e) {
      throw new GeneralSecurityException(e);
    }
  }
}

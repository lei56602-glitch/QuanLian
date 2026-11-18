package com.quanlian.app;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** Process-local RSA-2048 TLS identity. Private key never enters AndroidKeyStore or disk. */
final class InMemoryTlsIdentity {
    private static final String ALIAS = "QuanLian-RSA-2048-MEMORY";
    private static final Provider BC = new org.bouncycastle.jce.provider.BouncyCastleProvider();
    private static volatile InMemoryTlsIdentity INSTANCE;

    final KeyPair keyPair;
    final X509Certificate certificate;
    final X509ExtendedKeyManager keyManager;

    private InMemoryTlsIdentity() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        keyPair = generator.generateKeyPair();

        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 5L * 60L * 1000L);
        Date notAfter = new Date(now + 365L * 24L * 60L * 60L * 1000L);
        X500Name subject = new X500Name("CN=QuanLian Device");
        BigInteger serial = new BigInteger(159, new SecureRandom()).add(BigInteger.ONE);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true,
            new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        builder.addExtension(Extension.extendedKeyUsage, false,
            new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BC).build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        certificate = new JcaX509CertificateConverter().setProvider(BC).getCertificate(holder);
        certificate.checkValidity(new Date());
        certificate.verify(keyPair.getPublic());

        keyManager = new X509ExtendedKeyManager() {
            @Override public String[] getClientAliases(String keyType, Principal[] issuers) { return null; }
            @Override public String chooseClientAlias(String[] keyType, Principal[] issuers, java.net.Socket socket) { return null; }
            @Override public String[] getServerAliases(String keyType, Principal[] issuers) {
                return isRsa(keyType) ? new String[]{ALIAS} : null;
            }
            @Override public String chooseServerAlias(String keyType, Principal[] issuers, java.net.Socket socket) {
                return isRsa(keyType) ? ALIAS : null;
            }
            @Override public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
                return isRsa(keyType) ? ALIAS : null;
            }
            @Override public X509Certificate[] getCertificateChain(String alias) {
                return ALIAS.equals(alias) ? new X509Certificate[]{certificate} : null;
            }
            @Override public PrivateKey getPrivateKey(String alias) {
                return ALIAS.equals(alias) ? keyPair.getPrivate() : null;
            }
            private boolean isRsa(String keyType) {
                return keyType != null && keyType.toUpperCase(java.util.Locale.ROOT).contains("RSA");
            }
        };
        android.util.Log.i("QuanLianTLS", "IN_MEMORY_IDENTITY_READY alias=" + ALIAS
            + ", keyAlgorithm=" + keyPair.getPrivate().getAlgorithm()
            + ", keyBits=2048, certificateSignature=" + certificate.getSigAlgName()
            + ", subject=" + certificate.getSubjectX500Principal());
    }

    static InMemoryTlsIdentity get() throws Exception {
        InMemoryTlsIdentity value = INSTANCE;
        if (value == null) {
            synchronized (InMemoryTlsIdentity.class) {
                value = INSTANCE;
                if (value == null) INSTANCE = value = new InMemoryTlsIdentity();
            }
        }
        return value;
    }
}

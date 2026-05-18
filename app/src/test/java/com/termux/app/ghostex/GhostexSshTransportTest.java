package com.termux.app.ghostex;

import net.schmizz.sshj.Config;
import net.schmizz.sshj.common.Factory;
import net.schmizz.sshj.transport.kex.KeyExchange;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Test;

import java.security.Security;
import java.util.Locale;

public final class GhostexSshTransportTest {

    /*
    CDXC:AndroidSshTransport 2026-05-18-03:31:
    App-owned SSH must avoid SSHJ's Curve25519 KEX on Android because the active
    BouncyCastle provider may not expose X25519, causing reconnect to fail
    before macOS OpenSSH can negotiate another supported key exchange.

    CDXC:AndroidSshTransport 2026-05-18-04:04:
    App-owned SSH must replace Android's platform `BC` provider with the
    bundled BouncyCastle provider before SSHJ negotiates ECDH, because the
    platform provider can reject `EC` even after Curve25519 is disabled.
    */
    @Test
    public void sshConfigExcludesCurve25519KeyExchangeFactories() {
        Config config = GhostexSshTransport.createSshConfig();

        Assert.assertFalse(config.getKeyExchangeFactories().isEmpty());
        for (Factory.Named<KeyExchange> factory : config.getKeyExchangeFactories()) {
            Assert.assertFalse(factory.getName(),
                factory.getName().toLowerCase(Locale.ROOT).contains("curve25519"));
        }
    }

    @Test
    public void sshTransportInstallsBundledBouncyCastleProvider() {
        GhostexSshTransport.ensureBundledBouncyCastleProvider();

        Assert.assertTrue(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            instanceof BouncyCastleProvider);
    }

}

package com.quanlian.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

/** Per-install TLS identity. Peers pin the SHA-256 fingerprint advertised during discovery. */
public final class TlsIdentity {
    private static final String ALIAS="QuanLian-TLS-Identity";
    public static final String PREFS="quanlian_diagnostics";
    public static final String KEY_PLAIN="plain_mode";
    public static boolean plainMode(Context c){return BuildConfig.DEBUG && c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(KEY_PLAIN,false);}
    public static String plainFingerprint(){return "0".repeat(64);}
    private TlsIdentity(){}
    private static synchronized KeyStore keyStore() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyPairGenerator gen=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
            gen.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setCertificateSubject(new X500Principal("CN=QuanLian Device"))
                .setCertificateSerialNumber(new java.math.BigInteger(64,new SecureRandom()))
                .setCertificateNotBefore(new Date(System.currentTimeMillis()-86400000L))
                .setCertificateNotAfter(new Date(System.currentTimeMillis()+10L*365*86400000L)).build());
            gen.generateKeyPair();
        }
        return ks;
    }
    public static String fingerprint() throws Exception {
        java.security.cert.Certificate cert=keyStore().getCertificate(ALIAS);
        if(cert==null)throw new GeneralSecurityException("TLS device certificate unavailable");
        return hex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
    }
    private static SSLContext context(X509TrustManager trust) throws Exception {
        KeyStore ks=keyStore();KeyManagerFactory kmf=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());kmf.init(ks,null);
        TrustManager[] managers=trust==null?null:new TrustManager[]{trust};
        SSLContext ctx=SSLContext.getInstance("TLS");ctx.init(kmf.getKeyManagers(),managers,new SecureRandom());
        android.util.Log.d("QuanLianTLS","SSLContext algorithm="+ctx.getProtocol()+", provider="+ctx.getProvider().getName()+", enabled candidates=TLSv1.3,TLSv1.2");
        return ctx;
    }
    public static ServerSocket serverSocket(Context context,int port) throws Exception {
        if(plainMode(context)){
            ServerSocket plain=new ServerSocket();plain.setReuseAddress(true);plain.bind(new InetSocketAddress("0.0.0.0",port));
            android.util.Log.w("QuanLianPlain","DIAGNOSTIC PLAIN MODE: listening on 0.0.0.0:"+plain.getLocalPort()+" WITHOUT TLS");
            return plain;
        }
        SSLServerSocket server=(SSLServerSocket)context(null).getServerSocketFactory().createServerSocket();
        server.setReuseAddress(true);server.bind(new InetSocketAddress("0.0.0.0",port));
        enableModern(server);return server;
    }
    /**
     * Bind an unconnected TCP socket to the Android Network whose route reaches
     * the peer. If Android does not expose that route (common on SoftAP hosts),
     * bind to the local IPv4 address on the interface sharing the peer subnet.
     * Never silently fall back to the mobile-data default route.
     */
    private static void bindToPeerNetwork(Context context, Socket socket, InetAddress peer) throws Exception {
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network best=null;
        int bestScore=Integer.MIN_VALUE;
        if(cm!=null){
            Network[] networks=cm.getAllNetworks();
            for(Network network:networks){
                LinkProperties lp=cm.getLinkProperties(network);
                if(lp==null)continue;
                NetworkCapabilities caps=cm.getNetworkCapabilities(network);
                for(RouteInfo route:lp.getRoutes()){
                    if(route.matches(peer)){
                        int prefix=route.getDestination()==null?0:route.getDestination().getPrefixLength();
                        int score=prefix*10+(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?5:0);
                        if(score>bestScore){best=network;bestScore=score;}
                    }
                }
            }
        }
        if(best!=null){
            best.bindSocket(socket);
            android.util.Log.i("QuanLianNetwork","Bound TCP socket to Android Network for peer "+peer.getHostAddress());
            return;
        }
        // SoftAP owner devices may expose the AP interface but not a Network route.
        Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
        while(interfaces!=null&&interfaces.hasMoreElements()){
            NetworkInterface ni=interfaces.nextElement();
            try{if(!ni.isUp()||ni.isLoopback())continue;}catch(Exception e){continue;}
            for(InterfaceAddress entry:ni.getInterfaceAddresses()){
                InetAddress local=entry.getAddress();
                if(!(local instanceof Inet4Address)||!(peer instanceof Inet4Address))continue;
                int prefix=entry.getNetworkPrefixLength();
                if(prefix<1||prefix>32)continue;
                byte[] a=local.getAddress(),b=peer.getAddress();
                boolean same=true;
                for(int bit=0;bit<prefix;bit++){
                    int mask=1<<(7-(bit%8));
                    if((a[bit/8]&mask)!=(b[bit/8]&mask)){same=false;break;}
                }
                if(same){
                    socket.bind(new InetSocketAddress(local,0));
                    android.util.Log.i("QuanLianNetwork","Bound TCP socket to "+ni.getName()+" / "+local.getHostAddress()+" for peer "+peer.getHostAddress());
                    return;
                }
            }
        }
        throw new java.io.IOException("找不到通往对方 "+peer.getHostAddress()+" 的局域网接口，已阻止错误地走移动数据");
    }

    public static Socket connect(Context context,String host,int port,String expectedFingerprint) throws Exception {
        InetAddress peer=InetAddress.getByName(host);
        android.util.Log.d("QuanLianSocket","CONNECT begin target="+peer.getHostAddress()+":"+port+", plain="+plainMode(context));
        if(plainMode(context)){
            Socket plain=new Socket();
            try{bindToPeerNetwork(context,plain,peer);plain.connect(new InetSocketAddress(peer,port),8000);plain.setSoTimeout(12000);
                android.util.Log.w("QuanLianPlain","Connected WITHOUT TLS target="+peer.getHostAddress()+":"+port+", local="+plain.getLocalAddress().getHostAddress());
                return plain;
            }catch(Exception e){android.util.Log.e("QuanLianSocket","Plain TCP connect failed target="+peer.getHostAddress()+":"+port,e);try{plain.close();}catch(Exception ignored){}throw e;}
        }
        if(expectedFingerprint==null||expectedFingerprint.length()!=64)throw new SSLPeerUnverifiedException("缺少设备证书指纹");
        final String expected=expectedFingerprint.replace(":","").toLowerCase(Locale.ROOT);
        X509TrustManager selectedTrust;
        if(BuildConfig.DEBUG){
            // Diagnostic build only: accept any non-empty peer certificate chain so
            // hotspot connectivity can be isolated from first-use pinning problems.
            // Release builds always retain strict SHA-256 certificate pinning below.
            selectedTrust=new X509TrustManager(){
                public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
                public void checkClientTrusted(X509Certificate[] chain,String authType)throws CertificateException{throw new CertificateException("Client authentication not configured");}
                public void checkServerTrusted(X509Certificate[] chain,String authType)throws CertificateException{
                    if(chain==null||chain.length==0)throw new CertificateException("Empty certificate chain");
                    android.util.Log.w("QuanLianTLS","DEBUG BUILD ONLY: server certificate pin check bypassed for hotspot diagnosis");
                }
            };
        }else{
            selectedTrust=new X509TrustManager(){
                public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
                public void checkClientTrusted(X509Certificate[] chain,String authType)throws CertificateException{throw new CertificateException("Client authentication not configured");}
                public void checkServerTrusted(X509Certificate[] chain,String authType)throws CertificateException{
                    if(chain==null||chain.length==0)throw new CertificateException("Empty certificate chain");
                    try{String actual=hex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()));if(!actual.equals(expected))throw new CertificateException("设备证书指纹不匹配");}
                    catch(GeneralSecurityException e){throw new CertificateException(e);}
                }
            };
        }
        SSLContext sslContext=context(selectedTrust);
        SSLSocket socket=(SSLSocket)sslContext.getSocketFactory().createSocket();
        try{
            android.util.Log.d("QuanLianSocket","TLS socket created; target="+peer.getHostAddress()+":"+port);
            bindToPeerNetwork(context,socket,peer);
            android.util.Log.d("QuanLianSocket","TCP connect starting; target="+peer.getHostAddress()+":"+port);
            socket.connect(new InetSocketAddress(peer,port),8000);
            android.util.Log.d("QuanLianSocket","TCP connected; target="+peer.getHostAddress()+":"+port+", local="+socket.getLocalAddress().getHostAddress()+", SSLContext="+sslContext.getProtocol());
            socket.setSoTimeout(12000);enableModern(socket);
            android.util.Log.d("QuanLianTLS","Handshake starting; target="+peer.getHostAddress()+":"+port+", enabledProtocols="+Arrays.toString(socket.getEnabledProtocols()));
            socket.startHandshake();
            android.util.Log.i("QuanLianTLS","Handshake success; target="+peer.getHostAddress()+":"+port+", negotiated="+socket.getSession().getProtocol()+", cipher="+socket.getSession().getCipherSuite());
            return socket;
        }catch(Exception e){
            android.util.Log.e("QuanLianTLS","TCP/TLS failure; target="+peer.getHostAddress()+":"+port+", SSLContext="+sslContext.getProtocol()+", exception="+e.getClass().getName()+": "+e.getMessage(),e);
            try{socket.close();}catch(Exception ignored){}throw e;
        }
    }
    /** Used only for first-use enrollment; caller must present the fingerprint and obtain user approval. */
    public static String inspectFingerprint(Context context,String host,int port) throws Exception {
        if(plainMode(context))return plainFingerprint();
        X509TrustManager trustAll=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] c,String a){}
            public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{if(c==null||c.length==0)throw new CertificateException("Missing peer certificate");}
        };
        SSLContext ctx=SSLContext.getInstance("TLS");ctx.init(null,new TrustManager[]{trustAll},new SecureRandom());
        try(SSLSocket socket=(SSLSocket)ctx.getSocketFactory().createSocket()){
            InetAddress peer=InetAddress.getByName(host);bindToPeerNetwork(context,socket,peer);socket.connect(new InetSocketAddress(peer,port),2500);socket.setSoTimeout(4000);enableModern(socket);socket.startHandshake();
            java.security.cert.Certificate[] chain=socket.getSession().getPeerCertificates();return hex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()));
        }
    }
    private static void enableModern(SSLSocket s){ArrayList<String> p=new ArrayList<>();for(String v:s.getSupportedProtocols())if(v.equals("TLSv1.3")||v.equals("TLSv1.2"))p.add(v);s.setEnabledProtocols(p.toArray(new String[0]));}
    private static void enableModern(SSLServerSocket s){ArrayList<String> p=new ArrayList<>();for(String v:s.getSupportedProtocols())if(v.equals("TLSv1.3")||v.equals("TLSv1.2"))p.add(v);s.setEnabledProtocols(p.toArray(new String[0]));}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}
}

package net.hockeyapp.android.utils;

import android.content.Context;

import net.hockeyapp.android.R;

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;


/**
 * <h3>Description</h3>
 * 
 * {@link HttpClient} manager class
 * 
 * <h3>License</h3>
 * 
 * <pre>
 * Copyright (c) 2011-2014 Bit Stadium GmbH
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * </pre>
 * 
 * @author Bogdan Nistor
 */
public class ConnectionManager {
  private HttpClient httpClient;
  private static ConnectionManager INSTANCE;
  
  /** Private constructor prevents instantiation from other classes */
  private ConnectionManager(Context context) {
    /** Sets up parameters */
    HttpParams params = new BasicHttpParams();
    HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
    HttpProtocolParams.setContentCharset(params, "utf-8");
    params.setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);
    params.setParameter(CoreProtocolPNames.USER_AGENT, "HockeySDK/Android");
  
    //registers schemes for both http and https
    SchemeRegistry registry = new SchemeRegistry();
    registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
//    final SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
//    sslSocketFactory.setHostnameVerifier(SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    registry.register(new Scheme("https", createAdditionalCertsSSLSocketFactory(context), 443));
  
    ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(params, registry);
    httpClient = new DefaultHttpClient(manager, params);
  }


//  /**
//  * ConnectionManagerHolder is loaded on the first execution of ConnectionManager.getInstance()
//  * or the first access to ConnectionManagerHolder.INSTANCE, not before.
//  */
//  private static class ConnectionManagerHolder {
//    public static final ConnectionManager INSTANCE = new ConnectionManager();
//  }

  public static ConnectionManager getInstance(Context context) {
    if (INSTANCE == null) {
        INSTANCE = new ConnectionManager(context);
    }

    return INSTANCE;
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }

    protected SSLSocketFactory createAdditionalCertsSSLSocketFactory(Context context) {
        try {
            final KeyStore ks = KeyStore.getInstance("BKS");

            // the bks file we generated above
            final InputStream in = context.getResources().openRawResource(R.raw.hockeyapp);
            try {
                // don't forget to put the password used above in strings.xml/mystore_password
                ks.load(in, "ez24get".toCharArray());
            } finally {
                in.close();
            }

            return new AdditionalKeyStoresSSLSocketFactory(ks);

        } catch( Exception e ) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Allows you to trust certificates from additional KeyStores in addition to
     * the default KeyStore
     */
    public class AdditionalKeyStoresSSLSocketFactory extends SSLSocketFactory {
        protected SSLContext sslContext = SSLContext.getInstance("TLS");

        public AdditionalKeyStoresSSLSocketFactory(KeyStore keyStore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
            super(null, null, null, null, null, null);
            sslContext.init(null, new TrustManager[]{new AdditionalKeyStoresTrustManager(keyStore)}, null);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }

        /**
         * Based on http://download.oracle.com/javase/1.5.0/docs/guide/security/jsse/JSSERefGuide.html#X509TrustManager
         */
        public class AdditionalKeyStoresTrustManager implements X509TrustManager {

            protected ArrayList<X509TrustManager> x509TrustManagers = new ArrayList<X509TrustManager>();


            protected AdditionalKeyStoresTrustManager(KeyStore... additionalkeyStores) {
                final ArrayList<TrustManagerFactory> factories = new ArrayList<TrustManagerFactory>();

                try {
                    // The default Trustmanager with default keystore
                    final TrustManagerFactory original = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    original.init((KeyStore) null);
                    factories.add(original);

                    for( KeyStore keyStore : additionalkeyStores ) {
                        final TrustManagerFactory additionalCerts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                        additionalCerts.init(keyStore);
                        factories.add(additionalCerts);
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }



            /*
             * Iterate over the returned trustmanagers, and hold on
             * to any that are X509TrustManagers
             */
                for (TrustManagerFactory tmf : factories)
                    for( TrustManager tm : tmf.getTrustManagers() )
                        if (tm instanceof X509TrustManager)
                            x509TrustManagers.add( (X509TrustManager)tm );


                if( x509TrustManagers.size()==0 )
                    throw new RuntimeException("Couldn't find any X509TrustManagers");

            }

            /*
             * Delegate to the default trust manager.
             */
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                final X509TrustManager defaultX509TrustManager = x509TrustManagers.get(0);
                defaultX509TrustManager.checkClientTrusted(chain, authType);
            }

            /*
             * Loop over the trustmanagers until we find one that accepts our server
             */
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                for( X509TrustManager tm : x509TrustManagers ) {
                    try {
                        tm.checkServerTrusted(chain,authType);
                        return;
                    } catch( CertificateException e ) {
                        // ignore
                    }
                }
                throw new CertificateException();
            }

            public X509Certificate[] getAcceptedIssuers() {
                final ArrayList<X509Certificate> list = new ArrayList<X509Certificate>();
                for( X509TrustManager tm : x509TrustManagers )
                    list.addAll(Arrays.asList(tm.getAcceptedIssuers()));
                return list.toArray(new X509Certificate[list.size()]);
            }
        }

    }
}

package eu.paulburton.fitscales.sync;

import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.Api;
import org.scribe.builder.api.DefaultApi10a;
import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import eu.paulburton.fitscales.FitscalesApplication;
import eu.paulburton.fitscales.Prefs;
import eu.paulburton.fitscales.SyncService;

public class OAuthSyncService extends SyncService
{
    private static final String TAG = "OAuthSyncService";
    private static final boolean DEBUG = false;

    private static final String KEY_TOKEN = "_oa_token";
    private static final String KEY_SECRET = "_oa_secret";

    private AuthThread authThread;
    private Listener listener;
    private Class<? extends Api> oaApiClass;
    protected OAuthService oaService;
    protected Token oaToken;
    protected String authRedirectCodeParam = "code";

    protected OAuthSyncService(String name, String prefName)
    {
        super(name, prefName);
    }

    protected void setupOAuth(String key, String secret, Class<? extends Api> apiClass)
    {
        oaApiClass = apiClass;
        oaService = new ServiceBuilder().provider(apiClass).apiKey(key).apiSecret(secret)
                .callback("http://oauth.localhost/").build();
    }

    @Override
    public void load()
    {
        super.load();

        String token = FitscalesApplication.inst.prefs.getString(prefName + KEY_TOKEN, null);
        String secret = FitscalesApplication.inst.prefs.getString(prefName + KEY_SECRET, null);

        if (token != null && secret != null) {
            try {
                oaToken = new Token(token, secret);
                enabled = true;
            } catch (Exception ex) {
                if (DEBUG)
                    Log.e(TAG, "Failed to create oaToken", ex);
            }
        }
    }

    public void setListener(Listener l)
    {
        this.listener = l;
    }

    @Override
    public void connect()
    {
        synchronized (this) {
            if (oaToken != null || authThread != null)
                return;

            authThread = new AuthThread(this);
            authThread.start();
        }
    }

    @Override
    public void disconnect()
    {
        if (authThread != null) {
            authThread.cancel();
            while (true) {
                try {
                    authThread.join();
                    break;
                } catch (InterruptedException ex) {
                    /* loop, try again */
                }
            }
            authThread = null;
        }

        oaToken = null;
        enabled = false;

        SharedPreferences.Editor edit = FitscalesApplication.inst.prefs.edit();
        edit.remove(prefName + KEY_TOKEN);
        edit.remove(prefName + KEY_SECRET);
        Prefs.save(edit);
    }

    @Override
    public boolean isConnecting()
    {
        synchronized (this) {
            return authThread != null;
        }
    }

    // Runs on AuthThread post OAuth token retrieval
    protected void postOAuth()
    {
    }

    public void setOAuthPageResult(String url)
    {
        if (authThread == null) {
            if (DEBUG)
                Log.e(TAG, "setOAuthPageResult with no authThread?!?");
            return;
        }

        synchronized (authThread) {
            authThread.resultUrl = url;
            authThread.notifyAll();
        }
    }

    private class AuthThread extends Thread
    {
        private OAuthSyncService svc;
        private boolean canceled;
        private String resultUrl;

        public AuthThread(OAuthSyncService svc)
        {
            this.svc = svc;
        }

        public void cancel()
        {
            canceled = true;
            interrupt();
        }

        @Override
        public void run()
        {
            try {
                auth();
            } catch (Exception ex) {
                if (DEBUG)
                    Log.e(TAG, "Auth error", ex);
            }

            synchronized (svc) {
                svc.authThread = null;
                svc.enabled = svc.oaToken != null;
            }
            svc.listener.oauthDone(svc);
        }

        private void auth()
        {
            Token reqToken = null;
            if (DefaultApi10a.class.isAssignableFrom(svc.oaApiClass)) {
                if (DEBUG)
                    Log.d(TAG, "OAuth 1.0a based API, getting request token");
                reqToken = svc.oaService.getRequestToken();
            }
            if (DEBUG)
                Log.d(TAG, "Using request token " + reqToken);

            if (canceled)
                return;

            String authUrl = svc.oaService.getAuthorizationUrl(reqToken);
            if (DEBUG)
                Log.d(TAG, "Auth URL " + authUrl);

            if (canceled)
                return;

            svc.listener.oauthShowPage(svc, authUrl);
            String authCode = null;

            while (authCode == null && !canceled) {
                synchronized (this) {
                    if (resultUrl == null) {
                        try {
                            this.wait();
                        } catch (InterruptedException ex) {
                            continue;
                        }
                    }
                }

                // Shouldn't happen, in theory
                if (resultUrl == null)
                    continue;

                try {
                    Uri uri = Uri.parse(resultUrl);
                    authCode = uri.getQueryParameter(authRedirectCodeParam);
                    break;
                } catch (Exception ex) {
                    if (DEBUG)
                        Log.e(TAG, "Failed to parse auth code", ex);
                    return;
                }
            }
            if (DEBUG)
                Log.d(TAG, "Got auth code " + authCode);

            if (canceled)
                return;

            Verifier verifier = new Verifier(authCode);
            Token accessToken = svc.oaService.getAccessToken(reqToken, verifier);
            if (DEBUG)
                Log.d(TAG, "Got access token " + accessToken);

            if (canceled)
                return;

            SharedPreferences.Editor edit = FitscalesApplication.inst.prefs.edit();
            edit.putString(prefName + KEY_TOKEN, accessToken.getToken());
            edit.putString(prefName + KEY_SECRET, accessToken.getSecret());
            Prefs.save(edit);

            synchronized (this) {
                svc.oaToken = accessToken;
            }
            svc.postOAuth();
            svc.save();
        }
    }

    public interface Listener
    {
        public void oauthShowPage(OAuthSyncService svc, String url);

        public void oauthDone(OAuthSyncService svc);
    }
}

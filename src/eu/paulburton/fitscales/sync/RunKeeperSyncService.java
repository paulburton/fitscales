package eu.paulburton.fitscales.sync;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONObject;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Verb;

import android.util.Log;


public class RunKeeperSyncService extends OAuthSyncService
{
    private static final String TAG = "RunKeeperSyncService";
    private static final boolean DEBUG = false;

    private static final String API_BASE = "http://api.runkeeper.com";

    public RunKeeperSyncService()
    {
        super("RunKeeper", "runkeeper");

        authRedirectCodeParam = "code";
        setupOAuth("0bed6c44b8554e0bac1ad82f611df7a4", "395f1fb27f7949c1b840af3f61e765fe", RunKeeperApi.class);
    }
    
    @Override
    protected void postOAuth()
    {
        try {
            OAuthRequest request = new OAuthRequest(Verb.GET, API_BASE + "/profile");
            request.addHeader("Accept", "application/vnd.com.runkeeper.Profile+json");
            oaService.signRequest(oaToken, request);
            Response response = request.send();
            String body = response.getBody();
            if (DEBUG)
                Log.d(TAG, "Got profile body " + body);

            JSONObject json = new JSONObject(body);
            user = json.getString("name");
        } catch (Exception ex) {
            user = "unknown";
        }
    }
    
    @Override
    public boolean syncWeight(float weight)
    {
        try {
            SimpleDateFormat tsFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss", Locale.ENGLISH);
            String timestamp = tsFormat.format(new Date());
            String json = String.format("{\"weight\": %.2f,\"timestamp\": \"%s\" }", weight, timestamp);
            if (DEBUG)
                Log.d(TAG, "Posting weight " + json);

            OAuthRequest request = new OAuthRequest(Verb.POST, API_BASE + "/weight");
            request.addPayload(json);
            request.addHeader("Content-Type", "application/vnd.com.runkeeper.NewWeight+json");
            oaService.signRequest(oaToken, request);
            Response response = request.send();
            int code = response.getCode();
            
            if (DEBUG)
                Log.d(TAG, "Response code " + code);
            
            if (code == 200 || code == 201 || code == 204)
                return true;
            
            return false;
        } catch (Exception ex) {
            return false;
        }
    }
}

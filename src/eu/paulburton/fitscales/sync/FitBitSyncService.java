package eu.paulburton.fitscales.sync;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONObject;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Verb;

import android.util.Log;

public class FitBitSyncService extends OAuthSyncService
{
    private static final String TAG = "FitBitSyncService";
    private static final boolean DEBUG = false;

    private static final String API_BASE = "http://api.fitbit.com/1";

    public FitBitSyncService()
    {
        super("FitBit", "fitbit");

        authRedirectCodeParam = "oauth_verifier";
        setupOAuth("e2ceb2ee47074b239283ad996167ff84", "e253630f91cc496c833ab680386df3fa", FitBitApi.class);
    }
    
    @Override
    protected void postOAuth()
    {
        try {
            OAuthRequest request = new OAuthRequest(Verb.GET, API_BASE + "/user/-/profile.json");
            oaService.signRequest(oaToken, request);
            Response response = request.send();
            if (DEBUG)
                Log.d(TAG, "Profile response code " + response.getCode());
            String body = response.getBody();
            if (DEBUG)
                Log.d(TAG, "Got profile body " + body);

            JSONObject json = new JSONObject(body);
            user = json.getJSONObject("user").getString("displayName");
        } catch (Exception ex) {
            if (DEBUG)
                Log.e(TAG, "Failed to get profile", ex);
            user = "unknown";
        }
    }
    
    @Override
    public boolean syncWeight(float weight)
    {
        try {
            SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            String timestamp = tsFormat.format(new Date());

            OAuthRequest request = new OAuthRequest(Verb.POST, API_BASE + "/user/-/body.json");
            request.addBodyParameter("weight", String.format("%.2f", weight));
            request.addBodyParameter("date", timestamp);
            oaService.signRequest(oaToken, request);
            Response response = request.send();
            int code = response.getCode();
            
            if (DEBUG) {
                Log.d(TAG, "Response code " + code);
                try {
                    Log.d(TAG, "Response body " + response.getBody());
                } catch (Exception ex) {
                    Log.e(TAG, "Response body error", ex);
                }
            }
            
            if (code == 200 || code == 201 || code == 204)
                return true;
            
            return false;
        } catch (Exception ex) {
            return false;
        }
    }
}

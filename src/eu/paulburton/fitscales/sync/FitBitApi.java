package eu.paulburton.fitscales.sync;

import org.scribe.builder.api.DefaultApi10a;
import org.scribe.model.Token;

public class FitBitApi extends DefaultApi10a
{
    @Override
    public String getRequestTokenEndpoint()
    {
        return "https://api.fitbit.com/oauth/request_token";
    }

    @Override
    public String getAuthorizationUrl(Token token)
    {
        return String.format("https://www.fitbit.com/oauth/authorize?oauth_token=%s", token.getToken());
    }

    @Override
    public String getAccessTokenEndpoint()
    {
        return "https://api.fitbit.com/oauth/access_token";
    }
}

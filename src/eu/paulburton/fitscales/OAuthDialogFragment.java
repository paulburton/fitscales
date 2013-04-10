package eu.paulburton.fitscales;

import eu.paulburton.fitscales.sync.OAuthSyncService;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

public class OAuthDialogFragment extends DialogFragment
{
    private static final String TAG = "OAuthDialogFragment";
    private static final boolean DEBUG = false;

    private String url;
    private OAuthSyncService svc;
    private WebView web;
    private Button btnCancel;
    private boolean responded;

    static OAuthDialogFragment newInstance(String url, OAuthSyncService svc)
    {
        OAuthDialogFragment f = new OAuthDialogFragment();
        Bundle args = new Bundle();
        args.putString("url", url);
        args.putString("svc", svc.name);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        url = getArguments().getString("url");
        String svcName = getArguments().getString("svc");

        for (SyncService s : FitscalesApplication.inst.syncServices) {
            if (s.name.equals(svcName)) {
                svc = (OAuthSyncService)s;
                break;
            }
        }

        if (DEBUG) {
            if (svc == null)
                Log.e(TAG, "Unable to find service " + svcName + ", this will die horribly!");
        }
    }

    @Override
    public void onDestroy()
    {
        if (!responded) {
            if (DEBUG)
                Log.d(TAG, "onDestroy without response");
            svc.setOAuthPageResult("");
        }

        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.oauth_dialog, container, false);

        web = (WebView)v.findViewById(R.id.web);
        btnCancel = (Button)v.findViewById(R.id.btnCancel);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon)
            {
                Object tag = view.getTag();
                if (!(tag instanceof OAuthDialogFragment))
                    return;

                if (DEBUG)
                    Log.d(TAG, "onPageStarted " + url);

                if (url.startsWith("http://oauth.localhost/")) {
                    if (DEBUG)
                        Log.d(TAG, "Got OAuth response url");
                    view.setTag(null);
                    svc.setOAuthPageResult(url);
                    OAuthDialogFragment.this.responded = true;
                    web.post(new Runnable() {
                        public void run()
                        {
                            OAuthDialogFragment.this.dismiss();
                        }
                    });
                }
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                OAuthDialogFragment.this.dismiss();
            }
        });

        web.setTag(OAuthDialogFragment.this);
        web.getSettings().setJavaScriptEnabled(true);
        web.loadUrl(url);
        
        getDialog().setTitle(getResources().getString(R.string.dialog_oauth_title));

        return v;
    }
}

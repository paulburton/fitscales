package eu.paulburton.fitscales;

import java.util.Arrays;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import eu.paulburton.fitscales.BalanceBoard.Data;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class BoardFragment extends SherlockFragment
{
    private static final String TAG = "BoardFragment";
    private static final boolean DEBUG = false;

    private static final int REQUEST_ENABLE_BT = 0;

    private enum OverlayState
    {
        NONE, ERROR, SCANNING, CONNECTING, STABILISING, CALIBRATING,
    }

    private BluetoothAdapter btAdapter = null;
    private BalanceBoard wm = null;
    private DisconnectThread disconnectThread = null;
    private StabiliseThread stabiliseThread = null;
    private CalibrateThread calibrateThread = null;
    private StabiliseWeighinThread stabiliseWeighinThread = null;
    private boolean isStarted = false;
    private boolean btPowerOnStart = false;

    private BoardView boardView = null;
    private LinearLayout llOverlay = null;
    private LinearLayout llBluetoothWarning = null;
    private Button btnRetry = null;
    private ProgressBar pbBusy = null;
    private TextView tvBoardOverlayText = null;
    private TextView tvBoardOverlayTextSub = null;
    
    private MenuItem menuItemSettings;

    public BoardFragment()
    {
        super();
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        btPowerOnStart = (btAdapter != null) ? btAdapter.isEnabled() : false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.board_fragment, container, false);

        boardView = (BoardView)v.findViewById(R.id.board);
        llOverlay = (LinearLayout)v.findViewById(R.id.llOverlay);
        llBluetoothWarning = (LinearLayout)v.findViewById(R.id.llBluetoothWarning);
        btnRetry = (Button)v.findViewById(R.id.btnRetry);
        pbBusy = (ProgressBar)v.findViewById(R.id.pbBusy);
        tvBoardOverlayText = (TextView)v.findViewById(R.id.tvBoardOverlayText);
        tvBoardOverlayTextSub = (TextView)v.findViewById(R.id.tvBoardOverlayTextSub);

        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                startBtScan();
            }
        });

        return v;
    }

    @Override
    public void onStart()
    {
        super.onStart();

        isStarted = true;

        synchronized (this) {
            disconnectThread = null;
        }

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        getActivity().registerReceiver(btReceiver, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        getActivity().registerReceiver(btReceiver, filter);

        if (wm == null)
            startBtScan();
        else {
            boardView.setLed(wm.getLed(0));
            switchOverlay(OverlayState.NONE);
        }
    }

    @Override
    public void onStop()
    {
        getActivity().unregisterReceiver(btReceiver);

        synchronized (this) {
            disconnectThread = new DisconnectThread(this);
            disconnectThread.start();
        }

        isStarted = false;

        super.onStop();
    }

    @Override
    public void onDestroy()
    {
        cancelStabilise();
        cancelCalibrate();
        cancelWeighIn();

        if (wm != null) {
            wm.disconnect();
            wm = null;
        }

        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK)
                startBtScan();
            else
                bluetoothError();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main_menu, menu);
        
        menuItemSettings = menu.findItem(R.id.menuitem_settings);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Activity a = getActivity();
        if (a instanceof Listener)
            return ((Listener)a).onOptionsItemPressed(item);
        return super.onOptionsItemSelected(item);
    }
    
    public void setMenuIcons(boolean settingsVisible)
    {
        if (menuItemSettings != null)
            menuItemSettings.setIcon(settingsVisible ? R.drawable.ic_menu_close_clear_cancel : R.drawable.ic_menu_preferences);
    }

    private void switchOverlay(OverlayState state)
    {
        Resources res = getResources();

        if (DEBUG) {
            if (wm != null && state == OverlayState.SCANNING) {
                try {
                    throw new Exception();
                } catch (Exception ex) {
                    Log.e(TAG, "overlay shown with non-null wm", ex);
                }
            }
        }

        switch (state) {
        case NONE:
            llOverlay.setVisibility(View.GONE);
            break;

        case ERROR:
            pbBusy.setVisibility(View.GONE);
            btnRetry.setVisibility(View.VISIBLE);
            tvBoardOverlayText.setText(res.getString(R.string.board_overlay_error));
            tvBoardOverlayTextSub.setText(res.getString(R.string.board_overlay_error_sub));
            llOverlay.setVisibility(View.VISIBLE);
            break;

        case SCANNING:
            pbBusy.setVisibility(View.VISIBLE);
            btnRetry.setVisibility(View.GONE);
            tvBoardOverlayText.setText(res.getString(R.string.board_overlay_scanning));
            tvBoardOverlayTextSub.setText(res.getString(R.string.board_overlay_scanning_sub));
            llBluetoothWarning.setVisibility(View.VISIBLE);
            llOverlay.setVisibility(View.VISIBLE);
            break;

        case CONNECTING:
            pbBusy.setVisibility(View.VISIBLE);
            btnRetry.setVisibility(View.GONE);
            tvBoardOverlayText.setText(res.getString(R.string.board_overlay_connecting));
            tvBoardOverlayTextSub.setText(res.getString(R.string.board_overlay_connecting_sub));
            llBluetoothWarning.setVisibility(View.VISIBLE);
            llOverlay.setVisibility(View.VISIBLE);
            break;

        case STABILISING:
            pbBusy.setVisibility(View.VISIBLE);
            btnRetry.setVisibility(View.GONE);
            tvBoardOverlayText.setText(res.getString(R.string.board_overlay_stabilising));
            tvBoardOverlayTextSub.setText(res.getString(R.string.board_overlay_stabilising_sub));
            llBluetoothWarning.setVisibility(View.GONE);
            llOverlay.setVisibility(View.VISIBLE);
            break;

        case CALIBRATING:
            pbBusy.setVisibility(View.VISIBLE);
            btnRetry.setVisibility(View.GONE);
            tvBoardOverlayText.setText(res.getString(R.string.board_overlay_calibrating));
            tvBoardOverlayTextSub.setText(res.getString(R.string.board_overlay_calibrating_sub));
            llBluetoothWarning.setVisibility(View.GONE);
            llOverlay.setVisibility(View.VISIBLE);
            break;
        }

        Activity a = getActivity();
        if (a instanceof Listener)
            ((Listener)a).onBoardOverlayChange(state != OverlayState.NONE);
    }

    private void startBtScan()
    {
        if (DEBUG) {
            if (wm != null) {
                try {
                    throw new Exception();
                } catch (Exception ex) {
                    Log.e(TAG, "startBtScan called with non-null wm", ex);
                }
            }
        }

        cancelStabilise();
        cancelCalibrate();
        cancelWeighIn();
        switchOverlay(OverlayState.SCANNING);
        boardView.setLed(false);
        dataReport(0.0f, 0.0f, 0.0f, 0.0f);

        if (btAdapter == null) {
            bluetoothError();
            return;
        }
        if (!btAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
            return;
        }
        if (!btAdapter.startDiscovery()) {
            bluetoothError();
            return;
        }
        if (DEBUG)
            Log.d(TAG, "Bluetooth scan started");
    }

    private void bluetoothError()
    {
        if (DEBUG)
            Log.e(TAG, "Bluetooth error");
        switchOverlay(OverlayState.ERROR);
    }

    private void bluetoothFound(BluetoothDevice dev)
    {
        if (DEBUG)
            Log.d(TAG, "Using balance board device " + dev.getName() + " " + dev.getAddress());
        btAdapter.cancelDiscovery();
        wm = new BalanceBoard(btAdapter, dev, wmListener);
    }

    private void beginStabilise()
    {
        switchOverlay(OverlayState.STABILISING);
        wm.setBlinking(true);
        synchronized (this) {
            stabiliseThread = new StabiliseThread(this);
            stabiliseThread.start();
        }
    }

    private void cancelStabilise()
    {
        synchronized (this) {
            if (stabiliseThread != null) {
                stabiliseThread.cancel();
                try {
                    stabiliseThread.join();
                } catch (Exception ex) {
                }
                stabiliseThread = null;
            }
        }
    }

    private void boardStable()
    {
        switchOverlay(OverlayState.CALIBRATING);
        wm.setCalibrating(true);
        synchronized (this) {
            calibrateThread = new CalibrateThread(this);
            calibrateThread.start();
        }
    }

    private void cancelCalibrate()
    {
        synchronized (this) {
            if (calibrateThread != null) {
                calibrateThread.cancel();
                try {
                    calibrateThread.join();
                } catch (Exception ex) {
                }
                calibrateThread = null;
            }
        }
    }

    private void boardCalibrated()
    {
        switchOverlay(OverlayState.NONE);
        wm.setBlinking(false);
        wm.setLed(0, true);
        beginWeighIn();
    }

    private void dataReport(float tl, float tr, float bl, float br)
    {
        synchronized (this) {
            if (stabiliseThread != null)
                stabiliseThread.setData(tl, tr, bl, br);
            if (stabiliseWeighinThread != null)
                stabiliseWeighinThread.setData(tl, tr, bl, br);
        }

        boardView.setWeightData(tl, tr, bl, br);

        FragmentActivity act = BoardFragment.this.getActivity();
        if (act instanceof Listener)
            ((Listener)act).onBoardData(tl, tr, bl, br);
    }

    public void beginWeighIn()
    {
        synchronized (this) {
            if (stabiliseWeighinThread != null || calibrateThread != null || stabiliseThread != null) {
                if (DEBUG)
                    Log.d(TAG, "Ignoring beginWeighIn due to active thread");
                return;
            }
            if (wm == null) {
                if (DEBUG)
                    Log.e(TAG, "beginWeighIn with null wm");
                return;
            }

            stabiliseWeighinThread = new StabiliseWeighinThread(this);
            stabiliseWeighinThread.start();
        }
    }

    public void cancelWeighIn()
    {
        synchronized (this) {
            if (stabiliseWeighinThread != null) {
                stabiliseWeighinThread.cancel();
                try {
                    stabiliseWeighinThread.join();
                } catch (Exception ex) {
                }
                stabiliseWeighinThread = null;
            }
        }
    }

    private void boardWeighIn(float weight)
    {
        if (DEBUG)
            Log.d(TAG, "Weigh in! " + weight);

        FragmentActivity act = BoardFragment.this.getActivity();
        if (act instanceof Listener)
            ((Listener)act).onBoardWeighIn(weight);
    }

    private final BalanceBoard.Listener wmListener = new BalanceBoard.Listener() {
        @Override
        public void onWiimoteConnecting(BalanceBoard wm)
        {
            boardView.post(new Runnable() {
                public void run()
                {
                    switchOverlay(OverlayState.CONNECTING);
                }
            });
        }

        @Override
        public void onWiimoteConnected(BalanceBoard wm)
        {
            boardView.post(new Runnable() {
                public void run()
                {
                    beginStabilise();
                }
            });
        }

        @Override
        public void onWiimoteDisconnected(BalanceBoard wm)
        {
            boardView.post(new Runnable() {
                public void run()
                {
                    if (DEBUG)
                        Log.d(TAG, "onWiimoteDisconnected");
                    BoardFragment.this.wm = null;
                    if (BoardFragment.this.isStarted)
                        startBtScan();
                }
            });
        }

        @Override
        public void onWiimoteLEDChange(BalanceBoard wm)
        {
            final boolean ledOn = wm.getLed(0);
            boardView.post(new Runnable() {
                public void run()
                {
                    boardView.setLed(ledOn);
                }
            });
        }

        @Override
        public void onWiimoteData(BalanceBoard wm, Data data)
        {
            final float tl = data.getTopLeft();
            final float tr = data.getTopRight();
            final float bl = data.getBottomLeft();
            final float br = data.getBottomRight();
            boardView.post(new Runnable() {
                public void run()
                {
                    dataReport(tl, tr, bl, br);
                }
            });
        }
    };

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (DEBUG)
                Log.d(TAG, "Action " + action);

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (DEBUG)
                    Log.d(TAG, "Found device " + device.getAddress() + " " + device.getName());
                if ("Nintendo RVL-WBC-01".equals(device.getName())) {
                    if (BoardFragment.this.wm == null)
                        bluetoothFound(device);
                    else if (DEBUG)
                        Log.d(TAG, "Ignoring board because already have one");
                } else {
                    if (DEBUG)
                        Log.d(TAG, "Ignoring non-balance board device");
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (DEBUG)
                    Log.d(TAG, "BT scan finished");
                if (BoardFragment.this.wm == null) {
                    if (DEBUG)
                        Log.d(TAG, "No board connected, begin another scan");
                    startBtScan();
                }
            }
        }
    };

    private final class DisconnectThread extends Thread
    {
        private BoardFragment bf;

        public DisconnectThread(BoardFragment bf)
        {
            this.bf = bf;
        }

        @Override
        public void run()
        {
            BalanceBoard wm;

            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
            }

            synchronized (bf) {
                if (bf.disconnectThread != this)
                    return;
                wm = bf.wm;
                bf.wm = null;
                bf.disconnectThread = null;
            }

            if (DEBUG)
                Log.d(TAG, "Display timeout");

            if (wm != null) {
                if (DEBUG)
                    Log.d(TAG, "Disconnecting from balance board");
                wm.disconnect();
            }

            boolean restoreBtPower = true;

            if (restoreBtPower && (btAdapter != null) && !btPowerOnStart)
                btAdapter.disable();
        }
    }

    private final class StabiliseThread extends Thread
    {
        private BoardFragment bf;
        private boolean canceled = false;
        private float tl, tr, bl, br;

        public StabiliseThread(BoardFragment bf)
        {
            this.bf = bf;
        }

        public void cancel()
        {
            canceled = true;
            interrupt();
        }

        public void setData(float tl, float tr, float bl, float br)
        {
            this.tl = tl;
            this.tr = tr;
            this.bl = bl;
            this.br = br;
        }

        @Override
        public void run()
        {
            final int nSamples = 50;
            final float precisionMult = 1.0f / Prefs.getStabilityPrecision();
            final float maxWeight = 2.0f * precisionMult;
            final float maxDelta = 0.2f * precisionMult;
            final float maxWeightTotal = 4.0f * precisionMult;
            float[] tlSamples = new float[nSamples];
            float[] trSamples = new float[nSamples];
            float[] blSamples = new float[nSamples];
            float[] brSamples = new float[nSamples];
            Arrays.fill(tlSamples, 0.0f);
            Arrays.fill(trSamples, 0.0f);
            Arrays.fill(blSamples, 0.0f);
            Arrays.fill(brSamples, 0.0f);
            int count = 0, idx = 0;

            while (!canceled) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                }

                tlSamples[idx] = tl;
                trSamples[idx] = tr;
                blSamples[idx] = bl;
                brSamples[idx] = br;
                count = Math.min(count + 1, nSamples);

                float maxTl = BalanceBoard.arrayMax(tlSamples, idx, count);
                float maxTr = BalanceBoard.arrayMax(trSamples, idx, count);
                float maxBl = BalanceBoard.arrayMax(blSamples, idx, count);
                float maxBr = BalanceBoard.arrayMax(brSamples, idx, count);

                float minTl = BalanceBoard.arrayMin(tlSamples, idx, count);
                float minTr = BalanceBoard.arrayMin(trSamples, idx, count);
                float minBl = BalanceBoard.arrayMin(blSamples, idx, count);
                float minBr = BalanceBoard.arrayMin(brSamples, idx, count);

                idx = (idx + 1) % nSamples;

                if (count < nSamples)
                    continue;

                if (maxTl > maxWeight || maxTr > maxWeight || maxBl > maxWeight || maxBr > maxWeight) {
                    if (DEBUG)
                        Log.d(TAG, "maxWeight violation {" + maxTl + "," + maxTr + "," + maxBl + "," + maxBr + "}");
                    continue;
                }

                float total = Math.max(Math.abs(minTl + minTr + minBl + minBr), maxTl + maxTr + maxBl + maxBr);
                if (total > maxWeightTotal) {
                    if (DEBUG)
                        Log.d(TAG, "maxWeightTotal violation " + total);
                    continue;
                }

                if (maxTl - minTl > maxDelta) {
                    if (DEBUG)
                        Log.d(TAG, "TL delta violation max=" + maxTl + " min=" + minTl + " delta=" + (maxTl - minTl));
                    continue;
                }
                if (maxTr - minTr > maxDelta) {
                    if (DEBUG)
                        Log.d(TAG, "TR delta violation max=" + maxTr + " min=" + minTr + " delta=" + (maxTr - minTr));
                    continue;
                }
                if (maxBl - minBl > maxDelta) {
                    if (DEBUG)
                        Log.d(TAG, "BL delta violation max=" + maxBl + " min=" + minBl + " delta=" + (maxBl - minBl));
                    continue;
                }
                if (maxBr - minBr > maxDelta) {
                    if (DEBUG)
                        Log.d(TAG, "BR delta violation max=" + maxBr + " min=" + minBr + " delta=" + (maxBr - minBr));
                    continue;
                }

                synchronized (bf) {
                    bf.stabiliseThread = null;
                }
                bf.boardView.post(new Runnable() {
                    @Override
                    public void run()
                    {
                        bf.boardStable();
                    }
                });
                break;
            }
        }
    }

    private final class CalibrateThread extends Thread
    {
        private BoardFragment bf;
        private boolean canceled = false;

        public CalibrateThread(BoardFragment bf)
        {
            this.bf = bf;
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
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }

            if (canceled)
                return;

            synchronized (bf) {
                bf.calibrateThread = null;
            }
            bf.boardView.post(new Runnable() {
                @Override
                public void run()
                {
                    bf.wm.setCalibrating(false);
                    bf.boardCalibrated();
                }
            });
        }
    }

    private final class StabiliseWeighinThread extends Thread
    {
        private BoardFragment bf;
        private boolean canceled = false;
        private float tl, tr, bl, br;

        public StabiliseWeighinThread(BoardFragment bf)
        {
            this.bf = bf;
        }

        public void cancel()
        {
            canceled = true;
            interrupt();
        }

        public void setData(float tl, float tr, float bl, float br)
        {
            this.tl = tl;
            this.tr = tr;
            this.bl = bl;
            this.br = br;
        }

        @Override
        public void run()
        {
            final int nSamples = 50;
            final float precisionMult = 1.0f / Prefs.getStabilityPrecision();
            final float maxDelta = 0.2f * precisionMult;
            final float minWeight = 20.0f;
            float[] samples = new float[nSamples];
            Arrays.fill(samples, 0.0f);
            int count = 0, idx = 0;

            while (!canceled) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                }

                samples[idx] = tl + tr + bl + br;
                count = Math.min(count + 1, nSamples);

                final float min = BalanceBoard.arrayMin(samples, idx, count);
                final float max = BalanceBoard.arrayMax(samples, idx, count);
                final float mean = BalanceBoard.arrayMean(samples, idx, count);

                idx = (idx + 1) % nSamples;

                if (mean < minWeight) {
                    if (DEBUG)
                        Log.d(TAG, "Min weight violation " + mean);
                    continue;
                }

                if (count < nSamples)
                    continue;

                if (max - min > maxDelta) {
                    if (DEBUG)
                        Log.d(TAG, "Delta violation max=" + max + " min=" + min + " delta=" + (max - min));
                    continue;
                }

                synchronized (bf) {
                    bf.stabiliseWeighinThread = null;
                }
                bf.boardView.post(new Runnable() {
                    @Override
                    public void run()
                    {
                        bf.boardWeighIn(mean);
                    }
                });
                break;
            }
        }
    }

    public interface Listener
    {
        public void onBoardData(float tl, float tr, float bl, float br);
        public void onBoardWeighIn(float weight);
        public void onBoardOverlayChange(boolean showing);
        public boolean onOptionsItemPressed(MenuItem item);
    }
}

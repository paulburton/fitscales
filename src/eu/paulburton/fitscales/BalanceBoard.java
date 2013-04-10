package eu.paulburton.fitscales;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class BalanceBoard
{
    private static final String TAG = "BalanceBoard";
    private static final boolean DEBUG = false;

    private BluetoothAdapter btAdapter;
    private BluetoothDevice dev;
    private InputThread inputThread;
    private OutputThread outputThread;
    private KickStatusThread kickStatusThread;
    private BlinkThread blinkThread;
    private Object lock;
    private byte ledState = 0x0;
    private boolean haveExpansion = false;
    private boolean receivedStatus = false;
    private ArrayList<ReadRequest> readRequests = new ArrayList<ReadRequest>();
    private ArrayList<WeakReference<Listener>> listeners = new ArrayList<WeakReference<Listener>>();
    private Data dat = new Data();

    private static final byte CMD_LED = 0x11;
    private static final byte CMD_REPORT_TYPE = 0x12;
    private static final byte CMD_CTRL_STATUS = 0x15;
    private static final byte CMD_WRITE_DATA = 0x16;
    private static final byte CMD_READ_DATA = 0x17;

    private static final byte RPT_CTRL_STATUS = 0x20;
    private static final byte RPT_READ = 0x21;
    private static final byte RPT_WRITE = 0x22;
    private static final byte RPT_BTN = 0x30;
    private static final byte RPT_BTN_EXP = 0x34;

    private static final int MEM_OFFSET_CALIBRATION = 0x16;

    private static final int EXP_MEM_ENABLE = 0x04A40040;
    private static final int EXP_MEM_CALIBR = 0x04A40020;

    private static final int EXP_ID_WIIBOARD = 0xA4200402;

    private static final short EXP_HANDSHAKE_LEN = 224;

    public BalanceBoard(BluetoothAdapter btAdapter, BluetoothDevice dev, Listener... listeners)
    {
        this.btAdapter = btAdapter;
        this.dev = dev;

        for (Listener l : listeners)
            this.listeners.add(new WeakReference<Listener>(l));

        lock = new Object();
        inputThread = new InputThread(this);
        outputThread = new OutputThread(this);

        for (WeakReference<Listener> wl : this.listeners) {
            Listener l = wl.get();
            if (l != null)
                l.onWiimoteConnecting(this);
        }

        inputThread.start();
        outputThread.start();
    }

    public void disconnect()
    {
        if (DEBUG)
            Log.d(TAG, "Disconnecting");

        if (inputThread != null)
            inputThread.cancel();

        if (outputThread != null)
            outputThread.cancel();

        if (kickStatusThread != null)
            kickStatusThread.cancel();
        
        if (blinkThread != null)
            blinkThread.cancel();

        if (inputThread != null) {
            if (DEBUG)
                Log.d(TAG, "Joining inputThread");

            try {
                inputThread.join(100);
            } catch (InterruptedException ex) {
            }
            inputThread = null;
        }

        if (outputThread != null) {
            if (DEBUG)
                Log.d(TAG, "Joining outputThread");

            try {
                outputThread.join(100);
            } catch (InterruptedException ex) {
            }
            outputThread = null;
        }

        if (kickStatusThread != null) {
            if (DEBUG)
                Log.d(TAG, "Joining kickStatusThread");

            try {
                kickStatusThread.join(100);
            } catch (InterruptedException ex) {
            }
            kickStatusThread = null;
        }

        if (blinkThread != null) {
            if (DEBUG)
                Log.d(TAG, "Joining blinkThread");

            try {
                blinkThread.join(100);
            } catch (InterruptedException ex) {
            }
            blinkThread = null;
        }

        if (DEBUG)
            Log.d(TAG, "Notifying listeners of disconnect");

        for (WeakReference<Listener> wl : this.listeners) {
            Listener l = wl.get();
            if (l != null)
                l.onWiimoteDisconnected(this);
        }
    }

    public boolean getLed(int idx)
    {
        return (ledState & (1 << idx)) != 0;
    }

    public void setLed(int idx, boolean on)
    {
        if (on)
            ledState |= (1 << idx);
        else
            ledState &= ~(1 << idx);

        byte[] data = new byte[1];
        data[0] = (byte)(ledState << 4);
        sendCmd(CMD_LED, data);

        for (WeakReference<Listener> wl : this.listeners) {
            Listener l = wl.get();
            if (l != null)
                l.onWiimoteLEDChange(this);
        }
    }
    
    public void setBlinking(boolean blink)
    {
        if (blink == (blinkThread != null))
            return;
        
        if (blink) {
            blinkThread = new BlinkThread(this);
            blinkThread.start();
        } else {
            blinkThread.cancel();
            try {
                blinkThread.join(100);
            } catch (InterruptedException ex) {
            }
            blinkThread = null;
        }
    }

    public void setCalibrating(boolean cal)
    {
        dat.setCalibrating(cal);
    }

    private void setReportType()
    {
        byte[] payload = new byte[2];
        payload[0] = 0x00;
        payload[1] = 0x34; // BTN_EXP
        sendCmd(CMD_REPORT_TYPE, payload);
    }

    private void connectionFailed()
    {
        disconnect();
    }

    private void connectionReady()
    {
        if (!inputThread.ready || !outputThread.ready) {
            /* need both */
            return;
        }

        if (DEBUG)
            Log.d(TAG, "Connection to " + dev.getAddress() + " on adapter " + btAdapter.getAddress() + " ready");
        
        /*try {
            Method setPin = BluetoothDevice.class.getDeclaredMethod("setPin", byte[].class);
            setPin.setAccessible(true);
            String addr = btAdapter.getAddress();
            Log.d(TAG, "Attempting to pair using adapter " + addr);
            byte[] pin = new byte[6];
            pin[0] = Byte.parseByte(addr.substring(15, 17), 16);
            pin[1] = Byte.parseByte(addr.substring(12, 14), 16);
            pin[2] = Byte.parseByte(addr.substring(9, 11), 16);
            pin[3] = Byte.parseByte(addr.substring(6, 8), 16);
            pin[4] = Byte.parseByte(addr.substring(3, 5), 16);
            pin[5] = Byte.parseByte(addr.substring(0, 2), 16);
            setPin.invoke(dev, pin);
        } catch (Exception ex) {
            if (DEBUG)
                Log.e(TAG, "Failed to pair", ex);
        }*/

        setLed(0, true);
        readData(MEM_OFFSET_CALIBRATION, (short)7, new ReadListener() {
            public void onReadDone(byte[] data)
            {
                if (DEBUG)
                    Log.d(TAG, "Calibration data received");
            }

            public void onReadError()
            {
                if (DEBUG)
                    Log.e(TAG, "Failed to read calibration data");
            }
        });
        setReportType();
        setLed(0, true);

        kickStatusThread = new KickStatusThread(this);
        kickStatusThread.start();

        for (WeakReference<Listener> wl : this.listeners) {
            Listener l = wl.get();
            if (l != null)
                l.onWiimoteConnected(this);
        }
    }

    private void receivedData(byte[] data, int len)
    {
        if (len < 2)
            return;

        byte event = data[1];

        switch (event) {
        case RPT_CTRL_STATUS:
            if (DEBUG)
                Log.d(TAG, "Control status report " + len + " bytes");
            receivedStatus = true;
            boolean expansion = (data[4] & 0x02) != 0;
            if (expansion && !haveExpansion)
                handshakeExpansion();
            else if (!expansion && haveExpansion)
                disableExpansion();
            break;

        case RPT_READ:
            // if (DEBUG) Log.d(TAG, "Read report");
            ReadRequest req;
            synchronized (readRequests) {
                if (readRequests.size() == 0) {
                    Log.e(TAG, "Unexpected read report");
                    break;
                }
                req = readRequests.get(0);
            }
            boolean err = (data[4] & 0x0f) != 0;
            if (err) {
                if (req.listener != null)
                    req.listener.onReadError();
            } else {
                int rdlen = ((((int)data[4] & 0xff) & 0xf0) >> 4) + 1;
                int off = (((int)data[5] & 0xff) << 8) + ((int)data[6] & 0xff);
                System.arraycopy(data, 7, req.data, off - (req.addr & 0xffff), rdlen);
                req.remaining -= rdlen;
                if (req.remaining >= req.len)
                    req.remaining = 0;
                if (req.remaining > 0)
                    break;
                if (req.listener != null)
                    req.listener.onReadDone(req.data);
            }
            synchronized (readRequests) {
                readRequests.remove(0);
                if (readRequests.size() != 0)
                    sendReadRequest(readRequests.get(0));
            }
            break;

        case RPT_WRITE:
            // if (DEBUG) Log.d(TAG, "Write report");
            break;

        case RPT_BTN:
            break;

        case RPT_BTN_EXP:
            // if (DEBUG) Log.d(TAG, "Data report " + data.length + " bytes");
            if (len < 12)
                break;
            ByteBuffer bbuf = ByteBuffer.wrap(data);
            bbuf.order(ByteOrder.BIG_ENDIAN);
            int rtr = bbuf.getShort(4);
            int rbr = bbuf.getShort(6);
            int rtl = bbuf.getShort(8);
            int rbl = bbuf.getShort(10);
            dat.setRaw(rtl, rtr, rbl, rbr);
            // if (DEBUG) Log.d(TAG, "rtr=" + rtr + " rbr=" + rbr + " rtl=" + rtl + " rbl=" + rbl);

            for (WeakReference<Listener> wl : this.listeners) {
                Listener l = wl.get();
                if (l != null)
                    l.onWiimoteData(this, dat);
            }
            break;

        default:
            if (DEBUG)
                Log.d(TAG, "Unhandled event " + event);
        }
    }

    private void sendCmd(byte cmd, byte[] data)
    {
        byte[] payload = new byte[data.length + 2];
        payload[0] = 0x52; /* WM_SET_REPORT | WM_BT_OUTPUT */
        payload[1] = cmd;
        System.arraycopy(data, 0, payload, 2, data.length);
        outputThread.write(payload);
    }

    private void readData(int addr, short len, ReadListener listener)
    {
        ReadRequest req = new ReadRequest();
        req.addr = addr;
        req.len = len;
        req.remaining = len;
        req.data = new byte[len];
        req.listener = listener;
        enqueueReadRequest(req);
    }

    private void enqueueReadRequest(ReadRequest req)
    {
        synchronized (readRequests) {
            readRequests.add(req);

            if (readRequests.size() == 1)
                sendReadRequest(req);
        }
    }

    private void sendReadRequest(ReadRequest req)
    {
        byte[] payload = new byte[6];
        payload[0] = (byte)(req.addr >> 24);
        payload[1] = (byte)(req.addr >> 16);
        payload[2] = (byte)(req.addr >> 8);
        payload[3] = (byte)(req.addr >> 0);
        payload[4] = (byte)(req.len >> 8);
        payload[5] = (byte)(req.len >> 0);
        sendCmd(CMD_READ_DATA, payload);
    }

    private void writeData(int addr, byte[] data)
    {
        byte[] payload = new byte[21];
        payload[0] = (byte)(addr >> 24);
        payload[1] = (byte)(addr >> 16);
        payload[2] = (byte)(addr >> 8);
        payload[3] = (byte)(addr >> 0);
        payload[4] = (byte)data.length;
        System.arraycopy(data, 0, payload, 5, data.length);
        sendCmd(CMD_WRITE_DATA, payload);
    }

    private void handshakeExpansion()
    {
        if (DEBUG)
            Log.d(TAG, "Handshaking expansion");

        byte[] payload = new byte[1];
        payload[0] = 0x00;
        writeData(EXP_MEM_ENABLE, payload);

        readData(EXP_MEM_CALIBR, EXP_HANDSHAKE_LEN, new ReadListener() {
            public void onReadDone(byte[] data)
            {
                if (DEBUG)
                    Log.d(TAG, "Got expansion calibration data");

                int id = (((int)data[220] & 0xff) << 24) | (((int)data[221] & 0xff) << 16)
                        | (((int)data[222] & 0xff) << 8) | (((int)data[223] & 0xff) << 0);
                if (DEBUG)
                    Log.d(TAG, "expansion " + Integer.toHexString(id) + " detected");

                if (id != EXP_ID_WIIBOARD) {
                    Log.e(TAG, "invalid expansion " + Integer.toHexString(id));
                    disconnect();
                }

                ByteBuffer bbuf = ByteBuffer.wrap(data);
                bbuf.order(ByteOrder.BIG_ENDIAN);

                dat.cTr[0] = bbuf.getShort(4);
                dat.cBr[0] = bbuf.getShort(6);
                dat.cTl[0] = bbuf.getShort(8);
                dat.cBl[0] = bbuf.getShort(10);

                dat.cTr[1] = bbuf.getShort(12);
                dat.cBr[1] = bbuf.getShort(14);
                dat.cTl[1] = bbuf.getShort(16);
                dat.cBl[1] = bbuf.getShort(18);

                dat.cTr[2] = bbuf.getShort(20);
                dat.cBr[2] = bbuf.getShort(22);
                dat.cTl[2] = bbuf.getShort(24);
                dat.cBl[2] = bbuf.getShort(26);

                if (DEBUG) {
                    Log.d(TAG, "cTr 0=" + dat.cTr[0] + " 17=" + dat.cTr[1] + " 34=" + dat.cTr[2]);
                    Log.d(TAG, "cTl 0=" + dat.cTl[0] + " 17=" + dat.cTl[1] + " 34=" + dat.cTl[2]);
                    Log.d(TAG, "cBr 0=" + dat.cBr[0] + " 17=" + dat.cBr[1] + " 34=" + dat.cBr[2]);
                    Log.d(TAG, "cBl 0=" + dat.cBl[0] + " 17=" + dat.cBl[1] + " 34=" + dat.cBl[2]);
                }

                haveExpansion = true;
            }

            public void onReadError()
            {
                if (DEBUG)
                    Log.e(TAG, "Failed to read expansion calibration");
                disconnect();
            }
        });
    }

    private void disableExpansion()
    {
        haveExpansion = false;
    }

    static float arrayMean(float[] a, int last, int count)
    {
        float total = 0.0f;

        for (int i = 0; i < count; i++) {
            int idx = last - i;
            while (idx < 0)
                idx += count;
            total += a[idx];
        }

        return total / count;
    }

    static float arrayMin(float[] a, int last, int count)
    {
        float min = a[last];

        for (int i = 1; i < count; i++) {
            int idx = last - i;
            while (idx < 0)
                idx += count;
            min = Math.min(min, a[idx]);
        }

        return min;
    }

    static float arrayMax(float[] a, int last, int count)
    {
        float max = a[last];

        for (int i = 1; i < count; i++) {
            int idx = last - i;
            while (idx < 0)
                idx += count;
            max = Math.max(max, a[idx]);
        }

        return max;
    }

    public class Data
    {
        private int rawTl, rawTr, rawBl, rawBr;
        private int cTl[], cTr[], cBl[], cBr[];

        private static final int nSmoothItems = 20;
        private float tl[], tr[], bl[], br[];
        private int smoothIdx = 0;
        private int smoothCount = 0;
        private float stl, str, sbl, sbr;
        private float calTl, calTr, calBl, calBr;

        private boolean calibrating = false;

        private Data()
        {
            cTl = new int[3];
            Arrays.fill(cTl, 0);
            cTr = new int[3];
            Arrays.fill(cTr, 0);
            cBl = new int[3];
            Arrays.fill(cBl, 0);
            cBr = new int[3];
            Arrays.fill(cBr, 0);

            tl = new float[nSmoothItems];
            Arrays.fill(tl, 0);
            tr = new float[nSmoothItems];
            Arrays.fill(tr, 0);
            bl = new float[nSmoothItems];
            Arrays.fill(bl, 0);
            br = new float[nSmoothItems];
            Arrays.fill(br, 0);

            rawTl = rawTr = rawBl = rawBr = 0;
            stl = str = sbl = sbr = 0.0f;
        }
        
        public void setCalibrating(boolean cal)
        {
            if (calibrating == cal)
                return;

            calibrating = cal;

            if (calibrating) {
                calTl = calTr = calBl = calBr = 0.0f;
            } else {
                calTl = stl;
                calTr = str;
                calBl = sbl;
                calBr = sbr;
                if (DEBUG)
                    Log.d(TAG, "Calibration complete {" + calTl + "," + calTr + "," + calBl + "," + calBr + "}");
            }
        }

        private float interpolate(int raw, int[] cal)
        {
            /*if (raw < cal[0])
                return 0.0f;*/

            if (raw < cal[1]) {
                /* between 0kg & 17kg */
                return 17.0f * ((float)(raw - cal[0]) / (cal[1] - cal[0]));
            }

            /* over 17kg */
            return 17.0f + (17.0f * ((float)(raw - cal[1]) / (cal[2] - cal[1])));
        }

        private void setRaw(int rtl, int rtr, int rbl, int rbr)
        {
            rawTl = Math.max(rtl, 0);
            rawTr = Math.max(rtr, 0);
            rawBl = Math.max(rbl, 0);
            rawBr = Math.max(rbr, 0);

            tl[smoothIdx] = interpolate(rawTl, cTl);
            tr[smoothIdx] = interpolate(rawTr, cTr);
            bl[smoothIdx] = interpolate(rawBl, cBl);
            br[smoothIdx] = interpolate(rawBr, cBr);

            if (smoothCount < nSmoothItems)
                smoothCount++;

            stl = arrayMean(tl, smoothIdx, smoothCount);
            str = arrayMean(tr, smoothIdx, smoothCount);
            sbl = arrayMean(bl, smoothIdx, smoothCount);
            sbr = arrayMean(br, smoothIdx, smoothCount);

            /*
             * if (DEBUG) { Log.d(TAG, "new:" + tl[smoothIdx] + "," + tr[smoothIdx] + "," + bl[smoothIdx] + "," +
             * br[smoothIdx] + " smoothed:" + stl + "," + str + "," + sbl + "," + sbr); }
             */

            smoothIdx = (smoothIdx + 1) % nSmoothItems;
        }

        public float getTopLeft()
        {
            return stl - calTl;
        }

        public float getTopRight()
        {
            return str - calTr;
        }

        public float getBottomLeft()
        {
            return sbl - calBl;
        }

        public float getBottomRight()
        {
            return sbr - calBr;
        }
    }

    private class InputThread extends Thread
    {
        private BalanceBoard wm;
        private BluetoothSocket sk;
        private boolean canceled = false;
        private boolean ready = false;
        InputStream in = null;

        public InputThread(BalanceBoard wm)
        {
            this.wm = wm;
        }

        public void cancel()
        {
            canceled = true;
            try {
                /* cause any read to blow up */
                if (in != null)
                    in.close();
            } catch (Exception ex) {
            }
            interrupt();
        }

        @Override
        public void run()
        {
            try {
                sk = WiimoteSocket.create(wm.dev, 0x13);
            } catch (Exception ex) {
                if (DEBUG)
                    Log.e(TAG, "Failed to create WiimoteSocket", ex);
                synchronized (wm.lock) {
                    wm.connectionFailed();
                }
                return;
            }

            byte[] buf = new byte[512];
            try {
                sk.connect();
                in = sk.getInputStream();
                ready = true;
                synchronized (wm.lock) {
                    wm.connectionReady();
                }

                while (!canceled) {
                    int len = in.read(buf);
                    if (len <= 0)
                        break;
                    synchronized (wm.lock) {
                        wm.receivedData(buf, len);
                    }
                }
                
                ready = false;
            } catch (Exception ex) {
                if (DEBUG) {
                    if (!canceled)
                        Log.e(TAG, "Input error", ex);
                }
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception ex) {
                        if (DEBUG)
                            Log.e(TAG, "Failed to close input stream", ex);
                    }
                }
            }

            ready = false;
            if (DEBUG)
                Log.d(TAG, "Input thread ending");

            if (sk != null) {
                try {
                    sk.close();
                } catch (Exception ex) {
                    if (DEBUG)
                        Log.e(TAG, "Failed to close BluetoothSocket", ex);
                }
            }
            
            if (!canceled) {
                synchronized (wm.lock) {
                    wm.connectionFailed();
                }
            }
        }
    }

    private class OutputThread extends Thread
    {
        private BalanceBoard wm;
        private BluetoothSocket sk;
        private boolean canceled = false;
        private ArrayList<byte[]> queue = new ArrayList<byte[]>();
        private boolean ready = false;

        public OutputThread(BalanceBoard wm)
        {
            this.wm = wm;
        }

        public void cancel()
        {
            canceled = true;
            interrupt();

            synchronized (queue) {
                queue.notifyAll();
            }

            interrupt();
        }

        public void write(byte[] data)
        {
            synchronized (queue) {
                queue.add(data);
                queue.notifyAll();
            }
        }

        @Override
        public void run()
        {
            try {
                sk = WiimoteSocket.create(wm.dev, 0x11);
            } catch (Exception ex) {
                if (DEBUG)
                    Log.e(TAG, "Failed to create WiimoteSocket", ex);
                synchronized (wm.lock) {
                    wm.connectionFailed();
                }
                return;
            }

            OutputStream out = null;
            try {
                sk.connect();
                out = sk.getOutputStream();
                ready = true;
                synchronized (wm.lock) {
                    wm.connectionReady();
                }

                while (!canceled) {
                    synchronized (queue) {
                        while (queue.size() > 0) {
                            out.write(queue.remove(0));
                        }

                        try {
                            queue.wait();
                        } catch (InterruptedException ex) {
                        }
                    }
                }

                ready = false;
            } catch (Exception ex) {
                if (DEBUG)
                    Log.e(TAG, "Output error", ex);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception ex) {
                        if (DEBUG)
                            Log.e(TAG, "Failed to close output stream", ex);
                    }
                }
            }

            ready = false;
            if (DEBUG)
                Log.d(TAG, "Output thread ending");

            if (sk != null) {
                try {
                    sk.close();
                } catch (Exception ex) {
                    if (DEBUG)
                        Log.e(TAG, "Failed to close BluetoothSocket", ex);
                }
            }
            
            if (!canceled) {
                synchronized (wm.lock) {
                    wm.connectionFailed();
                }
            }
        }
    }

    private static class KickStatusThread extends Thread
    {
        private static final String TAG = "KickStatusThread";

        private BalanceBoard wm;
        private boolean canceled = false;

        public KickStatusThread(BalanceBoard wm)
        {
            this.wm = wm;
        }

        public void cancel()
        {
            canceled = true;
            interrupt();
        }

        public void run()
        {
            int rem = 20;
            while (!canceled && !wm.receivedStatus && rem > 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                }
                rem--;
            }

            if (canceled)
                return;

            if (!wm.receivedStatus) {
                if (DEBUG)
                    Log.d(TAG, "Requesting status");
                byte[] payload = new byte[1];
                payload[0] = 0;
                wm.sendCmd(CMD_CTRL_STATUS, payload);
            }

            rem = 10;
            while (!canceled && !wm.haveExpansion && rem > 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                }
                rem--;
            }

            if (canceled)
                return;

            if (!wm.haveExpansion) {
                if (DEBUG)
                    Log.d(TAG, "No expansion detected, abandoning board");
                wm.disconnect();
            }

            wm.kickStatusThread = null;
        }
    }

    private static class BlinkThread extends Thread
    {
        private BalanceBoard wm;
        private boolean canceled = false;

        public BlinkThread(BalanceBoard wm)
        {
            this.wm = wm;
        }

        public void cancel()
        {
            canceled = true;
            interrupt();
        }

        public void run()
        {
            while (!canceled) {
                wm.setLed(0, !wm.getLed(0));
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                }
            }
        }
    }

    private static final class ReadRequest
    {
        public int addr;
        public short len;
        public short remaining;
        public byte[] data;
        public ReadListener listener;
    }

    private interface ReadListener
    {
        void onReadDone(byte[] data);

        void onReadError();
    }

    public interface Listener
    {
        void onWiimoteConnecting(BalanceBoard wm);

        void onWiimoteConnected(BalanceBoard wm);

        void onWiimoteDisconnected(BalanceBoard wm);

        void onWiimoteLEDChange(BalanceBoard wm);

        void onWiimoteData(BalanceBoard wm, BalanceBoard.Data data);
    }
}

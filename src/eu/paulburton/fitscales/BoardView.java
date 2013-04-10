package eu.paulburton.fitscales;

import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class BoardView extends View
{
    private static final String TAG = "BoardView";
    private static final boolean DEBUG = false;

    private Picture svgPicture;
    private Paint btnPaint, ledOnPaint, ledOffPaint, balancePaint;
    private RectF btnRect, ledRect, svgLimits, svgRect;
    private float btnRound;
    private boolean ledOn = false;
    private Bitmap bmpStatic = null;
    private int w, h;
    private float wTl, wTr, wBl, wBr;
    private float balanceX = -1, balanceY = -1, balanceRad;

    public BoardView(Context context)
    {
        super(context);
        init();
    }

    public BoardView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    public BoardView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        init();
    }

    private void init()
    {
        SVG svg = SVGParser.getSVGFromResource(getResources(), R.raw.wii_balance_board);
        svgLimits = svg.getLimits();
        svgPicture = svg.getPicture();
        
        btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnPaint.setColor(0xffb0b0b0);
        
        ledOnPaint = new Paint();
        ledOnPaint.setColor(0xffa0c0ff);
        
        ledOffPaint = new Paint();
        ledOffPaint.setColor(0xff404040);
        
        balancePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        balancePaint.setStyle(Style.FILL);
        balancePaint.setColor(0xff60ff60);
    }
    
    public boolean getLed()
    {
        return ledOn;
    }

    public void setLed(boolean on)
    {
        ledOn = on;
        renderStatic();
        invalidate();
    }
    
    private int weightLogLimit = 0;

    public void setWeightData(float tl, float tr, float bl, float br)
    {
        float l, r, t, b;

        wTl = Math.max(tl, 0);
        wTr = Math.max(tr, 0);
        wBl = Math.max(bl, 0);
        wBr = Math.max(br, 0);
        l = wTl + wBl;
        r = wTr + wBr;
        t = wTl + wTr;
        b = wBl + wBr;
        
        if (r == 0 && l == 0)
            balanceX = -1;
        else if (r == 0)
            balanceX = 0;
        else
            balanceX = r / (r + l);

        if (t == 0 && b == 0)
            balanceY = -1;
        else if (b == 0)
            balanceY = 0;
        else
            balanceY = b / (b + t);
        
        if (DEBUG) {
            if ((weightLogLimit++ % 50) == 0)
                Log.d(TAG, "weights={" + tl + "," + tr + "," + bl + "," + br + "}=" + (l + r) + " balX=" + balanceX + " balY=" + balanceY);
        }

        balancePaint.setAlpha((int)(255 * Math.min(l + r, 20) / 20));

        invalidate();
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec)
    {
        int wMode, wSize, hMode, hSize;
        float bx, by, bw, bh;
        float btnL, btnT, btnW, btnH;
        float ledL, ledT, ledW, ledH;

        wMode = MeasureSpec.getMode(widthMeasureSpec);
        wSize = MeasureSpec.getSize(widthMeasureSpec);
        hMode = MeasureSpec.getMode(heightMeasureSpec);
        hSize = MeasureSpec.getSize(heightMeasureSpec);
        
        if (wMode == MeasureSpec.EXACTLY) {
            w = wSize;
        } else {
            w = 300; /* arbitrary */
            if (wMode == MeasureSpec.AT_MOST)
                w = Math.min(w, wSize);
        }
        
        if (hMode == MeasureSpec.EXACTLY) {
            h = hSize;
        } else {
            h = 200; /* arbitrary */
            if (hMode == MeasureSpec.AT_MOST)
                h = Math.min(h, hSize);
        }

        setMeasuredDimension(w, h);
        bx = by = 0;
        bw = w;
        bh = h;
        
        if (bw < bh) {
            float fullH = bh;
            bh = bw * (svgLimits.height() / svgLimits.width());
            by = (fullH - bh) / 2;
        }

        svgRect = new RectF(bx, by, bx + bw, by + bh);
        if (DEBUG)
            Log.d(TAG, "svgRect " + svgRect);
        
        btnW = bw / 12;
        btnH = bh / 10;
        btnL = bx + ((bw - btnW) / 2);
        btnT = by + ((bh - btnH) - (bh / 50));

        ledW = btnW / 10;
        ledH = btnH;
        ledL = btnL + (btnW / 6);
        ledT = btnT;

        btnRect = new RectF(btnL, btnT, btnL + btnW, btnT + btnH);
        ledRect = new RectF(ledL, ledT, ledL + ledW, ledT + ledH);
        
        btnRound = btnRect.width() / 15;
        
        balanceRad = w / 30;

        if (bmpStatic != null)
            bmpStatic.recycle();
        bmpStatic = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        renderStatic();
    }
    
    @Override
    protected void onDraw(Canvas canvas)
    {
        canvas.drawBitmap(bmpStatic, 0.0f, 0.0f, null);
        
        if (balanceX != -1 && balanceY != -1) {
            canvas.drawCircle(balanceX * w, balanceY * h, balanceRad, balancePaint);
        }
    }
    
    private void renderStatic()
    {
        if (bmpStatic == null)
            return;
        
        if (DEBUG)
            Log.d(TAG, "renderStatic ledOn=" + ledOn);

        Canvas canvas = new Canvas(bmpStatic);

        canvas.drawRoundRect(btnRect, btnRound, btnRound, btnPaint);
        canvas.drawRect(ledRect, ledOn ? ledOnPaint : ledOffPaint);
        canvas.drawPicture(svgPicture, svgRect);
    }
}

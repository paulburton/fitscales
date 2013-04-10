package eu.paulburton.fitscales;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class ScaleView extends View
{
    private static final String TAG = "ScaleView";
    private static final boolean DEBUG = false;

    private static final boolean DRAW_REGION_SEPARATORS = false;

    private Paint paintLine, paintSeparator, paintOverlay;
    private Paint paintPrevBmi, paintPrevBmiTriangle;
    private Paint paintCurrBmi, paintCurrBmiTriangle, paintCurrWeight;
    private int w, h;
    private float barOffset;
    private RectF barRect;
    private ScaleRegion[] regions;
    private ScaleRegionBoundary[] regionBoundaries;
    private Bitmap bmpStatic = null;
    private float minBmi = 15.0f;
    private float maxBmi = 35.0f;
    private float currBmi = -1.0f, prevBmi = -1.0f;
    private float currWeight = 0.0f;
    private String currWeightText = "0kg";
    private int overlayColor = 0;

    private final class ScaleRegion
    {
        private static final String TAG = "ScaleRegion";

        public String name;
        public float minBmi, maxBmi;
        public Paint paint;
        public RectF rect;

        public ScaleRegion(String name, float minBmi, float maxBmi, int color)
        {
            this.name = name;
            this.minBmi = minBmi;
            this.maxBmi = maxBmi;
            this.paint = new Paint();
            this.paint.setColor(color);
            this.rect = new RectF();
        }

        public void calcRect(RectF barRect, float barMin, float barMax)
        {
            float barRange = barMax - barMin;

            if (minBmi > barMax || maxBmi < barMin) {
                /* outside of the bar */
                rect = new RectF();
                return;
            }

            float barLen = Math.max(barRect.width(), barRect.height());

            float rOffMin = ((minBmi - barMin) / barRange) * barLen;
            float rOffMax = ((maxBmi - barMin) / barRange) * barLen;
            rOffMin = Math.max(rOffMin, 0.0f);
            rOffMax = Math.min(rOffMax, barLen);

            if (barRect.width() > barRect.height()) {
                /* horizontal */
                rect = new RectF(barRect.left + rOffMin, barRect.top, barRect.left + rOffMax, barRect.bottom);
            } else {
                /* vertical */
                rect = new RectF(barRect.left, barRect.bottom - rOffMax, barRect.right, barRect.bottom - rOffMin);
            }

            if (DEBUG)
                Log.d(TAG, "Region " + name + " " + rect);
        }
    }

    private final class ScaleRegionBoundary
    {
        public RectF rect;
        public Paint paint;
        public float lx0, ly0, lx1, ly1;

        public ScaleRegionBoundary(ScaleRegion rgn1, ScaleRegion rgn2)
        {
            float x0, y0, x1, y1;
            int col0, col1;

            if (rgn2.rect.left > rgn1.rect.left) {
                /* horizontal */
                x0 = (float)(rgn1.rect.left + (rgn1.rect.width() * 0.75));
                x1 = (float)(rgn2.rect.left + (rgn2.rect.width() * 0.25));
                lx0 = lx1 = rgn1.rect.right;
                ly0 = y0 = y1 = rgn1.rect.top;
                ly1 = rgn1.rect.bottom;
                col0 = rgn1.paint.getColor();
                col1 = rgn2.paint.getColor();
                rect = new RectF(x0, rgn1.rect.top, x1, rgn1.rect.bottom);
            } else {
                /* vertical */
                y0 = (float)(rgn2.rect.top + (rgn2.rect.height() * 0.75));
                y1 = (float)(rgn1.rect.top + (rgn1.rect.height() * 0.25));
                ly0 = ly1 = rgn1.rect.top;
                lx0 = x0 = x1 = rgn1.rect.left;
                lx1 = rgn1.rect.right;
                col0 = rgn2.paint.getColor();
                col1 = rgn1.paint.getColor();
                rect = new RectF(rgn1.rect.left, y0, rgn1.rect.right, y1);
            }

            LinearGradient grad = new LinearGradient((int)x0, (int)y0, (int)x1, (int)y1, col0, col1,
                    Shader.TileMode.CLAMP);
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(grad);
        }
    }

    public ScaleView(Context context)
    {
        super(context);
        init();
    }

    public ScaleView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init();
    }

    public ScaleView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        init();
    }

    private void init()
    {
        regions = new ScaleRegion[4];
        regions[0] = new ScaleRegion("underweight", 0.0f, 18.5f, 0xffff4020);
        regions[1] = new ScaleRegion("normal", 18.5f, 25.0f, 0xff00ff00);
        regions[2] = new ScaleRegion("overweight", 25.0f, 30.0f, 0xffffff00);
        regions[3] = new ScaleRegion("obese", 30.0f, Float.MAX_VALUE, 0xffff4020);

        paintLine = new Paint();
        paintLine.setColor(0xff33b5e5);
        paintLine.setStrokeWidth(2);

        if (DRAW_REGION_SEPARATORS) {
            paintSeparator = new Paint();
            paintSeparator.setColor(0xff606060);
            paintSeparator.setStyle(Style.STROKE);
            paintSeparator.setStrokeWidth(1);
            paintSeparator.setPathEffect(new DashPathEffect(new float[] { 20, 10 }, 0));
        }

        paintOverlay = new Paint();
        paintOverlay.setColor(0);
        paintOverlay.setStyle(Style.FILL);

        paintCurrBmi = new Paint();
        paintCurrBmi.setColor(0xff33b5e5);
        paintCurrBmi.setStrokeWidth(3);

        paintCurrBmiTriangle = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCurrBmiTriangle.setColor(0xff33b5e5);
        paintCurrBmiTriangle.setStyle(Style.FILL);

        paintCurrWeight = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCurrWeight.setColor(0xff202020);
        paintCurrWeight.setTypeface(Typeface.DEFAULT);

        paintPrevBmi = new Paint();
        paintPrevBmi.setColor(0xff808080);
        paintPrevBmi.setStrokeWidth(3);

        paintPrevBmiTriangle = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintPrevBmiTriangle.setColor(0xff808080);
    }

    public void setCurrent(float bmi, float weight)
    {
        currBmi = bmi;
        currWeight = weight;
        currWeightText = null; //String.format("%.2fkg", currWeight);
        if (DEBUG)
            Log.v(TAG, "Update current weight=" + currWeight + " bmi=" + bmi);
        invalidate();
    }

    public void setPrevious(float bmi, float weight)
    {
        prevBmi = bmi;
        if (DEBUG)
            Log.v(TAG, "Update prev weight=" + weight + " bmi=" + bmi);
        invalidate();
    }
    
    public void setOverlay(int color)
    {
        overlayColor = color;
        paintOverlay.setColor(color);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        int wMode, wSize, hMode, hSize;
        float barWidth;

        wMode = MeasureSpec.getMode(widthMeasureSpec);
        wSize = MeasureSpec.getSize(widthMeasureSpec);
        hMode = MeasureSpec.getMode(heightMeasureSpec);
        hSize = MeasureSpec.getSize(heightMeasureSpec);

        if (wMode == MeasureSpec.EXACTLY) {
            w = wSize;
        } else {
            w = 300; /* arbitrary */
            if (wMode == MeasureSpec.AT_MOST)
                w = Math.min((int)w, wSize);
        }

        if (hMode == MeasureSpec.EXACTLY) {
            h = hSize;
        } else {
            h = 200; /* arbitrary */
            if (hMode == MeasureSpec.AT_MOST)
                h = Math.min((int)h, hSize);
        }

        setMeasuredDimension((int)w, (int)h);

        barOffset = Math.min(w, h) / 2;
        barWidth = (int)Math.ceil((float)Math.min(w, h) / 2);

        if (w > h) {
            /* horizontal */
            if (DEBUG)
                Log.d(TAG, "Rendering horizontally");
            barRect = new RectF(0, barOffset, w, barOffset + barWidth);
            paintCurrWeight.setTextAlign(Align.LEFT);
        } else {
            /* vertical */
            if (DEBUG)
                Log.d(TAG, "Rendering vertically");
            barRect = new RectF(barOffset, 0, barOffset + barWidth, h);
            paintCurrWeight.setTextAlign(Align.CENTER);
        }

        paintCurrWeight.setTextSize(barWidth / 4);

        for (ScaleRegion rgn : regions)
            rgn.calcRect(barRect, minBmi, maxBmi);

        regionBoundaries = new ScaleRegionBoundary[regions.length - 1];
        int i;
        for (i = 0; i < regionBoundaries.length; i++)
            regionBoundaries[i] = new ScaleRegionBoundary(regions[i], regions[i + 1]);

        if (bmpStatic != null)
            bmpStatic.recycle();
        bmpStatic = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        renderStatic();
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        canvas.drawBitmap(bmpStatic, 0.0f, 0.0f, null);

        renderBmi(canvas, prevBmi, paintPrevBmi, paintPrevBmiTriangle, null, null);
        renderBmi(canvas, currBmi, paintCurrBmi, paintCurrBmiTriangle, currWeightText, paintCurrWeight);
        
        if (overlayColor != 0)
            canvas.drawRect(0.0f, 0.0f, w, h, paintOverlay);
    }

    private void renderBmi(Canvas canvas, float bmi, Paint bmiPaint, Paint bmiPaintTriangle, String text, Paint textPaint)
    {
        if (bmi < minBmi || bmi > maxBmi)
            return;

        if (w > h) {
            /* horizontal */
            float x = Math.round(w * ((bmi - minBmi) / (maxBmi - minBmi)));
            canvas.drawLine(x, barOffset / 2, x, h, bmiPaint);
            
            if (bmiPaintTriangle != null) {
                float len = barOffset * 0.6f;
                
                Path p = new Path();
                p.moveTo(x - (len / 2), barOffset / 2); /* tl */
                p.lineTo(x + (len / 2), barOffset / 2); /* tr */
                p.lineTo(x, (barOffset / 2) + (float)((len / 2) / Math.sin(Math.PI / 6))); /* b */
                p.close();
                
                canvas.drawPath(p, bmiPaintTriangle);
            }

            if (text != null && textPaint != null)
                canvas.drawText(text, x + (h / 10), barRect.centerY() - (h / 8), textPaint);
        } else {
            /* vertical */
            float y = Math.round(h * (1.0f - (bmi - minBmi) / (maxBmi - minBmi)));
            canvas.drawLine(barOffset / 2, y, w, y, bmiPaint);
            
            if (bmiPaintTriangle != null) {
                float len = barOffset * 0.6f;
                
                Path p = new Path();
                p.moveTo(barOffset / 2, y - (len / 2)); /* bl */
                p.lineTo(barOffset / 2, y + (len / 2)); /* tl */
                p.lineTo((barOffset / 2) + (float)((len / 2) / Math.sin(Math.PI / 6)), y); /* r */
                p.close();
                
                canvas.drawPath(p, bmiPaintTriangle);
            }

            if (text != null && textPaint != null)
                canvas.drawText(text, barRect.centerX(), y - (w / 10), textPaint);
        }
    }

    private void renderStatic()
    {
        if (bmpStatic == null)
            return;

        Canvas canvas = new Canvas(bmpStatic);

        for (ScaleRegion rgn : regions)
            canvas.drawRect(rgn.rect, rgn.paint);

        for (ScaleRegionBoundary sm : regionBoundaries) {
            canvas.drawRect(sm.rect, sm.paint);
            if (DRAW_REGION_SEPARATORS)
                canvas.drawLine(sm.lx0, sm.ly0, sm.lx1, sm.ly1, paintSeparator);
        }

        if (w > h) {
            /* horizontal */
            canvas.drawLine(0, barOffset, w, barOffset, paintLine);
        } else {
            /* vertical */
            canvas.drawLine(barOffset, 0, barOffset, h, paintLine);
        }
    }
}

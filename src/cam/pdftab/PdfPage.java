package cam.pdftab;
import cam.pdftab.PdfActivity;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class PdfPage
{
	private final String TAG = "PdfTab";
	private Context context;
	private PdfCore doc;
	private Bitmap screenBitmap;
	private int screenWidth, screenHeight;
	private int screenPageWidth, screenPageHeight;
	private int docPageWidth, docPageHeight;
	private float screenScale, pageScale;
	private int pageNum;
	private int cropLineTop, cropLineLeft, cropLineRight, cropLineBottom;
	private int screenBorderSize = 0;

	public PdfPage(Context context, PdfCore doc, int pageNum)
	{
		this.context = context;
		this.doc = doc;
		this.pageNum = pageNum;
	}

	public int getPageNum() { return pageNum; }
	public int getPageWidth() { return screenPageWidth; }
	public int getPageHeight() { return screenPageHeight + screenBorderSize * 2; }

	private int getCropPageWidth() { return cropLineRight - cropLineLeft; }
	private int getCropPageHeight() { return cropLineBottom - cropLineTop; }

	private int getScreenWidth() { return screenWidth - screenBorderSize * 2; }
	private int getScreenHeight() { return screenHeight - screenBorderSize * 2; }

	public void setScreenPadding(int border)
	{
		screenBorderSize = border;
	}

	public void setScreenInfo(int width, int height, int format)
	{
		screenWidth = width;
		screenHeight = height;
		init(format);
	}

	private void init(int format)
	{
		doc.gotoPage(pageNum);
		docPageHeight = (int)doc.pageHeight;
		docPageWidth = (int)doc.pageWidth;

		SharedPreferences settings = context.getSharedPreferences("pdf_files", Activity.MODE_PRIVATE);
		cropLineLeft = settings.getInt("crop:" + doc.path + ":left", 0);
		cropLineTop = settings.getInt("crop:" + doc.path + ":top", 0);
		cropLineRight = settings.getInt("crop:" + doc.path + ":right", (int)docPageWidth);
		cropLineBottom = settings.getInt("crop:" + doc.path + ":bottom", (int)docPageHeight);

		//Log.v(TAG, "screenWidth = " + getScreenWidth());
		//Log.v(TAG, "screenHeight = " + getScreenHeight());
		//Log.v(TAG, "pageWidth = " + docPageWidth);
		//Log.v(TAG, "pageHeight = " + docPageHeight);
		//Log.v(TAG, "cropLineTop = " + cropLineTop);
		//Log.v(TAG, "cropLineLeft = " + cropLineLeft);
		//Log.v(TAG, "cropLineBottom = " + cropLineBottom);
		//Log.v(TAG, "cropLineRight = " + cropLineRight);
		//Log.v(TAG, "cropWidth = " + getCropPageWidth());
		//Log.v(TAG, "cropHeight = " + getCropPageHeight());

		float screenScaleX = (float)getScreenWidth() / getCropPageWidth();
		float screenScaleY = (float)getScreenHeight() / getCropPageHeight();

		if(getScreenWidth() < getScreenHeight())
		{
			if(screenScaleX < screenScaleY)
			{
				pageScale = (float)docPageWidth / getCropPageWidth();
				screenScale = screenScaleX;
			}
			else
			{
				pageScale = (float)docPageHeight / getCropPageHeight();
				screenScale = screenScaleY;
			}
		}
		else
		{
			pageScale = (float)doc.pageWidth / getCropPageWidth();
			screenScale = screenScaleX;
		}

		//Log.v(TAG, "pageScale = " + pageScale);
		//Log.v(TAG, "screenScale = " + screenScale);

		screenPageWidth = (int)(getCropPageWidth() * screenScale + 0.5);
		screenPageHeight = (int)(getCropPageHeight() * screenScale + 0.5);

		renderPage(format);
	}

	private void renderPage(int format)
	{
		//long begin1 = System.currentTimeMillis();

		int size = screenPageWidth * screenPageHeight;
		int[] pixelBuffer = doc.renderPage(
			(int)(docPageWidth * screenScale), (int)(docPageHeight * screenScale),
			(int)(cropLineLeft * screenScale), (int)(cropLineTop * screenScale),
			screenPageWidth, screenPageHeight);

		//long duration1 = System.currentTimeMillis() - begin1;
		//long begin2 = System.currentTimeMillis();

		if(format == PixelFormat.RGB_565)
			screenBitmap = Bitmap.createBitmap(screenPageWidth, screenPageHeight,
				Bitmap.Config.RGB_565);
		else
			screenBitmap = Bitmap.createBitmap(screenPageWidth, screenPageHeight,
				Bitmap.Config.ARGB_8888);

		//long duration2 = System.currentTimeMillis() - begin2;
		//long begin3 = System.currentTimeMillis();

		screenBitmap.setPixels(pixelBuffer,
			0, screenPageWidth,
			0, 0,
			screenPageWidth, screenPageHeight);

		//long duration3 = System.currentTimeMillis() - begin3;
		//Log.v(TAG, String.format("render : %d,%d,%d ms", duration1, duration2, duration3));
	}

	public int findLink(int x, int y)
	{
		return doc.findLink(pageNum,
			(int)(docPageWidth * screenScale), (int)(docPageHeight * screenScale),
			(int)(cropLineLeft * screenScale), (int)(cropLineTop * screenScale),
			screenPageWidth, screenPageHeight,
			x, y);
	}

	public void blit(Canvas c, int x, int y)
	{
		if(screenBitmap == null)
			return;

		int destX = x;
		int destY = y;
		int destW = screenPageWidth;
		int destH = screenPageHeight;
		int srcX = 0;
		int srcY = 0;

		Rect border = new Rect(destX+screenBorderSize, destY,
			destX+screenBorderSize+destW, destY+destH);

		//only blit a screen-full
		if(destH > screenHeight)
			destH = screenHeight;
		if(destW > screenWidth)
			destW = screenWidth;

		//negative Y
		if(destY < 0)
		{
			//start into the bitmap destY lines
			srcY = -destY;

			//due to overscroll we may not actually have enough page for this offset
			if(destH - destY > screenPageHeight)
				destH = screenPageHeight + destY;

			//now draw at the top of the screen
			destY = 0;
		}
		else if(destY > 0)
		{
			destH = screenHeight - destY;
		}

		//max of the available page
		if(destH > screenPageHeight)
			destH = screenPageHeight;

		//Log.v(TAG, "blit height="+destH+"/"+screenHeight+" @ "+destY);
		if(destH > 0)
		{
			//long begin = System.currentTimeMillis();

			Rect src = new Rect(0, srcY, destW, srcY+destH);
			RectF dest = new RectF(destX+screenBorderSize, destY,
				destX+screenBorderSize+destW, destY+destH);

			c.drawBitmap(screenBitmap, src, dest, (Paint)null);

			if(screenBorderSize > 0)
			{
				Paint paintRect = new Paint();
				paintRect.setStrokeWidth(1);
				paintRect.setColor(0xFF707070);
				paintRect.setStyle(Paint.Style.STROKE);

				c.drawRect(border, paintRect);
			}

			//c.drawBitmap(screenBitmap, offset, screenPageWidth,
				//destX+screenBorderSize, destY+screenBorderSize, destW, destH,
				//false, (Paint)null);

			//long duration = System.currentTimeMillis() - begin;
			//Log.v(TAG, String.format("  blit : %d ms", duration));
		}
	}
}

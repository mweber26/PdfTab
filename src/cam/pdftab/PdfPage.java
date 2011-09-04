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
	private int[] pixelBuffer;
	private int screenWidth, screenHeight;
	private int screenPageWidth, screenPageHeight;
	private int docPageWidth, docPageHeight;
	private float screenScale, pageScale;
	private int pageNum;
	private int cropLineTop, cropLineLeft, cropLineRight, cropLineBottom;

	public PdfPage(Context context, PdfCore doc, int pageNum)
	{
		this.context = context;
		this.doc = doc;
		this.pageNum = pageNum;
	}

	public int getPageNum() { return pageNum; }
	public int getPageWidth() { return screenPageWidth; }
	public int getPageHeight() { return screenPageHeight; }

	private int getCropPageWidth() { return cropLineRight - cropLineLeft; }
	private int getCropPageHeight() { return cropLineBottom - cropLineTop; }

	public void setScreenSize(int width, int height)
	{
		screenWidth = width;
		screenHeight = height;
		init();
	}

	private void init()
	{
		doc.gotoPage(pageNum);
		docPageHeight = (int)doc.pageHeight;
		docPageWidth = (int)doc.pageWidth;

		SharedPreferences settings = context.getSharedPreferences("pdf_files", Activity.MODE_PRIVATE);
		cropLineLeft = settings.getInt("crop:" + doc.path + ":left", 0);
		cropLineTop = settings.getInt("crop:" + doc.path + ":top", 0);
		cropLineRight = settings.getInt("crop:" + doc.path + ":right", (int)docPageWidth);
		cropLineBottom = settings.getInt("crop:" + doc.path + ":bottom", (int)docPageHeight);

		Log.v(TAG, "screenWidth = " + screenWidth);
		Log.v(TAG, "screenHeight = " + screenHeight);
		Log.v(TAG, "pageWidth = " + docPageWidth);
		Log.v(TAG, "pageHeight = " + docPageHeight);
		Log.v(TAG, "cropLineTop = " + cropLineTop);
		Log.v(TAG, "cropLineLeft = " + cropLineLeft);
		Log.v(TAG, "cropLineBottom = " + cropLineBottom);
		Log.v(TAG, "cropLineRight = " + cropLineRight);
		Log.v(TAG, "cropWidth = " + getCropPageWidth());
		Log.v(TAG, "cropHeight = " + getCropPageHeight());

		float screenScaleX = (float)screenWidth / getCropPageWidth();
		float screenScaleY = (float)screenHeight / getCropPageHeight();

		if(screenWidth < screenHeight)
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

		Log.v(TAG, "pageScale = " + pageScale);
		Log.v(TAG, "screenScale = " + screenScale);

		screenPageWidth = (int)(getCropPageWidth() * screenScale + 0.5);
		screenPageHeight = (int)(getCropPageHeight() * screenScale + 0.5);

		renderPage();
	}

	private void renderPage()
	{
		int size = screenPageWidth * screenPageHeight;
		if(pixelBuffer == null || pixelBuffer.length != size)
		{
			pixelBuffer = doc.renderPage(
				(int)(docPageWidth * screenScale), (int)(docPageHeight * screenScale),
				(int)(cropLineLeft * screenScale), (int)(cropLineTop * screenScale),
				screenPageWidth, screenPageHeight);
		}
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
		if(pixelBuffer == null)
			return;

		int offset = 0; //offset into the source
		int destX = x;
		int destY = y;
		int destW = screenPageWidth;
		int destH = screenPageHeight;

		//only blit a screen-full
		if(destH > screenHeight)
			destH = screenHeight;
		if(destW > screenWidth)
			destW = screenWidth;

		//negative Y
		if(destY < 0)
		{
			//start into the bitmap destY lines
			offset = -destY * screenPageWidth;

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
		c.drawBitmap(pixelBuffer, offset, screenPageWidth,
			destX, destY, destW, destH,
			false, (Paint)null);
	}
}

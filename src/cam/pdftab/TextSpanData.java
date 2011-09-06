package cam.pdftab;

import android.graphics.*;
import android.util.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class TextSpanData
{
	private final String TAG = "PdfTab";
	private String text;
	private ArrayList<TextSpanArea> spanAreas = new ArrayList<TextSpanArea>();
	private TextSpanArea currentArea = new TextSpanArea();

	public void setString(String data)
	{
		spanAreas.add(currentArea);
		currentArea = null;

		Log.v(TAG, String.format("setString with data.length = %d", data.length()));
	}

	public void addRect(int left, int top, int right, int bottom)
	{
		boolean newLine = false;

		//newline from mupdf
		if(left == -1 && top == -1 && right == -1 && bottom == -1)
			newLine = true;

		if(newLine)
		{
			spanAreas.add(currentArea);
			currentArea = new TextSpanArea();
		}
		else
		{
			currentArea.addRect(left, top, right, bottom);
		}
	}

	public void draw(Canvas c, int x, int y)
	{
		Paint paintDim = new Paint();
		paintDim.setColor(0x2000FF00);

		for(TextSpanArea area : spanAreas)
		{
			c.drawRect(area.bounds.left + x, area.bounds.top + y,
				area.bounds.right + x, area.bounds.bottom + y, paintDim);
		}
	}

	private class TextSpanArea
	{
		private ArrayList<Rect> rects = new ArrayList<Rect>();
		protected Rect bounds = new Rect();

		public void addRect(int left, int top, int right, int bottom)
		{
			Rect r = new Rect(left, top, right, bottom);
			bounds.union(r);
			rects.add(r);
		}
	}
}

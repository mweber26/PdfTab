package cam.pdftab;
import cam.pdftab.PixmapView;

import java.io.*;
import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import android.util.*;

public class CropActivity extends Activity
{
	private final String TAG = "PdfTab";
	private final int edgeOffset = 100;
	private final int handleGrow = 30;
	private int movingCropLine = -1;
	private int pageX, pageY, pageW, pageH;
	private int cropLineTop, cropLineLeft, cropLineRight, cropLineBottom;
	private Drawable cropLeft, cropRight, cropTop, cropBottom;
	private Rect cropTopRect, cropLeftRect, cropRightRect, cropBottomRect;
	private CropView view;
	private PdfCore core;
	private int[] pagePixels;
	private float pageScale;

	@Override public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		int pageIndex = intent.getIntExtra("pageIndex", 0);

		setContentView(R.layout.crop);

		view = new CropView(this);
		FrameLayout frame = (FrameLayout)findViewById(R.id.pdfview);
		frame.addView(view);

		core = PdfActivity.getPdfCore();
		core.gotoPage(pageIndex);

		cropLeft = getResources().getDrawable(R.drawable.crop_left);
		cropTop = getResources().getDrawable(R.drawable.crop_top);
		cropRight = getResources().getDrawable(R.drawable.crop_right);
		cropBottom = getResources().getDrawable(R.drawable.crop_bottom);

		SharedPreferences settings = getSharedPreferences("pdf_files", Activity.MODE_PRIVATE);
		cropLineLeft = settings.getInt("crop:" + core.path + ":left", 0);
		cropLineTop = settings.getInt("crop:" + core.path + ":top", 0);
		cropLineRight = settings.getInt("crop:" + core.path + ":right", (int)core.pageWidth);
		cropLineBottom = settings.getInt("crop:" + core.path + ":bottom", (int)core.pageHeight);
	}

	protected void computePageOffsets()
	{
		float scaleX = (float)(view.getWidth() - 2 * edgeOffset) / core.pageWidth;
		float scaleY = (float)(view.getHeight() - 2 * edgeOffset) / core.pageHeight;

		if(scaleX < scaleY)
			pageScale = scaleX;
		else
			pageScale = scaleY;

		pageW = (int)(core.pageWidth * pageScale + 0.5);
		pageH = (int)(core.pageHeight * pageScale + 0.5);
		pageX = (view.getWidth() - pageW) / 2;
		pageY = (view.getHeight() - pageH) / 2;

		renderPage();
		view.invalidate();
	}

	private void renderPage()
	{
		pagePixels = core.renderPage(pageW, pageH, 0, 0, pageW, pageH);
	}

	public void onSave(View view)
	{
		SharedPreferences settings = getSharedPreferences("pdf_files", Activity.MODE_PRIVATE);
		SharedPreferences.Editor edit = settings.edit();
		edit.putInt("crop:" + core.path + ":left", cropLineLeft);
		edit.putInt("crop:" + core.path + ":top", cropLineTop);
		edit.putInt("crop:" + core.path + ":right", cropLineRight);
		edit.putInt("crop:" + core.path + ":bottom", cropLineBottom);
		edit.commit();
		finish();
	}

	private int clickOffset = 0;
	private boolean processCrop(MotionEvent event)
	{
		int screenTop = (int)(pageScale * cropLineTop);
		int screenLeft = (int)(pageScale * cropLineLeft);
		int screenRight = (int)(pageScale * cropLineRight);
		int screenBottom = (int)(pageScale * cropLineBottom);

		switch(event.getAction() & MotionEvent.ACTION_MASK)
		{
			case MotionEvent.ACTION_DOWN:
			{
				if(cropTopRect.contains((int)event.getX(), (int)event.getY()))
				{
					movingCropLine = 0;
					clickOffset = (int)event.getY() - screenTop - pageY;
				}
				else if(cropRightRect.contains((int)event.getX(), (int)event.getY()))
				{
					movingCropLine = 1;
					clickOffset = (int)event.getX() - screenRight - pageX;
				}
				else if(cropBottomRect.contains((int)event.getX(), (int)event.getY()))
				{
					movingCropLine = 2;
					clickOffset = (int)event.getY() - screenBottom - pageY;
				}
				else if(cropLeftRect.contains((int)event.getX(), (int)event.getY()))
				{
					movingCropLine = 3;
					clickOffset = (int)event.getX() - screenLeft - pageX;
				}

				Log.v(TAG, "clickOffset = " + clickOffset + " mcl = " + movingCropLine);

				if(movingCropLine >= 0)
					return true;
				break;
			}
			case MotionEvent.ACTION_MOVE:
			{
				if(movingCropLine >= 0)
				{
					if(movingCropLine == 0)
						cropLineTop = (int)((event.getY() - pageY - clickOffset) / pageScale);
					if(movingCropLine == 1)
						cropLineRight = (int)((event.getX() - pageX - clickOffset) / pageScale);
					if(movingCropLine == 2)
						cropLineBottom = (int)((event.getY() - pageY - clickOffset) / pageScale);
					if(movingCropLine == 3)
						cropLineLeft = (int)((event.getX() - pageX - clickOffset) / pageScale);

					if(cropLineTop < 0) cropLineTop = 0;
					if(cropLineLeft < 0) cropLineLeft = 0;
					if(cropLineRight > (int)core.pageWidth) cropLineRight = (int)core.pageWidth;
					if(cropLineBottom > (int)core.pageHeight) cropLineBottom = (int)core.pageHeight;

					view.invalidate();
					return true;
				}
				break;
			}
			case MotionEvent.ACTION_UP:
			{
				if(movingCropLine >= 0)
				{
					movingCropLine = -1;
					return true;
				}
				break;
			}
		}

		return false;
	}

	private class CropView extends View
	{
		private int w, h;
		public CropView(Context context)
		{
			super(context);
			setFocusable(true);
		}

		@Override public boolean onTouchEvent(final MotionEvent event)
		{
			if(!processCrop(event))
				return super.onTouchEvent(event);
			else
				return true;
		}

		public void onDraw(Canvas canvas)
		{
			if(w != getWidth() || h != getHeight())
			{
				w = getWidth();
				h = getHeight();
				computePageOffsets();
			}

			int screenLeft = (int)(pageScale * cropLineLeft);
			int screenTop = (int)(pageScale * cropLineTop);
			int screenRight = (int)(pageScale * cropLineRight);
			int screenBottom = (int)(pageScale * cropLineBottom);

			Paint paintLine = new Paint();
			paintLine.setStrokeWidth(1);
			paintLine.setColor(0xFFDA1F28);

			Paint paintDim = new Paint();
			paintDim.setColor(0x80000000);

			canvas.drawRGB(0, 0, 0);
			canvas.drawBitmap(pagePixels, 0, pageW, pageX, pageY, pageW, pageH, false, (Paint)null);

			canvas.drawRect(0, 0, screenLeft + pageX, getHeight(), paintDim);
			canvas.drawRect(screenRight + pageX, 0, getWidth(), getHeight(), paintDim);
			canvas.drawRect(screenLeft + pageX + 1, 0, screenRight + pageX, screenTop + pageY,
				paintDim);
			canvas.drawRect(screenLeft + pageX + 1, screenBottom + pageY + 1,
				screenRight + pageX, getHeight(), paintDim);

			int cw = screenRight - screenLeft;
			int ch = screenBottom - screenTop;

			canvas.drawLine(0, pageY+screenTop, getWidth(), pageY+screenTop, paintLine);
			cropTopRect = new Rect(
				pageX + screenLeft + cw/2 - cropTop.getIntrinsicWidth()/2,
				pageY + screenTop + 1,
				pageX + screenLeft + cw/2 + cropTop.getIntrinsicWidth()/2,
				pageY + screenTop + cropTop.getIntrinsicHeight() + 1);
			cropTop.setBounds(cropTopRect);
			cropTop.draw(canvas);

			canvas.drawLine(pageX+screenLeft, 0, pageX+screenLeft, getHeight(), paintLine);
			cropLeftRect = new Rect(
				pageX + screenLeft + 1,
				pageY + ch/2 + screenTop - cropLeft.getIntrinsicHeight()/2,
				pageX + screenLeft + cropLeft.getIntrinsicWidth() + 1,
				pageY + ch/2 + screenTop + cropLeft.getIntrinsicHeight()/2);
			cropLeft.setBounds(cropLeftRect);
			cropLeft.draw(canvas);

			canvas.drawLine(0, pageY+screenBottom, getWidth(), pageY+screenBottom, paintLine);
			cropBottomRect = new Rect(
				pageX + screenLeft + cw/2 - cropBottom.getIntrinsicWidth()/2,
				pageY + screenBottom - cropBottom.getIntrinsicHeight(),
				pageX + screenLeft + cw/2 + cropBottom.getIntrinsicWidth()/2,
				pageY + screenBottom);
			cropBottom.setBounds(cropBottomRect);
			cropBottom.draw(canvas);

			canvas.drawLine(pageX+screenRight, 0, pageX+screenRight, getHeight(), paintLine);
			cropRightRect = new Rect(
				pageX + screenRight - cropRight.getIntrinsicWidth(),
				pageY + ch/2 + screenTop - cropRight.getIntrinsicHeight()/2,
				pageX + screenRight,
				pageY + ch/2 + screenTop + cropRight.getIntrinsicHeight()/2);
			cropRight.setBounds(cropRightRect);
			cropRight.draw(canvas);

			cropTopRect = grow(cropTopRect, handleGrow, handleGrow);
			cropLeftRect = grow(cropLeftRect, handleGrow, handleGrow);
			cropBottomRect = grow(cropBottomRect, handleGrow, handleGrow);
			cropRightRect = grow(cropRightRect, handleGrow, handleGrow);
		}

		private Rect grow(Rect r, int w, int h)
		{
			return new Rect(r.left - w/2, r.top - h/2, r.right + w/2, r.bottom + h/2);
		}
	}
}

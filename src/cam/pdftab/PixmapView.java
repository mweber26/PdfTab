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

public class PixmapView extends SurfaceView
	implements SurfaceHolder.Callback, GestureDetector.OnGestureListener,
	ScaleGestureDetector.OnScaleGestureListener
{
	protected final static String TAG = "PdfTab";
	protected final GestureDetector gestureDetector;
	protected final ScaleGestureDetector scaleGestureDetector;
	private PdfActivity activity;
	private SurfaceHolder holder;
	private PdfThread thread = null;
	private PdfCore doc;
	private boolean isScaling = false;
	private int screenWidth, screenHeight;
	private int threadInitialPage = 0;

	public PixmapView(PdfActivity activity, PdfCore doc)
	{
		super(activity);

		this.doc = doc;
		this.activity = activity;
		this.gestureDetector = new GestureDetector(activity, this);
		this.scaleGestureDetector = new ScaleGestureDetector(activity, this);

		holder = getHolder();
		holder.addCallback(this);
		setFocusable(true);
	}

	public void setPage(int pageIndex)
	{
		Log.v(TAG, "setPage(" + pageIndex + ")");
		if(thread == null)
			threadInitialPage = pageIndex;
		else
			thread.setPage(pageIndex);
	}

	public int getPage()
	{
		return thread.getCurrentPage();
	}

	public int getNumPages()
	{
		return doc.numPages;
	}

	public void back()
	{
		thread.back();
	}

	@Override public boolean onTouchEvent(final MotionEvent event)
	{
		if(!activity.canAcceptPageActions())
			return false;

		switch(event.getAction() & MotionEvent.ACTION_MASK)
		{
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP:
			{
				thread.springBack();
			}
		}

		scaleGestureDetector.onTouchEvent(event);
		if(scaleGestureDetector.isInProgress() || isScaling)
			return true;
		if(gestureDetector.onTouchEvent(event))
			return true;
		return super.onTouchEvent(event);
	}

	@Override public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		return super.onKeyDown(keyCode, event);
	}

	@Override public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		return super.onKeyUp(keyCode, event);
	}

	@Override public boolean onDown(MotionEvent e)
	{
		return true;
	}

	@Override public boolean onScaleBegin(ScaleGestureDetector detector)
	{
		if(!activity.canAcceptPageActions())
			return false;

		Log.d(TAG, "onScaleBegin()");
		isScaling = true;
		return true;
	}

	@Override public boolean onScale(ScaleGestureDetector detector)
	{
		if(!activity.canAcceptPageActions())
			return false;

		Log.d(TAG, "onScale()");
		return true;
	}

	@Override public void onScaleEnd(ScaleGestureDetector detector)
	{
		if(!activity.canAcceptPageActions())
			return;

		Log.d(TAG, "onScaleEnd()");
		isScaling = false;
	}

	@Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
	{
		if(!activity.canAcceptPageActions())
			return false;
		if(isScaling)
			return false;

		thread.fling(velocityX, velocityY);
		return true;
	}

	@Override public void onLongPress(MotionEvent e)
	{
		if(!activity.canAcceptPageActions())
			return;
		if(isScaling)
			return;

		Log.d(TAG, "onLongPress(): ignoring!");
	}

	@Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
	{
		if(!activity.canAcceptPageActions())
			return false;
		if(isScaling)
			return false;

		thread.scroll(distanceX, distanceY);
		return true;
	}

	@Override public void onShowPress(MotionEvent e)
	{
		if(!activity.canAcceptPageActions())
			return;
		if(isScaling)
			return;

		Log.d(TAG, "onShowPress(): ignoring!");
	}

	@Override public boolean onSingleTapUp(MotionEvent e)
	{
		if(!activity.canAcceptPageActions())
			return false;
		if(isScaling)
			return false;

		thread.onTap(e.getX(), e.getY());
		return true;
	}

	public void surfaceCreated(SurfaceHolder holder)
	{
		thread = new PdfThread(holder);
		thread.start();
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{
		screenWidth = width;
		screenHeight = height;
		thread.screenChanged(width, height);
	}

	public void surfaceDestroyed(SurfaceHolder holder)
	{
		boolean retry = true;

		thread.stopRunning();
		while(retry)
		{
			try
			{
				thread.join();
				retry = false;
			}
			catch(InterruptedException e)
			{
			}
		}

		thread = null;
		Log.v(TAG, "surfaceDestroyed");
	}

	class PdfThread extends Thread
	{
		private final int pageTurnDuration = 500;
		private final int pageTurnSpacing = 50;
		private final OverScroller scroller;
		private SurfaceHolder holder;
		private boolean running = false;
		private int screenWidth, screenHeight;
		private PdfPageLayout currentPage, nextPage, prevPage;
		private boolean isWaiting = false;
		private long pageTurnAnimationStart = -1;
		private int pageTurnOffset;
		private boolean pageTurnRight = false;
		private int scrollingOffsetX = 0;
		private java.util.Stack<Integer> stack = new java.util.Stack<Integer>();

		public PdfThread(SurfaceHolder holder)
		{
			running = true;
			this.holder = holder;
			this.scroller = new OverScroller(activity);
		}

		public OverScroller getScroller() { return scroller; }
		public int getCurrentPage() { return currentPage.getPageNum() + 1; }

		public void setPage(int pageIndex)
		{
			stack.push(currentPage.getPageNum());
			currentPage = new PdfPageLayout(pageIndex);
			prevPage = null;
			nextPage = null;
			redraw();
		}

		public void stopRunning()
		{
			running = false;
			interrupt();
		}
	
		public void redraw()
		{
			interrupt();
		}

		public void back()
		{
			if(stack.size() > 0)
			{
				int newPage = (int)stack.pop();

				currentPage = new PdfPageLayout(newPage);
				prevPage = null;
				nextPage = null;
				redraw();
				updateActivityCurrentPage();
			}
		}

		private void updateActivityCurrentPage()
		{
			activity.showCurrentPage();
		}

		public void changePage(int pageNum)
		{
			currentPage = new PdfPageLayout(pageNum);
			prevPage = null;
			nextPage = null;
			redraw();
			updateActivityCurrentPage();
		}

		public void screenChanged(int width, int height)
		{
			this.screenWidth = width;
			this.screenHeight = height;

			if(currentPage != null)
				currentPage.setScreenSize(width, height);
			redraw();
		}

		public void springBack()
		{
			//if(!isAnimating())
				//currentPage.springBack();
		}

		public void scroll(float distanceX, float distanceY)
		{
			//if(!isAnimating())
				//currentPage.scroll(distanceX, distanceY);
		}

		public void fling(float velocityX, float velocityY)
		{
			//only fling if we aren't already moving
			if(!isAnimating())
			{
				verticalFlingSinglePage(velocityY);

				//if(Math.abs(velocityX) > Math.abs(velocityY))
					//currentPage.pageTurn(velocityX);
				//else
					//currentPage.fling(velocityX, velocityY);
			}
		}

		private void verticalFlingSinglePage(float velocityY)
		{
			if(screenHeight >= currentPage.getPageHeight() && screenWidth >= currentPage.getPageWidth())
				return;

			Log.d(TAG, "verticalFlingSinglePage(" + velocityY + ")");
			scroller.fling(
				currentPage.offsetX, currentPage.offsetY,
				0, -(int)(velocityY * 1.25),
				0, 0,
				0, currentPage.getPageHeight() - screenHeight,
				0, 50);
			redraw();
		}

		private void startPageTurnAnimation()
		{
			if(!isAnimating())
			{
				pageTurnAnimationStart = AnimationUtils.currentAnimationTimeMillis();
				redraw();
			}
		}

		private boolean isAnimating()
		{
			return scroller.computeScrollOffset() || (pageTurnAnimationStart >= 0);
		}

		public void onTap(float x, float y)
		{
			if(!isAnimating())
			{
				int pageLink = currentPage.findLink((int)x, (int)y);

				if(pageLink >= 0)
				{
					changePage(pageLink);
				}
				else if(x >= screenWidth - screenWidth / 4 && currentPage.getPageNum() < doc.numPages)
				{
					startPageTurnAnimation();
					pageTurnRight = true;
				}
				else if(x <= screenWidth / 4 && currentPage.getPageNum() > 0)
				{
					startPageTurnAnimation();
					pageTurnRight = false;
				}
				else if(x >= screenWidth / 4 && x <= screenWidth * 3 / 4 &&
					y >= screenHeight / 4 && y <= screenHeight * 3 / 4)
				{
					activity.clickInControlRegion(); 
				}
			}
		}

		public void run()
		{
			changePage(threadInitialPage);

			while(running)
			{
				boolean doSleep = true;
				Canvas c = null;

				try
				{
					c = holder.lockCanvas(null);
					c.drawRGB(0, 0, 0);

					if(pageTurnAnimationStart > 0)
					{
						doSleep = false;

						final long time = AnimationUtils.currentAnimationTimeMillis();
						final long duration = time - pageTurnAnimationStart;

						if(duration >= pageTurnDuration)
						{
							scrollingOffsetX = 0;
							pageTurnAnimationStart = -1;

							stack.push(currentPage.getPageNum());

							if(pageTurnRight)
							{
								prevPage = currentPage;
								currentPage = nextPage;
								nextPage = null;
							}
							else
							{
								PdfPageLayout p = currentPage;
								currentPage = prevPage;
								nextPage = p;
								prevPage = null;
							}

							updateActivityCurrentPage();
						}
						else
						{
							pageTurnOffset = (int)(((screenWidth + pageTurnSpacing) *
								duration) / pageTurnDuration);
						}
					}
					else
					{
						if(!scroller.isFinished())
						{
							doSleep = false;

							if(scroller.computeScrollOffset())
							{
								scrollingOffsetX = scroller.getCurrX();
								currentPage.offsetY = scroller.getCurrY();
							}
							else
							{
								scroller.abortAnimation();
							}
						}
					}

					drawPage(c);
				}
				finally
				{
					if(c != null)
						holder.unlockCanvasAndPost(c);
				}

				if(doSleep)
				{
					if(nextPage == null && currentPage.getPageNum() < doc.numPages)
						nextPage = new PdfPageLayout(currentPage.getPageNum() + 1);
					
					if(prevPage == null && currentPage.getPageNum() > 0)
						prevPage = new PdfPageLayout(currentPage.getPageNum() - 1);

					try { sleep(3600000); }
					catch(Exception e) { }
				}
			}
		}

		protected void drawPage(Canvas c)
		{
			if(pageTurnAnimationStart > 0)
			{
				if(pageTurnRight)
				{
					currentPage.blit(c, -pageTurnOffset);
					nextPage.blit(c, screenWidth - pageTurnOffset + pageTurnSpacing);
				}
				else
				{
					currentPage.blit(c, pageTurnOffset);
					prevPage.blit(c, -screenWidth - pageTurnSpacing + pageTurnOffset);
				}
			}
			else if(scrollingOffsetX != 0)
			{
				/*if(scrollingOffsetX > 0)
				{
					currentPage.blit(c, -scrollingOffsetX);
					nextPage.blit(c, screenWidth - scrollingOffsetX + pageTurnSpacing);
				}
				else
				{
					currentPage.blit(c, -scrollingOffsetX);
					prevPage.blit(c, -screenWidth - pageTurnSpacing - scrollingOffsetX);
				}

				if(Math.abs(scrollingOffsetX) > screenWidth / 5)
				{
					scroller.abortAnimation();

					pageTurnRight = scrollingOffsetX > 0;
					startPageTurnAnimation();
					scrollingOffsetX = 0;
				}*/
			}
			else
			{
				currentPage.blit(c, 0);
			}
		}

		private class PdfPageLayout extends PdfPage
		{
			public int offsetX, offsetY;

			public PdfPageLayout(int pageNum)
			{
				super(activity, doc, pageNum);
				setScreenSize(screenWidth, screenHeight);
			}

			public void blit(Canvas c, int startX)
			{
				super.blit(c, offsetX + startX, -offsetY);
			}
		}
	}
}

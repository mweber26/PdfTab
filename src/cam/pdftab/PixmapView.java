package cam.pdftab;
import cam.pdftab.PdfActivity;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
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
	private PdfCore core;
	private boolean isScaling = false;
	private int screenWidth, screenHeight;

	public PixmapView(PdfActivity activity, PdfCore core)
	{
		super(activity);

		this.core = core;
		this.activity = activity;
		this.gestureDetector = new GestureDetector(activity, this);
		this.scaleGestureDetector = new ScaleGestureDetector(activity, this);
		holder = getHolder();
		holder.addCallback(this);
		setFocusable(true);
	}

	public void setPage(int pageIndex)
	{
		thread.setPage(pageIndex);
	}

	public int getPage()
	{
		return thread.getCurrentPage();
	}

	public int getNumPages()
	{
		return core.numPages;
	}

	public void back()
	{
		thread.back();
	}

	@Override public boolean onTouchEvent(final MotionEvent event)
	{
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
		Log.d(TAG, "onScaleBegin()");
		isScaling = true;
		return true;
	}

	@Override public boolean onScale(ScaleGestureDetector detector)
	{
		Log.d(TAG, "onScale()");
		return true;
	}

	@Override public void onScaleEnd(ScaleGestureDetector detector)
	{
		Log.d(TAG, "onScaleEnd()");
		isScaling = false;
	}

	@Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
	{
		if(isScaling) return false;

		thread.fling(velocityX, velocityY);
		return true;
	}

	@Override public void onLongPress(MotionEvent e)
	{
		if(isScaling) return;

		Log.d(TAG, "onLongPress(): ignoring!");
	}

	@Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
	{
		if(isScaling) return false;

		thread.scroll(distanceX, distanceY);
		return true;
	}

	@Override public void onShowPress(MotionEvent e)
	{
		if(isScaling) return;

		Log.d(TAG, "onShowPress(): ignoring!");
	}

	@Override public boolean onSingleTapUp(MotionEvent e)
	{
		if(isScaling) return false;
		thread.onTap(e.getX(), e.getY());
		return true;
	}

	public void surfaceCreated(SurfaceHolder holder)
	{
		thread = new PdfThread(holder, core);
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
	}

	class PdfThread extends Thread
	{
		private final int pageTurnDuration = 500;
		private final int pageTurnSpacing = 50;
		private final OverScroller scroller;
		private SurfaceHolder holder;
		private boolean running = false;
		private int screenWidth, screenHeight;
		private PdfCore core;
		private PdfPage currentPage, nextPage, prevPage;
		private boolean isWaiting = false;
		private long pageTurnAnimationStart = -1;
		private int pageTurnOffset;
		private boolean pageTurnRight = false;
		private int scrollingOffsetX = 0;
		private java.util.Stack<Integer> stack = new java.util.Stack<Integer>();

		public PdfThread(SurfaceHolder holder, PdfCore core)
		{
			running = true;
			this.holder = holder;
			this.core = core;
			this.scroller = new OverScroller(activity);
		}

		public OverScroller getScroller() { return scroller; }
		public int getCurrentPage() { return currentPage.getPageNum() + 1; }

		public void setPage(int pageIndex)
		{
			stack.push(currentPage.getPageNum());
			currentPage = new PdfPage(pageIndex);
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

				currentPage = new PdfPage(newPage);
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
			currentPage = new PdfPage(pageNum);
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
				currentPage.init();
			redraw();
		}

		public void springBack()
		{
			if(!isAnimating())
				currentPage.springBack();
		}

		public void scroll(float distanceX, float distanceY)
		{
			if(!isAnimating())
				currentPage.scroll(distanceX, distanceY);
		}

		public void fling(float velocityX, float velocityY)
		{
			//only fling if we aren't already moving
			if(!isAnimating())
			{
				Log.d(TAG, "fling(): "+velocityX+","+velocityY);

				//if(Math.abs(velocityX) > Math.abs(velocityY))
					//currentPage.pageTurn(velocityX);
				//else
					currentPage.fling(velocityX, velocityY);
			}
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
				else if(x >= screenWidth - screenWidth / 4 && currentPage.getPageNum() < core.numPages)
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

		/*public void setPageScaleTo(float scale, PointF midpoint)
		{
			float x, y;
			//Convert midpoint (in screen coords) to page coords
			x = (midpoint.x - pageOriginX) / pageScale;
			y = (midpoint.y - pageOriginY) / pageScale;
			//Find new scaled page sizes
			synchronized(this)
			{
				pageWidth = (int)(core.pageWidth * scale + 0.5);
				if(pageWidth < screenWidth / 2)
				{
					scale = screenWidth / 2 / core.pageWidth;
					pageWidth = (int)(core.pageWidth * scale + 0.5);
				}
				pageHeight = (int)(core.pageHeight*scale+0.5);
				if(pageHeight < screenHeight / 2)
				{
					scale = screenHeight / 2 / core.pageHeight;
					pageWidth = (int)(core.pageWidth *scale+0.5);
					pageHeight = (int)(core.pageHeight*scale+0.5);
				}
				pageScale = scale;
				// Now given this new scale, calculate page origins so that x and y are at midpoint
				float xscale = (float)pageWidth /core.pageWidth;
				float yscale = (float)pageHeight/core.pageHeight;
				setPageOriginTo((int)(midpoint.x - x*xscale + 0.5),
						(int)(midpoint.y - y*yscale + 0.5));
			}
		}*/

		public void run()
		{
			changePage(0);

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
							if(pageTurnRight)
							{
								prevPage = currentPage;
								currentPage = nextPage;
								nextPage = null;
							}
							else
							{
								PdfPage p = currentPage;
								currentPage = prevPage;
								nextPage = p;
								prevPage = null;
							}

							stack.push(prevPage.getPageNum());
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
								currentPage.updateScrollPosition(scroller);
							}
							else
							{
								scroller.abortAnimation();
							}
						}
					}

					doDraw(c);
				}
				finally
				{
					if(c != null)
						holder.unlockCanvasAndPost(c);
				}

				if(doSleep)
				{
					if(nextPage == null && currentPage.getPageNum() < core.numPages)
						nextPage = new PdfPage(currentPage.getPageNum() + 1);
					if(prevPage == null && currentPage.getPageNum() > 0)
						prevPage = new PdfPage(currentPage.getPageNum() - 1);

					try { sleep(3600000); }
					catch(Exception e) { }
				}
			}
		}

		protected void doDraw(Canvas c)
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
				if(scrollingOffsetX > 0)
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
				}
			}
			else
			{
				currentPage.blit(c);
			}
		}
		
		public class PdfPage
		{
			private int[] pixelBuffer;
			private int pageWidth, pageHeight;
			private float pageScale;
			private int pageNum;
			private int pageOriginX, pageOriginY;

			public PdfPage(int pageNum)
			{
				this.pageNum = pageNum;
				init();
			}

			public void init()
			{
				core.gotoPage(pageNum);

				pageScale = (float)screenWidth / core.pageWidth;
 
				pageWidth = (int)(core.pageWidth * pageScale + 0.5);
				pageHeight = (int)(core.pageHeight * pageScale + 0.5);

				pageOriginX = -(screenWidth - pageWidth) / 2;
				pageOriginY = -(screenHeight - pageHeight) / 2;

				//if we have a scrolling height then we want to start at the top of the page
				if(pageHeight > screenHeight)
					pageOriginY = 0;

				renderPage();
			}

			public void prepareForPageTurn(PdfPage currentPage)
			{
				pageOriginX = currentPage.pageOriginX;
				pageOriginY = scrollMinY();
			}

			private void renderPage()
			{
				int size = pageWidth * pageHeight;
				if(pixelBuffer == null || pixelBuffer.length != size)
				{
					pixelBuffer = new int[size];
					core.drawPage(pixelBuffer,
						pageWidth, pageHeight,
						0, 0,
						pageWidth, pageHeight);
				}
			}

			public int findLink(int x, int y)
			{
				return core.findLink(pageNum,
					pageWidth, pageHeight,
					0, 0,
					pageWidth, pageHeight,
					x - pageOriginX, y + pageOriginY);
			}

			public int getPageNum() { return pageNum; }

			public boolean updateScrollPosition(OverScroller scroller)
			{
				pageOriginY = scroller.getCurrY();
				return true;
			}

			private int scrollMinX() { return 0; }
			private int scrollMaxX() { return 0; }

			private int scrollMinY()
			{
				if(screenHeight > screenWidth) //portrait
					return -(screenHeight - pageHeight) / 2;
				else
					return 0;
			}

			private int scrollMaxY()
			{
				if(screenHeight > screenWidth) //portrait
					return -(screenHeight - pageHeight) / 2;
				else
					return pageHeight - screenHeight;
			}

			public void springBack()
			{
				getScroller().springBack(
					pageOriginX, pageOriginY,
					scrollMinX(), scrollMaxX(),
					scrollMinY(), scrollMaxY());
				redraw();
			}

			public void scroll(float distanceX, float distanceY)
			{
				//pageOriginX += (int)distanceX;
				pageOriginY += (int)distanceY;
				redraw();
			}

			public void pageTurn(float velocityX)
			{
				thread.getScroller().fling(
					pageOriginX, pageOriginY,
					-(int)(velocityX * 1.25), 0,
					0, 0,
					scrollMinY(), scrollMaxY(),
					pageWidth/3, 0);
				redraw();
			}

			public void fling(float velocityX, float velocityY)
			{
				thread.getScroller().fling(
					pageOriginX, pageOriginY,
					-(int)(velocityX * 1.25), -(int)(velocityY * 1.15),
					scrollMinX(), scrollMaxX(),
					scrollMinY(), scrollMaxY(),
					0, 50);
				redraw();
			}

			public void blit(Canvas c)
			{
				blit(c, 0);
			}

			private void blit(Canvas c, int startX)
			{
				if(pixelBuffer == null)
					return;

				int patchX = -pageOriginX;
				int patchY = -pageOriginY;
				int blitW = screenWidth;
				int blitH = screenHeight;
				int offset = 0;

				if(screenHeight > screenWidth) //portrait
				{
					if(blitW > pageWidth) blitW = pageWidth;
					if(blitH > pageHeight) blitH = pageHeight;
				}
				else
				{
					if(patchY < 0)
					{
						offset = pageOriginY * pageWidth;
						patchY = 0;
						if(blitH + pageOriginY > pageHeight)
							blitH = pageHeight - pageOriginY;
					}

					//if(startX < 0)
						//c.clipRect(new Rect(0, 0, blitH+startX, blitH), Region.Op.REPLACE);
					//else if(startX > 0)
						//c.clipRect(new Rect(startX, 0, blitW, blitH), Region.Op.REPLACE);
				}

				//System.out.println("Blitting bitmap "+blitW+","+blitH+" @ "+
					//patchX+","+patchY+" offset "+(offset / pageWidth));

				c.drawBitmap(pixelBuffer,
					offset, pageWidth,
					patchX + startX, patchY,
					blitW, blitH,
					false, (Paint)null);
			}
		}
	}
}

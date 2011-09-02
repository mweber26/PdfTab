package cam.pdftab;
import android.graphics.*;

public class PdfCore
{
	static { System.loadLibrary("mupdf"); }

	public int pageNum;
	public int numPages;
	public float pageWidth;
	public float pageHeight;

	private static native int openFile(String filename);
	private static native void gotoPageInternal(int localActionPageNum);
	private static native float getPageWidth();
	private static native float getPageHeight();
	public static native void drawPage(int[] pixels,
		int pageW, int pageH,
		int patchX, int patchY,
		int patchW, int patchH);
	public static native int findLink(int pageNum,
		int pageW, int pageH,
		int patchX, int patchY,
		int patchW, int patchH,
		int x, int y);
	public static native void destroying();

	public PdfCore(String filename) throws Exception
	{
		numPages = openFile(filename);
		if(numPages <= 0)
			throw new Exception("Failed to open " + filename);
		pageNum = 0;
	}

	public void gotoPage(int page)
	{
		if(page > numPages-1)
			page = numPages-1;
		else if(page < 0)
			page = 0;

		gotoPageInternal(page);
		this.pageNum = page;
		this.pageWidth = getPageWidth();
		this.pageHeight = getPageHeight();
	}

	public void onDestroy()
	{
		destroying();
	}
}

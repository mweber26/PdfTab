package cam.pdftab;
import cam.pdftab.PixmapView;

import java.io.*;
import android.app.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import android.util.*;

public class PdfActivity extends Activity
{
	private final int currentPageTimeout = 2000;
	private final int controlsTimeout = 8000;
	private Handler handler = new Handler();
	private PdfCore core;
	private PixmapView pdfView;
	private TextView currentpage;
	private SeekBar seeker;
	private View controls;
	private Animation fadeIn;
	private Animation fadeOut;

	private PdfCore openFile()
	{
		String storageState = Environment.getExternalStorageState();
		File path, file;
		PdfCore core;

		if(Environment.MEDIA_MOUNTED.equals(storageState))
		{
			System.out.println("Media mounted read/write");
		}
		else if(Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState))
		{
			System.out.println("Media mounted read only");
		}
		else
		{
			System.out.println("No media at all! Bale!\n");
			return null;
		}
		path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		file = new File(path, "test.pdf");
		System.out.println("Trying to open "+file.toString());
		try
		{
			core = new PdfCore(file.toString());
		}
		catch(Exception e)
		{
			System.out.println(e);
			return null;
		}
		return core;
	}

	@Override public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if(core == null) { core = openFile(); }
		if(core == null) { return; }

		setContentView(R.layout.main);

		pdfView = new PixmapView(this, core);
		FrameLayout frame = (FrameLayout)findViewById(R.id.pdfview);
		frame.addView(pdfView);

		controls = (View)findViewById(R.id.controls);
		currentpage = (TextView)findViewById(R.id.currentpage);
		fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
		fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);

		seeker = (SeekBar)findViewById(R.id.pageseek);
		seeker.setThumbOffset(18);
		seeker.setOnSeekBarChangeListener(seekChangePage);
		seeker.setMax(pdfView.getNumPages() - 1);
	}

	Runnable showPageBadgeTask = new Runnable() {
		public void run() {
			currentpage.setText(String.format("%d of %d",
				pdfView.getPage(), pdfView.getNumPages()));

			if(currentpage.getVisibility() == View.INVISIBLE)
			{
				fadeIn.reset();
				currentpage.startAnimation(fadeIn);
				currentpage.setVisibility(View.VISIBLE);
				handler.postDelayed(hidePageBadgeTask, currentPageTimeout);
			}
			else
			{
				//reset the timeout
				handler.removeCallbacks(hidePageBadgeTask);
				handler.postDelayed(hidePageBadgeTask, currentPageTimeout);
			}
		}
	};

	Runnable showControlsTask = new Runnable() {
		public void run() {
			if(controls.getVisibility() == View.INVISIBLE)
			{
				fadeIn.reset();
				controls.startAnimation(fadeIn);
				controls.setVisibility(View.VISIBLE);
				handler.postDelayed(hideControlsTask, controlsTimeout);
			}
			else
			{
				//reset the timeout
				resetControlTimeout();
			}
		}
	};

	Runnable hideControlsTask = new Runnable() {
		public void run() {
			fadeOut.reset();
			controls.startAnimation(fadeOut);
			controls.setVisibility(View.INVISIBLE);
		}
	};

	Runnable hidePageBadgeTask = new Runnable() {
		public void run() {
			fadeOut.reset();
			currentpage.startAnimation(fadeOut);
			currentpage.setVisibility(View.INVISIBLE);
		}
	};

	public void showCurrentPage()
	{
		handler.post(showPageBadgeTask);
		seeker.setProgress(pdfView.getPage() - 1);
	}

	public void clickInControlRegion()
	{
		if(controls.getVisibility() == View.INVISIBLE)
		{
			handler.post(showControlsTask);
		}
		else
		{
			handler.removeCallbacks(hideControlsTask);
			handler.post(hideControlsTask);
		}
	}

	private void resetControlTimeout()
	{
		//reset the timeout
		handler.removeCallbacks(hideControlsTask);
		handler.postDelayed(hideControlsTask, controlsTimeout);
	}

	private SeekBar.OnSeekBarChangeListener seekChangePage = new SeekBar.OnSeekBarChangeListener() {
		public void onProgressChanged(SeekBar s, int progress, boolean touch) {
			if(touch)
			{
				currentpage.setText(String.format("%d of %d",
					progress + 1, pdfView.getNumPages()));
				resetControlTimeout();
			}
		}

		public void onStartTrackingTouch(SeekBar seekBar) {
			//reset any timeouts
			handler.removeCallbacks(hidePageBadgeTask);

			fadeIn.reset();
			currentpage.startAnimation(fadeIn);
			currentpage.setVisibility(View.VISIBLE);
		}

		public void onStopTrackingTouch(SeekBar seekBar) {
			handler.postDelayed(hidePageBadgeTask, currentPageTimeout);
			pdfView.setPage(seekBar.getProgress());
			resetControlTimeout();
		}
	};

	public void onDestroy()
	{
		if(core != null)
			core.onDestroy();
		core = null;
		super.onDestroy();
	}
}

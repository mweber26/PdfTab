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
	private static final int CROP_RESPONSE = 1;

	private final String TAG = "PdfTab";
	private final int currentPageTimeout = 2000;
	private final int controlsTimeout = 8000;
	private static PdfCore core;
	private Handler handler = new Handler();
	private PixmapView pdfView;
	private TextView currentpage;
	private SeekBar seeker;
	private View controls;
	private Animation fadeIn;
	private Animation fadeOut;
	private Animation slideDown;
	private Animation slideUp;

	public static PdfCore getPdfCore()
	{
		return core;
	}

	@Override public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		if(intent.getAction().equals(Intent.ACTION_VIEW))
		{
ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
activityManager.getMemoryInfo(mi);
Log.i("memory free", "" + mi.availMem);

			if(core != null)
			{
				Log.v(TAG, "reset core for new file");
				core.onDestroy();
				core = null;
				pdfView = null;
			}

			Log.v(TAG, "ACTION_VIEW");
			Log.v(TAG, intent.getData().getPath());
			core = openFile(intent.getData().getPath());
		}
		else
		{
			finish();
		}

		setContentView(R.layout.main);

		pdfView = new PixmapView(this, core);
		FrameLayout frame = (FrameLayout)findViewById(R.id.pdfview);
		frame.addView(pdfView);

		controls = (View)findViewById(R.id.controls);
		currentpage = (TextView)findViewById(R.id.currentpage);
		fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
		fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
		slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down);
		slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);

		seeker = (SeekBar)findViewById(R.id.pageseek);
		seeker.setThumbOffset(18);
		seeker.setOnSeekBarChangeListener(seekChangePage);
		seeker.setMax(pdfView.getNumPages() - 1);
	}

	public void onPause()
	{
		super.onPause();

		SharedPreferences settings = getPreferences(Activity.MODE_PRIVATE);
		settings.edit().putInt("pdf_page:" + core.path, pdfView.getPage() - 1).commit();
		Log.v(TAG, "onPause(page=" + (pdfView.getPage() - 1) + ")");
	}

	public void onResume()
	{
		super.onResume();

		SharedPreferences settings = getPreferences(Activity.MODE_PRIVATE);
		int restorePage = settings.getInt("pdf_page:" + core.path, 0);
		Log.v(TAG, "onResume(page=" + restorePage + ")");
		pdfView.setPage(restorePage);
	}

	private PdfCore openFile(String path)
	{
		try
		{
			return new PdfCore(path);
		}
		catch(Exception e)
		{
			System.out.println(e);
			return null;
		}
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
				slideDown.reset();
				controls.startAnimation(slideDown);
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
			slideUp.reset();
			controls.startAnimation(slideUp);
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

	public void onExit(View view)
	{
		finish();
	}

	public void onCrop(View view)
	{
		Intent intent = new Intent(this, CropActivity.class);
		intent.putExtra("pageIndex", pdfView.getPage() - 1);
		startActivityForResult(intent, CROP_RESPONSE);
	}

	private void onCropFinish()
	{
		Log.v(TAG, "onCropFinish");
		handler.removeCallbacks(showControlsTask);
		handler.removeCallbacks(hideControlsTask);
		controls.setVisibility(View.INVISIBLE);
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if(requestCode == CROP_RESPONSE) onCropFinish();
	}

	public void onBackPressed()
	{
		pdfView.back();
	}

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

	public boolean canAcceptPageActions()
	{
		return true;
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
		Log.v(TAG, "onDestroy");
		if(core != null)
			core.onDestroy();
		core = null;
		pdfView = null;
		super.onDestroy();
	}
}

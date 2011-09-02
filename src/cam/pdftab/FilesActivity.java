package cam.pdftab;
import cam.pdftab.PixmapView;

import java.io.*;
import java.util.*;
import android.app.*;
import android.net.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import android.util.*;

public class FilesActivity extends ListActivity
{
	protected LayoutInflater inflater = null;
	protected File root = new File("/mnt/sdcard/Books");
	protected File currentRoot = new File("/mnt/sdcard/Books");

	public void onCreate(Bundle bundle)
	{
		super.onCreate(bundle);
		this.inflater = getLayoutInflater();

		updateList();
	}

	public void onResume()
	{
		super.onResume();
		updateList();
	}

	public void onBackPressed()
	{
		if(currentRoot.getPath() == root.getPath())
			return;

		currentRoot = currentRoot.getParentFile();
		updateList();
	}

	public void onListItemClick(ListView l, View v, int position, long id)
	{
		File file = (File)getListView().getItemAtPosition(position);
		if(file.isDirectory())
		{
			currentRoot = file;
			updateList();
		}
		else
		{
			Intent intent = new Intent(this, PdfActivity.class);
			intent.setAction(Intent.ACTION_VIEW);
			intent.setData(Uri.fromFile(file));
			startActivity(intent);
		}
	}

	private void updateList()
	{
		ArrayList<File> arr = new ArrayList<File>();
		File[] files = currentRoot.listFiles();

		if(files != null)
		{
			for(File f : files)
			{
				if(f.isDirectory())
					arr.add(f);
				else if(f.isFile() && f.getName().endsWith(".pdf"))
					arr.add(f);
			}

			Collections.sort(arr);
		}

		setListAdapter(new FileAdapter(this, arr));
	}

	private class FileAdapter extends ArrayAdapter<File>
	{
		public FileAdapter(Context context, List<File> items)
		{
			super(context, R.layout.list_item, items);
		}

		@Override public View getView(int position, View convertView, ViewGroup parent)
		{
			View row;
			File item = getItem(position);

			if(null == convertView)
				row = inflater.inflate(R.layout.list_item, null);
			else
				row = convertView;

			ImageView img = (ImageView)row.findViewById(R.id.image);

			if(item.isDirectory())
				img.setImageResource(R.drawable.folder);
			else
				img.setImageResource(R.drawable.pdf);

			TextView tv = (TextView)row.findViewById(R.id.text);
			tv.setText(item.getName());
			return row;
		}
	}
}

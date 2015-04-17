package test.com.testtask;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import test.com.testtask.DBHelper;

public class MainActivity extends ActionBarActivity implements OnClickListener, ScrollViewListener  {

    static String[] s_names = {"britney", "tarja", "alan", "bob", "park", "trevis", "lola"};
    static int s_nameId = 0;

    static DBHelper dbHelper;

    public static String LOG_TAG = "my_log";
    public static MainActivity mainInstance = null;

    static Boolean shouldLoadData = true;
    static Boolean loadProcessEnd = true;

    static LinearLayout s_layoutScroll = null;
    static ScrollViewExt s_scrollViewExt = null;
    static ProgressBar s_spinner = null;

    static int s_readedRecords = 0;

    static String folderName = "";

    static boolean s_touchesEnabled = true;

    @Override
    public void onScrollChanged(ScrollViewExt scrollView, int x, int y, int oldx, int oldy) {
        // We take the last son in the scrollview
        View view = (View) scrollView.getChildAt(scrollView.getChildCount() - 1);
        int diff = (view.getBottom() - (scrollView.getHeight() + scrollView.getScrollY()));

        Log.d(LOG_TAG, String.format("%d" , diff));
        // if diff is zero, then the bottom has been reached
        if (diff == 0 && loadProcessEnd)
        {
            StartLoadJson();
        }
    }

    public void StartLoadJson()
    {
        if (s_nameId < 7)
        {
            new ParseTask().execute(s_names[s_nameId]);
            s_nameId++;
            loadProcessEnd = false;

            s_touchesEnabled = false;

            if (s_spinner != null)
                s_spinner.setVisibility(View.VISIBLE);
        }
    }

    private class ParseTask extends AsyncTask<String, Void, String> {

        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;
        String resultJson = "";
        MainActivity activity;

        @Override
        protected String doInBackground(String... params) {
            // получаем данные с внешнего ресурса
            try {
                URL url = new URL(String.format("https://itunes.apple.com/search?term=%s&entity=musicVideo", params[0]));

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();

                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                }

                resultJson = buffer.toString();

            } catch (Exception e) {
                e.printStackTrace();
            }
            return resultJson;
        }

        @Override
        protected void onPostExecute(String strJson) {
            super.onPostExecute(strJson);
            // выводим целиком полученную json-строку
            Log.d(LOG_TAG, strJson);

            JSONObject dataJsonObj = null;
            String secondName = "";

            try {
                dataJsonObj = new JSONObject(strJson);
                JSONArray results = dataJsonObj.getJSONArray("results");

                mainInstance.AddItems(results);


            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void AddItems(JSONArray array)
    {
        try {

            // подключаемся к БД
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            for (int i = 0; i < array.length(); i++)
            {
                JSONObject object = array.getJSONObject(i);

                String artworkUrl60 = object.getString("artworkUrl60");
                String artworkPath = folderName + artworkUrl60.substring(artworkUrl60.lastIndexOf("/"));
                String artistName = object.getString("artistName");
                String collectionName = "";
                if (object.has("collectionName"))
                    collectionName = object.getString("collectionName");
                String trackName = object.getString("trackName");

                String songId = artistName + collectionName + trackName;

                //String where = "songId=\"" + songId + "\"";
                //Cursor c = db.query("songs", new String[] { "songId" }, where, null, null, null, null);

                // создаем объект для данных
                ContentValues cv = new ContentValues();

                cv.put("songId", songId);
                cv.put("artworkUrl60", artworkUrl60);
                cv.put("artworkPath", artworkPath);
                cv.put("artistName", artistName);
                cv.put("collectionName", collectionName);
                cv.put("trackName", trackName);

                long rowID = db.insert("songs", null, cv);
                Log.d(LOG_TAG, "row inserted, ID = " + rowID);


            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        dbHelper.close();

        StartDownloadImages();
    }

    public void StartDownloadImages()
    {
        new LoadImagesTask().execute();
    }

    class LoadImagesTask extends AsyncTask<Void, Void, Void> {

        private Exception exception;

        protected Void doInBackground(Void... Params) {
            try {

                SQLiteDatabase db = dbHelper.getWritableDatabase();

                Cursor c = db.query("songs", null, null, null, null, null, null);

                // ставим позицию курсора на первую строку выборки
                // если в выборке нет строк, вернется false
                if (c.moveToFirst()) {

                    // определяем номера столбцов по имени в выборке
                    int idColIndex = c.getColumnIndex("id");
                    int artColIndex = c.getColumnIndex("artworkUrl60");
                    int artPathColIndex = c.getColumnIndex("artworkPath");

                    do {

                        try
                        {
                            File imgFile = new  File(c.getString(artPathColIndex));

                            if(!imgFile.exists())
                            {
                                URL url = new URL(c.getString(artColIndex));

                                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                                connection.setDoInput(true);
                                connection.connect();
                                InputStream input = connection.getInputStream();
                                Bitmap myBitmap = BitmapFactory.decodeStream(input);

                                FileOutputStream stream = new FileOutputStream(c.getString(artPathColIndex));

                                ByteArrayOutputStream outstream = new ByteArrayOutputStream();
                                myBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outstream);
                                byte[] byteArray = outstream.toByteArray();

                                stream.write(byteArray);
                                stream.close();

                                Log.d(LOG_TAG, "Image " + c.getString(artPathColIndex) + " saved.");
                            }
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                            break;
                        }


                        // переход на следующую строку
                        // а если следующей нет (текущая - последняя), то false - выходим из цикла
                    } while (c.moveToNext());
                } else
                    Log.d(LOG_TAG, "0 rows");

                c.close();

                dbHelper.close();

            } catch (Exception e) {
                this.exception = e;
            }
            return null;
        }

        protected void onPostExecute(Void param) {
            mainInstance.LoadProcessEnd();
        }
    }

    public void LoadProcessEnd()
    {
        loadProcessEnd = true;
        FillTableWithData();

        s_touchesEnabled = true;

        if (s_spinner != null)
            s_spinner.setVisibility(View.INVISIBLE);
    }

    public void FillTableWithData()
    {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        Cursor c = db.query("songs", null, null, null, null, null, null);

        // ставим позицию курсора на первую строку выборки
        // если в выборке нет строк, вернется false
        s_readedRecords = 0;
        if (c.moveToFirst()) {

            // определяем номера столбцов по имени в выборке
            int idColIndex = c.getColumnIndex("id");
            int artColIndex = c.getColumnIndex("artworkPath");
            int artistColIndex = c.getColumnIndex("artistName");
            int collectionColIndex = c.getColumnIndex("collectionName");
            int trackColIndex = c.getColumnIndex("trackName");

            do {

                AddNewItemToTable(s_layoutScroll,
                        c.getString(artColIndex),
                        c.getString(artistColIndex),
                        c.getString(collectionColIndex),
                        c.getString(trackColIndex));

                // переход на следующую строку
                // а если следующей нет (текущая - последняя), то false - выходим из цикла
                s_readedRecords++;

            } while (c.moveToNext());
        } else
            Log.d(LOG_TAG, "0 rows");

        c.close();

        dbHelper.close();

        TextView textViewReaded = (TextView)findViewById(R.id.textViewReaded);
        if (textViewReaded != null)
            textViewReaded.setText(String.format("Прочитано записей: %d", s_readedRecords));
    }

    void AddNewItemToTable(LinearLayout linearLayout, String artworkPath, String artistName, String collectionName, String trackName)
    {
        String tag = artistName + collectionName + trackName;

        if (linearLayout.findViewWithTag(tag) != null)
            return;

        RelativeLayout v1 = (RelativeLayout)LayoutInflater.from(this).inflate(R.layout.custom_list, null);

        for(int j=0; j<v1.getChildCount(); ++j)
        {
            View nextChild = v1.getChildAt(j);
            if (nextChild.getId() == R.id.imageView)
            {
                ImageView imageView = (ImageView)nextChild;
                File imgFile = new  File(artworkPath);
                if(imgFile.exists())
                {
                    Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                    imageView.setImageBitmap(myBitmap);
                }
            }
            if (nextChild.getId() == R.id.listTextView1)
            {
                TextView textView = (TextView)nextChild;
                String artistCollection = artistName;
                if (!collectionName.isEmpty())
                {
                    artistCollection += " - " + collectionName;
                }
                textView.setText(artistCollection);
            }
            if (nextChild.getId() == R.id.listTextView2)
            {
                TextView textView = (TextView)nextChild;
                textView.setText(trackName);
            }
        }


        v1.setTag(tag);
        linearLayout.addView(v1);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mainInstance = this;

        LinearLayout menuLayout = (LinearLayout)findViewById(R.id.menuLayout);
        if (menuLayout != null)
        {
            RelativeLayout menu = (RelativeLayout)LayoutInflater.from(this).inflate(R.layout.menu, null);
            if (menu != null)
            {
                menuLayout.addView(menu);
            }
        }

        Button buttonMenu = (Button)findViewById(R.id.buttonMenu);
        if (buttonMenu != null)
        {
            buttonMenu.setOnClickListener(this);
        }

        if (shouldLoadData)
        {
            s_layoutScroll = new LinearLayout(this);
            s_layoutScroll.setOrientation(LinearLayout.VERTICAL);
            s_layoutScroll.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            s_scrollViewExt = new ScrollViewExt(this);
            s_scrollViewExt.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            s_scrollViewExt.setScrollViewListener(this);

            s_spinner = new ProgressBar(this);
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
            layoutParams.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            s_spinner.setLayoutParams(layoutParams);

            folderName = Environment.getExternalStorageDirectory() + "/test.com.testtask";
            File f = new File(folderName);
            f.mkdir();
            folderName = Environment.getExternalStorageDirectory() + "/test.com.testtask/images";
            File f1 = new File(folderName);
            f1.mkdir();

            // создаем объект для создания и управления версиями БД
            dbHelper = new DBHelper(this);

            shouldLoadData = false;
            StartLoadJson();
        }

        RelativeLayout main_layout = (RelativeLayout)findViewById(R.id.main_layout);
        if (main_layout != null)
        {
            ViewGroup parent = (ViewGroup) s_spinner.getParent();
            if (parent != null)
                parent.removeView(s_spinner);
            main_layout.addView(s_spinner);
        }

        TextView textViewReaded = (TextView)findViewById(R.id.textViewReaded);
        if (textViewReaded != null)
            textViewReaded.setText(String.format("Прочитано записей: %d", s_readedRecords));

        Button buttonClose = (Button)findViewById(R.id.buttonClose);
        if (buttonClose != null)
            buttonClose.setOnClickListener(this);

        if (s_layoutScroll != null)
        {
            ViewGroup parent = (ViewGroup) s_layoutScroll.getParent();
            if (parent != null)
                parent.removeView(s_layoutScroll);

            if (s_scrollViewExt != null)
            {
                ViewGroup parentS = (ViewGroup) s_scrollViewExt.getParent();
                if (parentS != null)
                    parentS.removeView(s_scrollViewExt);

                LinearLayout contentLayout = (LinearLayout) findViewById(R.id.contentLayout);
                if (contentLayout != null)
                    contentLayout.addView(s_scrollViewExt);

                s_scrollViewExt.addView(s_layoutScroll);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    static RelativeLayout s_menuScreen = null;

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.buttonMenu:
                LinearLayout contentLayout = (LinearLayout)findViewById(R.id.contentLayout);
                if (contentLayout != null)
                {
                    s_menuScreen = (RelativeLayout)LayoutInflater.from(this).inflate(R.layout.menu, null);
                    if (s_menuScreen != null)
                    {
                        TextView textViewReaded = (TextView)s_menuScreen.findViewById(R.id.textViewReaded);
                        if (textViewReaded != null)
                            textViewReaded.setText(String.format("Прочитано записей: %d", s_readedRecords));

                        Button buttonClose = (Button)s_menuScreen.findViewById(R.id.buttonClose);
                        if (buttonClose != null)
                            buttonClose.setOnClickListener(this);

                        contentLayout.addView(s_menuScreen, 0);
                    }
                }
                break;
            case R.id.buttonClose:
                this.finish();
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (s_menuScreen != null)
            {
                ViewGroup parent = (ViewGroup) s_menuScreen.getParent();
                if (parent != null)
                    parent.removeView(s_menuScreen);
                s_menuScreen = null;
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (s_touchesEnabled)
            return super.dispatchTouchEvent(ev);
        return true;
    }
}

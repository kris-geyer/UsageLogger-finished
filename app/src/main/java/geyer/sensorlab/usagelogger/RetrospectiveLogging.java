package geyer.sensorlab.usagelogger;

import android.annotation.SuppressLint;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RetrospectiveLogging extends AsyncTask<Object, Integer, Integer> {


    private static final String TAG = "rLOG-DATA";


    private Context context;
    private UsageStatsManager usm;
    private PackageManager pm;
    private int numberOfDaysBackToMeasureDurationOfUse,
            dataPoint,
            timeSampledForEventsOfUsage;
    private SharedPreferences prefs;
    private long startDate;
    private AsyncResult delegate;
    private SimpleDateFormat readableFormat;
    Password passwordStorage;


    RetrospectiveLogging(AsyncResult delegate) {
        this.delegate = delegate;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected Integer doInBackground(Object... objects) {
        readableFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Log.i(TAG, "upload history operations called");

        initializationOfValues(objects);
        Boolean queryUsageStatistics = (Boolean) objects[1],
                queryUsageEvents = (Boolean) objects[3];
        if (queryUsageStatistics) {
            documentStatistic(recordUsageStatistics());
        }
        if (queryUsageEvents) {
            documentEvents();
        }
        final Boolean documentedPastUsage = fileExists(Constants.PAST_USAGE_FILE);
        final Boolean documentedPastEvents = fileExists(Constants.PAST_EVENTS_FILE);
        boolean atExtractionPoint = (boolean) objects[5];
        if(!atExtractionPoint){
            if (documentedPastUsage && !documentedPastEvents) {
                return 9;
            } else if (!documentedPastUsage && documentedPastEvents) {
                return 10;
            } else if (!documentedPastUsage && !documentedPastEvents) {
                return 11;
            } else {
                return 12;
            }
        }else{
            if (documentedPastUsage && !documentedPastEvents) {
                return 13;
            } else if (!documentedPastUsage && documentedPastEvents) {
                return 14;
            } else if (!documentedPastUsage && !documentedPastEvents) {
                return 15;
            } else {
                return 16;
            }
        }

    }


    private boolean fileExists(String file) {
        String directory = (String.valueOf(context.getFilesDir()) + File.separator);
        File inspectedFile = new File(directory + File.separator + file);
        return inspectedFile.exists();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("WrongConstant")
    private void initializationOfValues(Object[] objects) {
        passwordStorage = new Password();
        context = (Context) objects[0];

        numberOfDaysBackToMeasureDurationOfUse = (Integer) objects[2];
        /**
         * Remove the duration of days
         */
        timeSampledForEventsOfUsage = (Integer) objects[4];

        prefs = context.getSharedPreferences("app initialization prefs", Context.MODE_PRIVATE);
        usm = (UsageStatsManager) context.getSystemService("usagestats");
        pm = context.getPackageManager();
    }

    private String returnReadableTime (Long unix){
        Date date = new java.util.Date(unix);
        return  readableFormat.format(date);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private LinkedHashMap<Integer, HashMap<Long, String>> recordUsageStatistics() {

        Log.e(TAG, "numbers to survey: " + numberOfDaysBackToMeasureDurationOfUse);

        List<UsageStats> appStatistics;
        dataPoint = 0;


        //initialize what will contain the app Usage
        LinkedHashMap<Integer, HashMap<Long, String>> orderedAppUsage = new LinkedHashMap<>();

        //document the duration required
        //set calendar to the beginning of when the usage is intended to be monitored
        //generate database and then enter it into the LinkedHashMap orderedAppUsage

        //Daily
        Calendar calendar = initializeCalendar(UsageStatsManager.INTERVAL_DAILY);
        HashMap<Long, String> documentDay = new HashMap<>();
        documentDay.put(calendar.getTimeInMillis(), "day monitoring from");
        orderedAppUsage.put(++dataPoint, documentDay);
        appStatistics = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, calendar.getTimeInMillis(), System.currentTimeMillis());
        Log.i(TAG, "daily analysis");
        for (UsageStats usageStats : appStatistics){
            Log.i(TAG, usageStats.getPackageName());
            Log.i(TAG, returnReadableTime(usageStats.getFirstTimeStamp()));
            Log.i(TAG, returnReadableTime(usageStats.getLastTimeStamp()));
            Log.i(TAG, returnReadableTime(usageStats.getLastTimeUsed()));

        }
        orderedAppUsage.putAll(addUsageToOrderedAppUsage(orderedAppUsage, appStatistics));
        Log.i("size", "size of ordered: " + orderedAppUsage.size());

        if(numberOfDaysBackToMeasureDurationOfUse > 7){
            Log.e(TAG, "weekly survey: ");
            //Weekly
            calendar = initializeCalendar(UsageStatsManager.INTERVAL_WEEKLY);
            HashMap<Long, String> documentWeek = new HashMap<>();
            documentWeek.put(calendar.getTimeInMillis(), " weeks monitoring from");
            orderedAppUsage.put(++dataPoint, documentWeek);
            appStatistics = usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, calendar.getTimeInMillis(), System.currentTimeMillis());
            Log.i(TAG, "weekly analysis");
            for (UsageStats usageStats : appStatistics){
                Log.i(TAG, usageStats.getPackageName());
                Log.i(TAG, returnReadableTime(usageStats.getFirstTimeStamp()));
                Log.i(TAG, returnReadableTime(usageStats.getLastTimeStamp()));
                Log.i(TAG, returnReadableTime(usageStats.getLastTimeUsed()));

            }
            orderedAppUsage.putAll(addUsageToOrderedAppUsage(orderedAppUsage, appStatistics));
            Log.i("size", "size of ordered: " + orderedAppUsage.size());
        }

        if(numberOfDaysBackToMeasureDurationOfUse > 28){
            //Monthly
            Log.e(TAG, "monthly survey: ");
            calendar = initializeCalendar(UsageStatsManager.INTERVAL_MONTHLY);
            HashMap<Long, String> documentMonth = new HashMap<>();
            documentMonth.put(calendar.getTimeInMillis(), " months monitoring from");
            orderedAppUsage.put(++dataPoint, documentMonth);
            appStatistics = usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, calendar.getTimeInMillis(), System.currentTimeMillis());
            Log.i(TAG, "Monthly analysis");
            for (UsageStats usageStats : appStatistics){
                Log.i(TAG, usageStats.getPackageName());
                Log.i(TAG, returnReadableTime(usageStats.getFirstTimeStamp()));
                Log.i(TAG, returnReadableTime(usageStats.getLastTimeStamp()));
                Log.i(TAG, returnReadableTime(usageStats.getLastTimeUsed()));

            }
            orderedAppUsage.putAll(addUsageToOrderedAppUsage(orderedAppUsage, appStatistics));
            Log.i("size", "size of ordered: " + orderedAppUsage.size());
        }

        if(numberOfDaysBackToMeasureDurationOfUse > 182){
            Log.e(TAG, "annually survey: ");
            //Annually
            calendar = initializeCalendar(UsageStatsManager.INTERVAL_YEARLY);
            HashMap<Long, String> documentYear = new HashMap<>();
            documentYear.put(calendar.getTimeInMillis(), " years monitoring from");
            orderedAppUsage.put(++dataPoint, documentYear);
            appStatistics = usm.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, calendar.getTimeInMillis(), System.currentTimeMillis());
            Log.i(TAG, "Annual analysis");
            for (UsageStats usageStats : appStatistics){
                Log.i(TAG, usageStats.getPackageName());
                Log.i(TAG, returnReadableTime(usageStats.getFirstTimeStamp()));
                Log.i(TAG, returnReadableTime(usageStats.getLastTimeStamp()));
                Log.i(TAG, returnReadableTime(usageStats.getLastTimeUsed()));

            }

            orderedAppUsage.putAll(addUsageToOrderedAppUsage(orderedAppUsage, appStatistics));
            Log.i("size", "size of ordered: " + orderedAppUsage.size());
        }


        return orderedAppUsage;

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private LinkedHashMap<Integer, HashMap<Long, String>> addUsageToOrderedAppUsage(LinkedHashMap<Integer, HashMap<Long, String>> orderedAppUsage, List<UsageStats> appStatistics) {

        final int dbSize = appStatistics.size();
        int progress = 0;

        for (UsageStats stat : appStatistics) {
            if (stat.getTotalTimeInForeground() > 0) {
                final String packageName = stat.getPackageName();

                String appName;
                try{
                    appName = (String) pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA));
                }catch (PackageManager.NameNotFoundException e1){
                    appName = packageName;
                }

                HashMap<Long, String> timeInForeground = new HashMap<>();
                timeInForeground.put(stat.getTotalTimeInForeground(), appName + " used for");
                orderedAppUsage.put(++dataPoint, timeInForeground);

                HashMap<Long, String> firstTimeUsed = new HashMap<>();
                firstTimeUsed.put(stat.getFirstTimeStamp(), appName + " first used");
                orderedAppUsage.put(++dataPoint, firstTimeUsed);

                HashMap<Long, String> lastTimeUsed = new HashMap<>();
                lastTimeUsed.put(stat.getLastTimeUsed(), appName + " last used");
                orderedAppUsage.put(++dataPoint, lastTimeUsed);

                if (stat.getFirstTimeStamp() < startDate) {
                    startDate = stat.getFirstTimeStamp();
                }

                Log.i("entry", "app: " + packageName + " -  time:" + stat.getTotalTimeInForeground());
            }

            ++progress;
            if (progress % 10 == 0) {
                int currentProgress = (progress * 100) / dbSize;
                publishProgress(currentProgress);
            }

        }
        return orderedAppUsage;
    }


    private Calendar initializeCalendar(int interval) {
        Calendar calendar = Calendar.getInstance();
        switch (interval) {
            case UsageStatsManager.INTERVAL_DAILY:
                calendar.add(Calendar.DAY_OF_YEAR, -numberOfDaysBackToMeasureDurationOfUse);
                break;
            case UsageStatsManager.INTERVAL_WEEKLY:
                calendar.add(Calendar.DAY_OF_YEAR, -numberOfDaysBackToMeasureDurationOfUse);
                break;
            case UsageStatsManager.INTERVAL_MONTHLY:
                calendar.add(Calendar.DAY_OF_YEAR, -numberOfDaysBackToMeasureDurationOfUse);
                break;
            case UsageStatsManager.INTERVAL_YEARLY:
                calendar.add(Calendar.DAY_OF_YEAR, -numberOfDaysBackToMeasureDurationOfUse);
                break;
        }

        Log.i("initialize", "" + calendar.getTimeInMillis());

        return calendar;
    }

    private void documentStatistic(LinkedHashMap<Integer, HashMap<Long, String>> stats) {

        if (stats.size() > 0) {
            //creates document
            Document document = new Document();
            //getting destination
            File path = context.getFilesDir();
            File file = new File(path, Constants.PAST_USAGE_FILE);
            // Location to save
            PdfWriter writer = null;
            try {
                writer = PdfWriter.getInstance(document, new FileOutputStream(file));
            } catch (DocumentException e) {
                Log.e(TAG, "document exception: " + e);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "File not found exception: " + e);
            }
            try {
                if (writer != null) {
                    writer.setEncryption("concretepage".getBytes(), passwordStorage.returnPassword().getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
                }
            } catch (DocumentException e) {
                Log.e(TAG, "document exception: " + e);
            }
            if (writer != null) {
                writer.createXmpMetadata();
            }
            // Open to write
            document.open();

            PdfPTable table = new PdfPTable(2);
            //attempts to add the columns

            Log.i("entry set", stats.entrySet() + "");

            try {
                int count = 0;
                for (Map.Entry<Integer, HashMap<Long, String>> app : stats.entrySet()) {

                    HashMap<Long, String> toAddToDB = app.getValue();
                    Map.Entry<Long, String> entry = toAddToDB.entrySet().iterator().next();

                    final long time = entry.getKey();
                    Log.i("entry", "app: " + entry.getValue() + " - time: " + time);
                    table.addCell("" + String.valueOf(time));
                    table.addCell(entry.getValue());
                    int currentProgress = (count * 100) / stats.size();
                    count++;
                    publishProgress(currentProgress);
                }
            } catch (Exception e) {
                Log.e("file construct", "error " + e);
            }

            //add to document
            document.setPageSize(PageSize.A4);
            document.addCreationDate();
            try {
                document.add(table);
            } catch (DocumentException e) {
                Log.e(TAG, "Document exception: " + e);
            }
            document.addAuthor("Kris");
            document.close();
        } else {
            Log.i(TAG, "no data in passed stats value");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void documentEvents() {
        UsageEvents usageEvents = null;

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -timeSampledForEventsOfUsage);
        long start = calendar.getTimeInMillis();

        if (usm != null) {
            usageEvents = usm.queryEvents(start, System.currentTimeMillis());
        } else {
            Log.e(TAG, "usm equals null");
        }

        ArrayList<String> databaseEvent = new ArrayList<>();

        HashMap<String, Integer> uninstalledApps = new HashMap<>();

        //creates document
        Document document = new Document();
        //getting destination
        File path = context.getFilesDir();
        File file = new File(path, Constants.PAST_EVENTS_FILE);
        // Location to save
        PdfWriter writer = null;
        try {
            writer = PdfWriter.getInstance(document, new FileOutputStream(file));
        } catch (DocumentException e) {
            Log.e(TAG, "document exception: " + e);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found exception: " + e);
        }
        try {
            if (writer != null) {
                writer.setEncryption("concretepage".getBytes(), passwordStorage.returnPassword().getBytes(), PdfWriter.ALLOW_COPY, PdfWriter.ENCRYPTION_AES_128);
            }
        } catch (DocumentException e) {
            Log.e(TAG, "document exception: " + e);
        }
        if (writer != null) {
            writer.createXmpMetadata();
        }
        // Open to write
        document.open();

        //generate an length of usage Events

        int rowSize = 0,
            currentRow = 0;

        if(usageEvents != null){
          while(usageEvents.hasNextEvent()){
              rowSize++;
              UsageEvents.Event e = new UsageEvents.Event();
              usageEvents.getNextEvent(e);
          }
        }

        if (usm != null) {
            usageEvents = usm.queryEvents(start, System.currentTimeMillis());
        } else {
            Log.e(TAG, "usm equals null");
        }

        //Start storing data from here
        PdfPTable table = new PdfPTable(3);

        if(usageEvents != null){
            while(usageEvents.hasNextEvent()){
                UsageEvents.Event e = new UsageEvents.Event();
                usageEvents.getNextEvent(e);
                String appName = "";

                try {
                    appName = (String) pm.getApplicationLabel(pm.getApplicationInfo(e.getPackageName(), PackageManager.GET_META_DATA));
                    appName = appName.replace(" ", "-");
                    databaseEvent.add(appName);
                    //Log.i("app name", appName );
                } catch (PackageManager.NameNotFoundException e1) {

                    appName = e.getPackageName();
                    appName = appName.replace(" ", "-");
                    if (uninstalledApps.containsKey(appName)) {
                        databaseEvent.add("app" + uninstalledApps.get(appName));
                    } else {
                        databaseEvent.add("app" + uninstalledApps.size());
                        uninstalledApps.put(appName, uninstalledApps.size());
                    }
                    Log.e("usageHistory", "Error in identify package name: " + e);
                }

                try {
                    currentRow++;

                    table.addCell(""+e.getTimeStamp());
                    table.addCell(""+ appName);
                    table.addCell(""+e.getEventType());

                    if (currentRow % 10 == 0) {
                        int currentProgress = (currentRow * 100) / rowSize;
                        publishProgress(currentProgress);
                    }


                } catch (Exception e1) {
                    Log.e("file construct", "error " + e1);
                }

            }
        }

        //add to document
        document.setPageSize(PageSize.A4);
        document.addCreationDate();
        try {
            document.add(table);
        } catch (DocumentException e) {
            Log.e(TAG, "Document exception: " + e);
        }
        document.addAuthor("Kris");
        document.close();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        //Log.i("Main", "Progress update: " + values[0]);
        //set up sending local signal to update main activity

        informMain(values[0]);
    }

    private void informMain(int progressBarValue) {
        Intent intent = new Intent("changeInService");
        intent.putExtra("progress bar update", true);
        intent.putExtra("progress bar progress", progressBarValue);
        intent.putExtra("asyncTask","past usage logging");
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Override
    protected void onPostExecute(Integer integer) {
        super.onPostExecute(integer);
        delegate.processFinish(integer);
    }

}


/*

removed from documentEvents

        int count = 0;
        if (usageEvents != null) {
            while (usageEvents.hasNextEvent()) {
                //Log.i(TAG, "number: " + count);
                UsageEvents.Event e = new UsageEvents.Event();
                usageEvents.getNextEvent(e);
                count++;
            }

            usageEvents = usm.queryEvents(start, System.currentTimeMillis());


            int newCount = 0;
            while (usageEvents.hasNextEvent()) {

                UsageEvents.Event e = new UsageEvents.Event();
                usageEvents.getNextEvent(e);
                HashMap<String, Integer> uninstalledApps = new HashMap<>();

                try {
                    String appName = (String) pm.getApplicationLabel(pm.getApplicationInfo(e.getPackageName(), PackageManager.GET_META_DATA));
                    appName = appName.replace(" ", "-");
                    databaseEvent.add(appName);
                    //Log.i("app name", appName );
                } catch (PackageManager.NameNotFoundException e1) {

                    String packageName = e.getPackageName();
                    packageName = packageName.replace(" ", "-");
                    if (uninstalledApps.containsKey(packageName)) {
                        databaseEvent.add("app" + uninstalledApps.get(packageName));
                    } else {
                        databaseEvent.add("app" + uninstalledApps.size());
                        uninstalledApps.put(packageName, uninstalledApps.size());
                    }
                    Log.e("usageHistory", "Error in identify package name: " + e);
                }

                databaseTimestamp.add(e.getTimeStamp());
                databaseEventType.add(e.getEventType());

                newCount++;
                publishProgress((newCount * 100) / count);
                if (newCount % 100 == 0) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }


                for (int i = 0; i < databaseEvent.size(); i++) {
                    HashMap<String, Integer> eventAndEventType = new HashMap<>();
                    eventAndEventType.put(databaseEvent.get(i), databaseEventType.get(i));
                    CompleteRecord.put(databaseTimestamp.get(i), eventAndEventType);
                }
            }

        } else {
            Log.e(TAG, "usage event equals null");
        }


 */

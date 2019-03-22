package geyer.sensorlab.usagelogger;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import android.support.annotation.RequiresApi;
import android.support.v4.content.FileProvider;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AsyncResult {

    private static final String TAG = "MAIN";

    private SharedPreferences prefs, userGeneratedRandomnessPrefs;
    SharedPreferences.Editor editor, randomEditor;

    TextView status;

    //Classes
    DirectAppInitialization directApp;
    InformUser informUser;
    RequestPermission permissionRequests;
    CrossSectionalQuery crossSectionalQuery;
    ResearcherInput researcherInput;
    RetrospectiveLogging retrospectiveLogging;
    PackageProspectiveUsage packageProspectiveUsage;
    Password passwordStorage;


    /**
     * Fix service so that it informs the user that data is being collected
     * Speed up documenting of apps
     * Have Java determine the layout of the application view
     *
     *
     * @param savedInstanceState
     */



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        initializeInvisibleComponents();
        initializeServiceStateListener();
        initializeVisibleComponents();
        initializeClasses();
        promptAction(directApp.detectState());
    }

    private void initializeInvisibleComponents() {
        SQLiteDatabase.loadLibs(this);
        prefs = getSharedPreferences("app initialization prefs", MODE_PRIVATE);
        userGeneratedRandomnessPrefs = getSharedPreferences("random prefs", MODE_PRIVATE);
        editor = prefs.edit();
        editor.putBoolean("main in foreground", true).apply();
        randomEditor = userGeneratedRandomnessPrefs.edit();
        randomEditor.putLong("rTimeOne", System.currentTimeMillis()).apply();
    }

    private void initializeServiceStateListener() {

        final ProgressBar progressBar = findViewById(R.id.progressBar);

        BroadcastReceiver localListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getBooleanExtra("dataFromProspective", false)){
                    Log.i("FROM service", "data collection on going");
                    String msg = intent.getStringExtra("dataToRelay");
                    if(msg!= null){
                        if(msg.equals("please close and open the screen")){
                            status.setText(msg);
                        }else{
                            Log.i(TAG, msg);
                        }
                        if(msg.equals("event")){
                            status.setText(R.string.dataCollectingOnGoing);
                        }
                    }
                }

                if(intent.getBooleanExtra("errorFromProspective", false)){
                    final String msg = intent.getStringExtra("dataToRelay");
                    if(msg!= null){
                        Log.i("FROM service", msg);
                        status.setText(msg);
                    }
                }

                if(intent.getBooleanExtra("progress bar update", false)){
                    updateProgressBar(intent.getIntExtra("progress bar progress", 0));
                    String fromAsync = intent.getStringExtra("asyncTask");
                    if(fromAsync != null){
                        status.setText(intent.getStringExtra("asyncTask"));
                    }else{
                        status.setText(R.string.backgroundRunning);
                    }
                }
            }

            private void updateProgressBar(int progress_bar_progress) {
                progressBar.setProgress(progress_bar_progress);
            }
        };
        LocalBroadcastManager.getInstance(MainActivity.this).registerReceiver(localListener, new IntentFilter("changeInService"));
    }

    private void initializeVisibleComponents() {
        Button email = findViewById(R.id.btnEmail);
        email.setOnClickListener(this);

        Button password = findViewById(R.id.btnGenerateNewPassword);
        password.setOnClickListener(this);

        Button showInstructions = findViewById(R.id.btnShowInstructions);
        showInstructions.setOnClickListener(this);

        Button privacy = findViewById(R.id.btnPrivacyPolicy);
        privacy.setOnClickListener(this);

        status = findViewById(R.id.tvStatus);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnGenerateNewPassword:
                reportPassword(passwordStorage.returnPassword());
                break;
            case R.id.btnEmail:
                int dataExtractionRequired = DeterminePointOfDataExtraction.pointOfCrossSectionalLogging + DeterminePointOfDataExtraction.pointOfRetrospectiveLogging;
                if(dataExtractionRequired > 2){
                        conductExportPointDataExtraction(dataExtractionRequired);
                }else{
                    if(researcherInput.ProspectiveLoggingEmployed){
                        packageProspectiveUsage = new PackageProspectiveUsage(this);
                        packageProspectiveUsage.execute(this);
                    }else{
                        sendEmail();
                    }
                }
                break;
            case R.id.btnShowInstructions:
                informUser(1, true);
                break;
            case R.id.btnPrivacyPolicy:
                Uri uri = Uri.parse("https://psychsensorlab.com/privacy-agreement-for-apps/");
                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(launchBrowser);
                break;
        }
    }

    private void conductExportPointDataExtraction(int dataExtractionRequired) {
        if(dataExtractionRequired == 4){
        //both data sources will be required
        retrospectiveLogging.execute(this,
                researcherInput.UseUsageStatics,
                researcherInput.NumberOfDaysForUsageStats,
                researcherInput.UseUsageEvents,
                researcherInput.NumberOfDaysForUsageEvents,
                true);
    }else {
            if (DeterminePointOfDataExtraction.pointOfRetrospectiveLogging == 2) {
                //conduct retrospective logging
                retrospectiveLogging.execute(this,
                        researcherInput.UseUsageStatics,
                        researcherInput.NumberOfDaysForUsageStats,
                        researcherInput.UseUsageEvents,
                        researcherInput.NumberOfDaysForUsageEvents,
                        true);
            } else {
                //conduct crossSectional logging
                crossSectionalQuery.execute(getApplicationContext(), this, researcherInput.LevelOfCrossSectionalAnalysis, true);
            }
        }
    }

    private void generateNewPassword() {
        final Context context = this;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Consenting")
                .setMessage("Press I consent in order to begin the process of collecting data for researchers, otherwise you can go to the information sheet again")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                            passwordAcceptable();
                    }

                    private void passwordAcceptable(){
                        String password = null;
                        try {
                            password = generatePassword();
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.getMessage());
                        }
                        if(password != null){
                            if (password.length() >= 10) {


                                editor.putBoolean("password generated", true);
                                passwordStorage.initializePassword(context, password);

                                editor.apply();

                                reportPassword(password);
                            }
                        }else{
                            status.setText(R.string.pError);
                        }

                    }

                    private String generatePassword() throws InterruptedException {

                        Long randomValues[] = new Long[4];
                        randomValues[0] = userGeneratedRandomnessPrefs.getLong("rTimeOne", 1);
                        randomValues[1] = userGeneratedRandomnessPrefs.getLong("rTimeTwo", 1);
                        randomValues[2] = userGeneratedRandomnessPrefs.getLong("rTimeThree", 1);
                        randomValues[3] = userGeneratedRandomnessPrefs.getLong("rTimeFour", 1);

                        if(
                                randomValues[0] == 1||
                                randomValues[1] == 1||
                                randomValues[2] == 1||
                                randomValues[3] == 1){
                            return "probs";
                        }else{
                            Ascii ascii = new Ascii();
                            StringBuilder password = new StringBuilder();
                            Random userRandomInitial = new Random(randomValues[0]);
                            while (password.length() < 12){
                                Random userRandom ;
                                userRandom = new Random(randomValues[userRandomInitial.nextInt(4)]);

                                Random random = new Random(System.currentTimeMillis()* randomValues[userRandomInitial.nextInt(4)]);
                                Thread.sleep(userRandom.nextInt(40) +1);

                                int randomAsciiInt =  random.nextInt(93) + 33;
                                Character randomAsciiChar = ascii.returnAscii(randomAsciiInt);
                                if(randomAsciiChar != ' '){
                                    Log.i(TAG, "returned from Ascii: " + randomAsciiChar);
                                    password.append(randomAsciiChar);
                                }
                            }
                            Arrays.fill(randomValues,null);
                            Calendar c = Calendar.getInstance();
                            c.set(Calendar.HOUR_OF_DAY, 1);
                            c.set(Calendar.MINUTE, 1);
                            c.set(Calendar.MILLISECOND, 1);
                            c.set(Calendar.SECOND, 1);

                            editor
                                    .putLong("rTimeOne", c.getTimeInMillis())
                                    .putLong("rTimeTwo", c.getTimeInMillis())
                                    .putLong("rTimeThree", c.getTimeInMillis())
                                    .putLong("rTimeFour", c.getTimeInMillis())
                                    .apply();

                            Log.i(TAG, "current password: " + prefs.getLong("rTimeOne", 0) + " - " + prefs.getLong("rTimeFour", 0));

                            return password.toString();
                        }
                    }

                }).setNegativeButton("see information again", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.create()
                .show();
    }

    private void reportPassword(String password) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Your password")
                .setMessage("Your password is: " + "\n" + "\n" + password)
                .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        promptAction(directApp.detectState());
                    }
                });

        builder.create()
                .show();
    }

    private void sendEmail() {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("text/plain");

        //getting directory for internal files
        String directory = (String.valueOf(this.getFilesDir()) + File.separator);
        Log.i("Directory", directory);
        File directoryPath = new File(directory);
        File[] filesInDirectory = directoryPath.listFiles();
        Log.d("Files", "Size: "+ filesInDirectory.length);
        for (File file : filesInDirectory) {
            Log.d("Files", "FileName:" + file.getName());
        }

        //initializing files reference
        File appDocumented = new File(directory + File.separator + Constants.APPS_FILE),

                crossSectional = new File(directory + File.separator + Constants.CROSS_SECTIONAL_FILE),

                screenUsage = new File(directory + File.separator + Constants.PROSPECTIVE_LOGGING),

                usageStats = new File(directory + File.separator + Constants.PAST_USAGE_FILE),

                usageEvents = new File(directory + File.separator + Constants.PAST_EVENTS_FILE);

        //list of files to be uploaded
        ArrayList<Uri> files = new ArrayList<>();

        //if target files are identified to exist then they are packages into the attachments of the email
        try {
            if(appDocumented.exists()){
                files.add(FileProvider.getUriForFile(this, "geyer.sensorlab.usagelogger.fileprovider", appDocumented));
            }

            if(crossSectional.exists()){
                files.add(FileProvider.getUriForFile(this, "geyer.sensorlab.usagelogger.fileprovider", crossSectional));
            }

            if(screenUsage.exists()){
                files.add(FileProvider.getUriForFile(this, "geyer.sensorlab.usagelogger.fileprovider", screenUsage));
            }

            if(usageStats.exists()){
                files.add(FileProvider.getUriForFile(this, "geyer.sensorlab.usagelogger.fileprovider", usageStats));
            }

            if(usageEvents.exists()){
                files.add(FileProvider.getUriForFile(this, "geyer.sensorlab.usagelogger.fileprovider", usageEvents));
            }

            if(files.size()>0){
                //adds the file to the intent to send multiple data points
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            }else{
                Log.e("email", "no files to upload");
            }

        }
        catch (Exception e){
            Log.e("File upload error1", "Error:" + e);
        }
    }

    private void initializeClasses() {
        passwordStorage = new Password();
        directApp = new DirectAppInitialization(this);

        if(directApp.detectPermissionsState() < 6){
            permissionRequests = new RequestPermission(this);
        }

        researcherInput = new ResearcherInput();

        if(researcherInput.PerformCrossSectionalAnalysis){
            crossSectionalQuery = new CrossSectionalQuery(this);
        }

        if(researcherInput.InformUserRequired){
            informUser = new InformUser(this);
        }

        if(researcherInput.RetrospectiveLoggingEmployed){
            retrospectiveLogging = new RetrospectiveLogging(this);
        }

        if(researcherInput.PerformCrossSectionalAnalysis | researcherInput.ProspectiveLoggingEmployed){
            packageProspectiveUsage = new PackageProspectiveUsage(this);
        }
    }

    private void promptAction(int i) {
        Log.i(TAG, "result of detect state: " + i);
        switch (i){
            //inform user
            case 1:
                informUser(1, false);
                break;
            //request password
            case 2:
                generateNewPassword();
                break;
            //document apps
            case 3:
                if(!prefs.getBoolean("crossSectionalQueryRunning", false)){
                    runCrossSectionalQuery();
                }
                break;
            //request the usage permissions
            case 4:
                //request permissions
                informAboutRequestForPermission(Constants.USAGE_STATISTIC_PERMISSION_REQUEST);

                break;
            //request the notification permissions
            case 5:
                informAboutRequestForPermission(Constants.NOTIFICATION_LISTENER_PERMISSIONS);
                break;
            case 6:
                Toast.makeText(this, "No action to take", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error occurred");
                break;
            case 7:
                //start retrospectively logging data
                retrospectiveLogging.execute(this,
                        researcherInput.UseUsageStatics,
                        researcherInput.NumberOfDaysForUsageStats,
                        researcherInput.UseUsageEvents,
                        researcherInput.NumberOfDaysForUsageEvents,
                        false);
                break;
            case 8:
                //end of retrospective logging and no prospective logging required
                break;
            case 9:
                Log.i(TAG, "call to start logging background data");
                startProspectiveLogging(false);
                break;
            case 10:
                Log.i(TAG, "call to start logging notification background data");
                startProspectiveLogging(true);
            case 11:
                informServiceIsRunning();
                break;
            default:
                break;
        }
    }

    private void runCrossSectionalQuery() {
        crossSectionalQuery.execute(getApplicationContext(), this, researcherInput.LevelOfCrossSectionalAnalysis,false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case Constants.SHOW_PRIVACY_POLICY:
                promptAction(directApp.detectState());
                break;
            case Constants.USAGE_STATISTIC_PERMISSION_REQUEST:
                promptAction(directApp.detectState());
                break;
            case Constants.NOTIFICATION_LISTENER_PERMISSIONS:
                promptAction(directApp.detectState());
                break;
        }
    }

    private void informAboutRequestForPermission(int permissionToBeRequested) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        switch (permissionToBeRequested){
            case Constants.USAGE_STATISTIC_PERMISSION_REQUEST:
                builder.setTitle("usage permission")
                        .setMessage("usage details")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                permissionRequests.requestSpecificPermission(Constants.USAGE_STATISTIC_PERMISSION_REQUEST);
                            }
                        });
                break;
            case Constants.NOTIFICATION_LISTENER_PERMISSIONS:
                builder.setTitle("notification permission")
                        .setMessage("details")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                permissionRequests.requestSpecificPermission(Constants.NOTIFICATION_LISTENER_PERMISSIONS);
                            }
                        });
                break;
        }

        builder.create()
                .show();
    }

    private void informUser(int whichMessageToCall, boolean userDirectedCalls) {

        final boolean userDirectedCall = userDirectedCalls;
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            switch(whichMessageToCall){
                case 1:
                    builder.setTitle("Usage app")
                            .setMessage(informUser.constructMessageOne())
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if(passwordStorage.returnPassword().equals("notPassword")){
                                        randomEditor.putLong("rTimeTwo", System.currentTimeMillis()).apply();
                                    }
                                    callNextMessage(2, userDirectedCall);
                                }
                            });
                    break;
                case 2:
                    builder.setTitle("Usage app")
                            .setMessage(informUser.constructMessageTwo())
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    if(passwordStorage.returnPassword().equals("notPassword")){
                                        randomEditor.putLong("rTimeThree", System.currentTimeMillis()).apply();
                                    }
                                    callNextMessage(3, userDirectedCall);
                                }
                            });
                    break;
                default:
                    builder.setTitle("Usage app")
                            .setMessage(informUser.constructMessageThree())
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    if(passwordStorage.returnPassword().equals("notPassword")){
                                        randomEditor.putLong("rTimeFour", System.currentTimeMillis()).apply();
                                        editor.putBoolean("instructions shown", true)
                                                .apply();
                                    }
                                    if(!userDirectedCall){
                                        promptAction(directApp.detectState());
                                    }

                                }
                            }).setNegativeButton("View privacy policy", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Uri uri = Uri.parse("https://psychsensorlab.com/privacy-agreement-for-apps/");
                            Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uri);
                            startActivityForResult(launchBrowser, Constants.SHOW_PRIVACY_POLICY);
                        }
                    });
            }
            builder.create().show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void callNextMessage(int i, boolean userDirectedCall) {
        informUser(i, userDirectedCall);
    }

    private void startProspectiveLogging(Boolean startNotificationService){
        Intent startLogging;
        if(startNotificationService){

        startLogging = new Intent(this, ProspectiveNotificationLogger.class);

        }else{
            startLogging = new Intent(this, ProspectiveLogger.class);
        }
        Bundle bundle = new Bundle();
        bundle.putBoolean("from_restart_service", false);

        boolean documentAppRunningInForeground = researcherInput.LevelOfProspectiveLogging>1 && researcherInput.LevelOfProspectiveLogging != 3;

        Log.i(TAG, "direct service to include usage statistics: " + documentAppRunningInForeground);
        if(documentAppRunningInForeground){
            bundle.putBoolean("usage log", true);
        }else{
            bundle.putBoolean("usage log", false);
        }

        if(researcherInput.PerformCrossSectionalAnalysis){
            bundle.putBoolean("document apps", true);
        }else{
            bundle.putBoolean("document apps", false);
        }

        startLogging.putExtras(bundle);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(startLogging);
        }else{
            startService(startLogging);
        }
    }

    private void informServiceIsRunning() {

    }

    @Override
    public void processFinish(Integer output) {
        Log.i(TAG, "result from async task: " + output);
        switch (output){
            case 1:
                Log.i(TAG, "crossSectional databases both exist");
                crossSectionalQueryFinished(output, false);
                break;
            case 2:
                Log.i(TAG, "crossSectional databases apps exist but permissions don't");
                crossSectionalQueryFinished(output, false);
                break;
            case 3:
                Log.i(TAG, "crossSectional databases permission exist but apps don't");
                crossSectionalQueryFinished(output, false);
                break;
            case 4:
                Log.i(TAG, "crossSectional neither databases apps exist");
                crossSectionalQueryFinished(output, false);
                break;
            case 5:
                Log.i(TAG, "crossSectional databases both exist");
                crossSectionalQueryFinished(output, true);
                break;
            case 6:
                Log.i(TAG, "crossSectional databases apps exist but permissions don't");
                crossSectionalQueryFinished(output, true);
                break;
            case 7:
                Log.i(TAG, "crossSectional databases permission exist but apps don't");
                crossSectionalQueryFinished(output, true);
                break;
            case 8:
                Log.i(TAG, "crossSectional neither databases apps exist");
                crossSectionalQueryFinished(output, true);
                break;
            case 9:
                Log.i(TAG, "retrospective logging unsuccessful - events don't exist");
                retrospectiveQueryFinished(output,false);
                break;
            case 10:
                Log.i(TAG, "retrospective logging unsuccessful - stats don't exist");
                retrospectiveQueryFinished(output,false);
                break;
            case 11:
                Log.i(TAG, "retrospective logging unsuccessful - events and stats don't exist");
                retrospectiveQueryFinished(output,false);
                break;
            case 12:
                Log.i(TAG, "retrospective logging successful");
                retrospectiveQueryFinished(output,false);
                break;
            case 13:
                Log.i(TAG, "retrospective logging unsuccessful - events don't exist");
                retrospectiveQueryFinished(output,true);
                break;
            case 14:
                Log.i(TAG, "retrospective logging unsuccessful - stats don't exist");
                retrospectiveQueryFinished(output,true);
                break;
            case 15:
                Log.i(TAG, "retrospective logging unsuccessful - events and stats don't exist");
                retrospectiveQueryFinished(output,true);
                break;
            case 16:
                Log.i(TAG, "retrospective logging successful");
                retrospectiveQueryFinished(output,true);
                break;
            case 17:
                Log.i(TAG, "packaging successful");
                sendEmail();
                break;
            case 18:
                Log.i(TAG, "packaging unsuccessful - cross sectional file doesn't exist. Prospective file exists");
                sendEmail();
                break;
            case 19:
                Log.i(TAG, "packaging unsuccessful - app file doesn't exist. Prospective file exists");
                sendEmail();
                break;
            case 20:
                Log.i(TAG, "packaging unsuccessful - neither file exists. Prospective file exists");
                sendEmail();
                break;
            case 21:
                Log.i(TAG, "packaging unsuccessful - App and cross section file exists, Prospective file does not exists");
                sendEmail();
                break;
            case 22:
                Log.i(TAG, "packaging unsuccessful - app file doesn't exists, Prospective file does not exists");
                sendEmail();
                break;
            case 23:
                Log.i(TAG, "packaging unsuccessful - cross sectional file doesn't exists, Prospective file does not exists");
                sendEmail();
                break;
            case 24:
                Log.i(TAG, "packaging unsuccessful - none of the files exists, Prospective file does not exists");
                sendEmail();
                break;

        }
    }

    private void retrospectiveQueryFinished(Integer output, boolean progressToNextPointOfExtraction) {
        Log.i(TAG, "Retrospective query finished with result: " + output);
        if(progressToNextPointOfExtraction){
            if(DeterminePointOfDataExtraction.pointOfCrossSectionalLogging == 2){
                crossSectionalQuery = new CrossSectionalQuery(this);
                crossSectionalQuery.execute(getApplicationContext(), this, researcherInput.LevelOfCrossSectionalAnalysis, true);
            }else{
                if(researcherInput.ProspectiveLoggingEmployed){
                    retrospectiveLogging = new RetrospectiveLogging(this);
                    packageProspectiveUsage.execute(this);
                }else{
                    sendEmail();
                }
            }
        }else{
            promptAction(directApp.detectState());
        }
    }

    private void crossSectionalQueryFinished(Integer output, boolean progressToExtraction) {
        Log.i(TAG, "cross sectional query finish with result: " + output);
        if(!progressToExtraction){
            promptAction(directApp.detectState());
        }else{
            if(researcherInput.ProspectiveLoggingEmployed){
                retrospectiveLogging = new RetrospectiveLogging(this);
                packageProspectiveUsage.execute(this);
            }else{
                sendEmail();
            }
        }
    }
}

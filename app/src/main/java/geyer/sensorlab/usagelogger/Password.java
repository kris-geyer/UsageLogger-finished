package geyer.sensorlab.usagelogger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.securepreferences.SecurePreferences;

public class Password {
    static SharedPreferences preferences;


    public void initializePassword(Context context, String password){
        Log.i("password", "initialized");
        preferences = new SecurePreferences(context);

        preferences.edit().putString("password", password).apply();
    }

    public String returnPassword(){
        String toReturn;
        try{
            toReturn = preferences.getString("password", "notPassword");
        }catch (Exception e){
            toReturn = "notPassword";
        }
        return toReturn;
    }
}

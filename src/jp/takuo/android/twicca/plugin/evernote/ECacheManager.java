package jp.takuo.android.twicca.plugin.evernote;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.ListIterator;

import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.Tag;

import android.content.Context;
import android.util.Log;

public class ECacheManager {
    // cache
    private static final String LOG_TAG = "ECacheManager";

    // filenames
    private static final String CACHE_TOKEN  = "token";
    private static final String CACHE_TAGS   = "tags";
    private static final String CACHE_NOTES  = "notes";

    private Context mContext;

    public ECacheManager(Context context) {
        mContext = context;
    }

    public static void clear(File dir) {
        File file;
        file = new File(dir, CACHE_TOKEN);
        if (file.exists()) file.delete();
        file = new File(dir, CACHE_NOTES);
        if (file.exists()) file.delete();
        file = new File(dir, CACHE_TAGS);
        if (file.exists()) file.delete();
    }

    public String getAuthToken() {
        File file = new File(mContext.getCacheDir(), CACHE_TOKEN);
        if (! file.canRead()) return null;
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String expire_at = br.readLine();
            String token = br.readLine();
            br.close();
            // 10 mins padding.
            if (Long.parseLong(expire_at) < System.currentTimeMillis() - (600 * 1000)) return null;
            return token;
        } catch (Exception e){
            Log.d(LOG_TAG, "Auth Read error: " + e.getMessage());
        }
        return null;
    }

    public String[] getTagNames() {
        File file = new File(mContext.getCacheDir(), CACHE_TAGS);
        if (! file.canRead()) return new String [] {};
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line = br.readLine();
            String[] retval = line.split(",");
            br.close();
            return retval;
        } catch (Exception e){
            Log.d(LOG_TAG, "Tags Read error: " + e.getMessage());
        }
        return new String [] {};
    }

    public Hashtable<String, String> getNoteTable () {
        Hashtable<String, String> retval = new Hashtable<String, String>();
        String line;
        String[] pair;
        File file = new File(mContext.getCacheDir(), CACHE_NOTES);
        if (! file.canRead()) return retval;
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            line = br.readLine();
            while (line != null) {
                pair = line.split(",", 2);
                retval.put(pair[1].toLowerCase(), pair[0]);
                line = br.readLine();
            }
            br.close();
            return retval;
        } catch (Exception e){
            Log.e(LOG_TAG, "Note Read error: " + e.getMessage());
            e.printStackTrace();
        }
        return retval;
    }

    public ArrayList<String> getNotebookNames () {
        ArrayList<String> retval = new ArrayList<String>();
        String line;
        String[] pair;
        File file = new File(mContext.getCacheDir(), CACHE_NOTES);
        if (! file.canRead()) return retval;
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            line = br.readLine();
            while (line != null) {
                pair = line.split(",", 2);
                retval.add(pair[1]);
                line = br.readLine();
            }
            br.close();
            return retval;
        } catch (Exception e){
            Log.e(LOG_TAG, "Note Read error: " + e.getMessage());
            e.printStackTrace();
        }
        return retval;
    }

    public void writeTagsCache(List<Tag> tags) {
        Log.d(LOG_TAG, "Write tags cache");
        try {
            String tagString = "";
            File file = new File(mContext.getCacheDir(), CACHE_TAGS);

            BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(file));
            ListIterator<Tag> itr = tags.listIterator();
            while (itr.hasNext()) {
                tagString += itr.next().getName();
                if (itr.hasNext()) tagString += ",";
            }
            fos.write(tagString.getBytes());
            fos.close();
        } catch (Exception e){
            Log.e(LOG_TAG, "Tags Write error:" + e.getMessage());
        }
    }

    public void writeNoteCache(List<Notebook> notebooks) {
        Log.d(LOG_TAG, "Write note cache");
        try {
            String content = "";
            File file = new File(mContext.getCacheDir(), CACHE_NOTES);

            BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(file));
            ListIterator<Notebook> itr = notebooks.listIterator();
            while (itr.hasNext()) {
                Notebook notebook = itr.next();
                content += notebook.getGuid() + "," + notebook.getName() + "\n";
            }
            fos.write(content.getBytes());
            fos.close();
        } catch (Exception e){
            Log.e(LOG_TAG, "Note Write error:" + e.getMessage());
        }
    }

    public boolean writeAuthCache(String token, long expire_at) {
        Log.d(LOG_TAG, "Write auth cache");
        try {
            File file = new File(mContext.getCacheDir(), CACHE_TOKEN);
            BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(file));
            fos.write((Long.toString(expire_at) + "\n" + token).getBytes());
            fos.close();
        } catch (Exception e){
            Log.e(LOG_TAG, "Auth Write error:" + e.getMessage());
            return false;
        }
        return true;
    }
}

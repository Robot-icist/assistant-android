package com.assistant.main.helpers;

import android.content.Context;

import java.io.File;

public class Cache {
    public static void DeleteCache(Context context) {
        try {
            File dir = context.getCacheDir();
            deleteDir(dir);
        } catch (Exception e) { e.printStackTrace();}
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
            return dir.delete();
        } else if(dir!= null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

    public static void RemoveCache(Context context){
        try{
            context.getCacheDir().delete();
            //context.getCacheDir().deleteOnExit();
            context.getCodeCacheDir().delete();
            //context.getCodeCacheDir().deleteOnExit();
            context.getExternalCacheDir().delete();
            //context.getExternalCacheDir().deleteOnExit();
            context.getDataDir().delete();
            //context.getDataDir().deleteOnExit();
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
    }

    public static boolean ClearCache(Context context) {
        try {

            // create an array object of File type for referencing of cache files
            File[] files = context.getCacheDir().listFiles();

            // use a for etch loop to delete files one by one
            for (File file : files) {

                /* you can use just [ file.delete() ] function of class File
                 * or use if for being sure if file deleted
                 * here if file dose not delete returns false and condition will
                 * will be true and it ends operation of function by return
                 * false then we will find that all files are not delete
                 */
                if (!file.delete()) {
                    return false;         // not success
                }
            }

            // if for loop completes and process not ended it returns true
            return true;      // success of deleting files

        } catch (Exception e) {}

        // try stops deleting cache files
        return false;       // not success
    }
}

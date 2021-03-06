package com.cinder92.musicfiles;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;

import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.File;

import static android.media.MediaMetadataRetriever.METADATA_KEY_DURATION;


public class RNReactNativeGetMusicFilesModule extends ReactContextBaseJavaModule {
    private final ReactApplicationContext reactContext;
    private boolean getArtistFromSong = false;
    private boolean getDurationFromSong = true;
    private boolean getTitleFromSong = true;
    private boolean getIDFromSong = false;
    private boolean getGenreFromSong = false;
    private boolean getAlbumFromSong = true;
    private int minimumSongDuration = 0;
    private int songsPerIteration = 0;
    private int version = Build.VERSION.SDK_INT;
    private String[] STAR = {
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DURATION,
    };

    public RNReactNativeGetMusicFilesModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNReactNativeGetMusicFiles";
    }

    @ReactMethod
    public void getAll(ReadableMap options, final Callback successCallback, final Callback errorCallback) {
        if (options.hasKey("artist")) {
            getArtistFromSong = options.getBoolean("artist");
        }

        if (options.hasKey("duration")) {
            getDurationFromSong = options.getBoolean("duration");
        }

        if (options.hasKey("title")) {
            getTitleFromSong = options.getBoolean("title");
        }

        if (options.hasKey("id")) {
            getIDFromSong = options.getBoolean("id");
        }

        if (options.hasKey("genre")) {
            getGenreFromSong = options.getBoolean("genre");
        }

        if (options.hasKey("album")) {
            getAlbumFromSong = options.getBoolean("album");
        }

        if (options.hasKey("batchNumber")) {
            songsPerIteration = options.getInt("batchNumber");
        }

        if (options.hasKey("minimumSongDuration") && options.getInt("minimumSongDuration") > 0) {
            minimumSongDuration = options.getInt("minimumSongDuration");
        } else {
            minimumSongDuration = 0;
        }

        if(version <= 19){
            getSongs(successCallback,errorCallback);
        }else{
            Thread bgThread = new Thread(null,
                    new Runnable() {
                        @Override
                        public void run() {
                            getSongs(successCallback,errorCallback);
                        }
                    }, "asyncTask", 1024
            );
            bgThread.start();
        }
    }

    private void getSongs(final Callback successCallback, final Callback errorCallback){
        ContentResolver musicResolver = getCurrentActivity().getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";

        if(minimumSongDuration > 0){
            selection += " AND " + MediaStore.Audio.Media.DURATION + " >= " + minimumSongDuration;
        }

        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
        Cursor musicCursor = musicResolver.query(musicUri, STAR, selection, null, sortOrder);

        int pointer = 0;

        if (musicCursor != null && musicCursor.moveToFirst()) {
            if (musicCursor.getCount() > 0) {
                WritableArray jsonArray = new WritableNativeArray();
                WritableMap items;

                MediaMetadataRetriever mmr = new MediaMetadataRetriever();

                int idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);

                try {
                    do {
                        try {
                            items = new WritableNativeMap();

                            long songId = musicCursor.getLong(idColumn);

                            if (getIDFromSong) {
                                String str = String.valueOf(songId);
                                items.putString("id", str);
                            }

                            String songPath = musicCursor.getString(musicCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));

                            if (songPath != null && !songPath.equals("")) {
                                File file = new File(songPath);
                                String strFileName = file.getName();

                                items.putString("path", songPath);
                                items.putString("fileName", strFileName);

                                mmr.setDataSource(songPath);

                                String songTimeDuration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

                                if (getAlbumFromSong) {
                                    String songAlbum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                                    items.putString("album", songAlbum);
                                }

                                if (getArtistFromSong) {
                                    String songArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                                    items.putString("author", songArtist);
                                }


                                if (getTitleFromSong) {
                                    String songTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                                    items.putString("title", songTitle);
                                }

                                if (getDurationFromSong) {
                                    items.putString("duration", songTimeDuration);
                                }

                                jsonArray.pushMap(items);

                                if (songsPerIteration > 0) {

                                    if (songsPerIteration > musicCursor.getCount()) {
                                        if (pointer == (musicCursor.getCount() - 1)) {
                                            WritableMap params = Arguments.createMap();
                                            params.putArray("batch", jsonArray);
                                            sendEvent(reactContext, "onBatchReceived", params);
                                        }
                                    } else {
                                        if (songsPerIteration == jsonArray.size()) {
                                            WritableMap params = Arguments.createMap();
                                            params.putArray("batch", jsonArray);
                                            sendEvent(reactContext, "onBatchReceived", params);
                                            jsonArray = new WritableNativeArray();
                                        } else if (pointer == (musicCursor.getCount() - 1)) {
                                            WritableMap params = Arguments.createMap();
                                            params.putArray("batch", jsonArray);
                                            sendEvent(reactContext, "onBatchReceived", params);
                                        }
                                    }

                                    pointer++;
                                }
                            }

                        } catch (Exception e) {
                            pointer++;

                            continue; // This is redundant, but adds meaning
                        }

                    } while (musicCursor.moveToNext());

                    if (songsPerIteration == 0) {
                        successCallback.invoke(jsonArray);
                    }
                } catch (RuntimeException e) {
                    errorCallback.invoke(e.toString());
                } catch (Exception e) {
                    errorCallback.invoke(e.getMessage());
                } finally {
                    mmr.release();
                }
            }else{
                successCallback.invoke("Error, you dont' have any songs");
            }
        }else{
            errorCallback.invoke("Something get wrong with musicCursor");
        }
    }

    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}

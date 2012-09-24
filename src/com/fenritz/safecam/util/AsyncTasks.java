package com.fenritz.safecam.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.provider.MediaStore.Video.Thumbnails;
import android.util.Log;
import android.widget.ImageView;

import com.fenritz.safecam.R;
import com.fenritz.safecam.SafeCameraApplication;

public class AsyncTasks {
	public static abstract class OnAsyncTaskFinish {
		public void onFinish(){}
		public void onFinish(ArrayList<File> files){
			onFinish();
		}
		public void onFinish(Integer result){
			onFinish();
		}
	}
	
	
	public static class DecryptPopulateImage extends LimitedThreadAsyncTask<Void, Integer, Bitmap> {

		private final Context context;
		private final String filePath;
		private final ImageView image;
		private OnAsyncTaskFinish onFinish;
		private int size = 200;
		private MemoryCache memCache;


		public DecryptPopulateImage(Context context, String filePath, ImageView image) {
			this.context = context;
			this.filePath = filePath;
			this.image = image;
		}
		
		public void setSize(int size){
			this.size = size;
		}
		
		public void setCache(MemoryCache cache){
			memCache = cache;
		}
		
		public void setOnFinish(OnAsyncTaskFinish onFinish){
			this.onFinish = onFinish;
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			File file = new File(filePath);
			if (file.exists() && file.isFile()) {
				try {
					FileInputStream input = new FileInputStream(file);
					byte[] decryptedData = Helpers.getAESCrypt(context).decrypt(input, null, this);

					if (decryptedData != null) {
						Bitmap bitmap = Helpers.decodeBitmap(decryptedData, size);
						decryptedData = null;
						if (bitmap != null) {
							if(memCache != null){
								memCache.put(filePath, bitmap);
							}
							return bitmap;
						}
					}
					else {
						Log.d("sc", "Unable to decrypt: " + filePath);
					}
				}
				catch (FileNotFoundException e) { }
			}
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			super.onPostExecute(bitmap);

			if (bitmap != null) {
				image.setImageBitmap(bitmap);
				if(onFinish != null){
					onFinish.onFinish();
				}
			}
		}
	}
	
	public static class DeleteFiles extends AsyncTask<ArrayList<File>, Integer, ArrayList<File>> {

		private ProgressDialog progressDialog;
		private final Activity activity;
		private final OnAsyncTaskFinish finishListener;

		public DeleteFiles(Activity activity){
			this(activity, null);
		}
		
		public DeleteFiles(Activity activity, OnAsyncTaskFinish finishListener){
			this.activity = activity;
			this.finishListener = finishListener;
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					DeleteFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.deleting_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		@Override
		protected ArrayList<File> doInBackground(ArrayList<File>... params) {
			ArrayList<File> filesToDelete = params[0];
			progressDialog.setMax(filesToDelete.size());
			for (int i = 0; i < filesToDelete.size(); i++) {
				File file = filesToDelete.get(i);
				if (file.exists()){
					if(file.isFile()) {
						file.delete();
						
						File thumb = new File(Helpers.getThumbsDir(activity) + "/" + file.getName());

						if (thumb.exists() && thumb.isFile()) {
							thumb.delete();
						}
					}
					else if(file.isDirectory()){
						deleteFileFolder(file);
					}
				}

				publishProgress(i + 1);

				if (isCancelled()) {
					break;
				}
			}

			return filesToDelete;
		}
		
		private void deleteFileFolder(File fileOrDirectory) {
		    if (fileOrDirectory.isDirectory()){
		        for (File child : fileOrDirectory.listFiles()){
		        	deleteFileFolder(child);
		        }
		    }

		    fileOrDirectory.delete();
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(null);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(ArrayList<File> deletedFiles) {
			super.onPostExecute(deletedFiles);

			progressDialog.dismiss();
			
			if (finishListener != null) {
				finishListener.onFinish(deletedFiles);
			}
		}
	}
	
	public static class MoveFiles extends AsyncTask<ArrayList<File>, Integer, Void> {

		private ProgressDialog progressDialog;
		private final Activity activity;
		private final File destination;
		private final OnAsyncTaskFinish finishListener;

		public MoveFiles(Activity activity, File destination){
			this(activity, destination, null);
		}
		
		public MoveFiles(Activity activity, File destination, OnAsyncTaskFinish finishListener){
			this.activity = activity;
			this.finishListener = finishListener;
			this.destination = destination;
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					MoveFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.moving_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		@Override
		protected Void doInBackground(ArrayList<File>... params) {
			ArrayList<File> filesToMove = params[0];
			progressDialog.setMax(filesToMove.size());
			for (int i = 0; i < filesToMove.size(); i++) {
				File file = filesToMove.get(i);
				if (file.exists() && file.isFile()) {
					file.renameTo(new File(destination, file.getName()));
				}

				publishProgress(i + 1);

				if (isCancelled()) {
					break;
				}
			}

			return null;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(null);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			
			if (finishListener != null) {
				finishListener.onFinish();
			}
		}
	}
	
	public static class DecryptFiles extends AsyncTask<ArrayList<File>, Integer, ArrayList<File>> {

		private ProgressDialog progressDialog;
		private final Activity activity;
		private final String destinationFolder;
		private final OnAsyncTaskFinish finishListener;

		public DecryptFiles(Activity activity, String pDestinationFolder) {
			this(activity, pDestinationFolder, null);
		}

		public DecryptFiles(Activity activity, String pDestinationFolder, OnAsyncTaskFinish pFinishListener) {
			this.activity = activity;
			destinationFolder = pDestinationFolder;
			finishListener = pFinishListener;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					DecryptFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.decrypting_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		@Override
		protected ArrayList<File> doInBackground(ArrayList<File>... params) {
			ArrayList<File> filesToDecrypt = params[0];
			ArrayList<File> decryptedFiles = new ArrayList<File>();

			progressDialog.setMax(filesToDecrypt.size());

			for (int i = 0; i < filesToDecrypt.size(); i++) {
				File file = filesToDecrypt.get(i);
				if (file.exists() && file.isFile()) {
					String destFileName = file.getName();
					if (destFileName.substring(destFileName.length() - 3).equalsIgnoreCase(activity.getString(R.string.file_extension))) {
						destFileName = destFileName.substring(0, destFileName.length() - 3);
					}

					try {
						FileInputStream inputStream = new FileInputStream(file);
						FileOutputStream outputStream = new FileOutputStream(new File(destinationFolder, destFileName));

						Helpers.getAESCrypt(activity).decrypt(inputStream, outputStream, null, this);

						publishProgress(i+1);
						decryptedFiles.add(new File(destinationFolder + "/" + destFileName));
					}
					catch (FileNotFoundException e) { }
				}

				if (isCancelled()) {
					break;
				}
			}

			return decryptedFiles;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(null);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(ArrayList<File> decryptedFiles) {
			super.onPostExecute(decryptedFiles);

			progressDialog.dismiss();
			if (finishListener != null) {
				finishListener.onFinish(decryptedFiles);
			}
		}
	}
	
	public static class ReEncryptFiles extends AsyncTask<HashMap<String, Object>, Integer, ArrayList<File>> {

		private ProgressDialog progressDialog;
		private final OnAsyncTaskFinish finishListener;
		private final Activity activity;

		public ReEncryptFiles(Activity activity, OnAsyncTaskFinish pFinishListener) {
			this.activity = activity;
			finishListener = pFinishListener;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					ReEncryptFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.reencrypting_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		@SuppressWarnings("unchecked")
		@Override
		protected ArrayList<File> doInBackground(HashMap<String, Object>... params) {
			ArrayList<File> reencryptedFiles = new ArrayList<File>();
			String newPassword = params[0].get("newPassword").toString();
			ArrayList<File> files = (ArrayList<File>) params[0].get("files");

			try {
				newPassword = AESCrypt.byteToHex(AESCrypt.getHash(newPassword));
			}
			catch (AESCryptException e1) {
				return null;
			}
			
			AESCrypt newCrypt = Helpers.getAESCrypt(newPassword, activity);

			progressDialog.setMax(files.size());


			int counter = 0;
			for (File file : files) {
				try {
					FileInputStream inputStream = new FileInputStream(file);
					String tmpFilePath = Helpers.getHomeDir(activity) + "/.tmp/" + file.getName();
					
					File tmpFile = new File(tmpFilePath);
					FileOutputStream outputStream = new FileOutputStream(tmpFile);

					if(Helpers.getAESCrypt(activity).reEncrypt(inputStream, outputStream, newCrypt, null, this)){
						reencryptedFiles.add(tmpFile);
					}
					else{
						if(tmpFile.isFile()){
							tmpFile.delete();
						}
					}
					publishProgress(++counter);
				}
				catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}

			return reencryptedFiles;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(ArrayList<File> reencryptedFiles) {
			super.onPostExecute(reencryptedFiles);

			progressDialog.dismiss();
			
			if (finishListener != null) {
				finishListener.onFinish(reencryptedFiles);
			}

		}
	}
	
	public static class EncryptFiles extends AsyncTask<String, Integer, Void> {

		private ProgressDialog progressDialog;
		private final Activity activity;
		private final OnAsyncTaskFinish finishListener;
		private final String destinationFolder;

		public EncryptFiles(Activity activity, String destinationFolder) {
			this(activity, destinationFolder, null);
		}
		
		public EncryptFiles(Activity activity, String destinationFolder, OnAsyncTaskFinish pFinishListener) {
			this.activity = activity;
			finishListener = pFinishListener;
			this.destinationFolder = destinationFolder;
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					EncryptFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.encrypting_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		@Override
		protected Void doInBackground(String... params) {
			progressDialog.setMax(params.length);
			for (int i = 0; i < params.length; i++) {
				File origFile = new File(params[i]);

				if (origFile.exists() && origFile.isFile()) {
					FileInputStream inputStream;
					try {
						inputStream = new FileInputStream(origFile);

						String destFilePath = Helpers.findNewFileNameIfNeeded(activity, destinationFolder, origFile.getName())  + activity.getString(R.string.file_extension);
						// String destFilePath =
						// findNewFileNameIfNeeded(Helpers.getHomeDir(GalleryActivity.this),
						// origFile.getName());

						FileOutputStream outputStream = new FileOutputStream(destFilePath);

						Helpers.getAESCrypt(activity).encrypt(inputStream, outputStream, null, this);
						publishProgress(i+1);
					}
					catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				}
			}

			return null;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(null);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			
			if (finishListener != null) {
				finishListener.onFinish();
			}
		}
	}
	
	public static class ImportFiles extends AsyncTask<HashMap<String, Object>, Integer, Integer> {

		public static final int STATUS_OK = 0;
		public static final int STATUS_FAIL = 1;
		public static final int STATUS_CANCEL = 2;

		private ProgressDialog progressDialog;
		private final Activity activity;
		private final OnAsyncTaskFinish finishListener;
		private final String destinationFolder;
		
		public ImportFiles(Activity activity, String destinationFolder){
			this(activity, destinationFolder, null);
		}
		
		public ImportFiles(Activity activity, String destinationFolder, OnAsyncTaskFinish finishListener){
			this.activity = activity;
			this.finishListener = finishListener;
			this.destinationFolder = destinationFolder;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progressDialog = new ProgressDialog(activity);
			progressDialog.setCancelable(true);
			progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					ImportFiles.this.cancel(false);
				}
			});
			progressDialog.setMessage(activity.getString(R.string.importing_files));
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressDialog.show();
		}

		@Override
		protected Integer doInBackground(HashMap<String, Object>... rparams) {
			HashMap<String, Object> params = rparams[0];
			int returnStatus = STATUS_OK;

			String[] filePaths = (String[]) params.get("filePaths");
			String password = (String) params.get("password");
			Boolean deleteAfterImport = (Boolean) params.get("deleteAfterImport");

			try {
				password = AESCrypt.byteToHex(AESCrypt.getHash(password));
			}
			catch (AESCryptException e1) {
				returnStatus = STATUS_FAIL;
				return returnStatus;
			}
			
			AESCrypt newCrypt = Helpers.getAESCrypt(password, activity);

			progressDialog.setMax(filePaths.length);
			for (int i = 0; i < filePaths.length; i++) {

				File origFile = new File(filePaths[i]);

				if (origFile.exists() && origFile.isFile()) {
					try {
						FileInputStream inputStream = new FileInputStream(origFile);

						String destFilePath = Helpers.findNewFileNameIfNeeded(activity, destinationFolder, origFile.getName());

						FileOutputStream outputStream = new FileOutputStream(destFilePath);
						if(Helpers.getAESCrypt(activity).reEncrypt(inputStream, outputStream, newCrypt, null, this, true)){
							if (deleteAfterImport) {
								origFile.delete();
							}
						}
						else{
							File dstFile = new File(destFilePath);
							if(dstFile.isFile()){
								dstFile.delete();
							}
							returnStatus = STATUS_FAIL;
						}
						publishProgress(i+1);
					}
					catch (FileNotFoundException e) {
						returnStatus = STATUS_FAIL;
					}
				}
			}

			return returnStatus;
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();

			this.onPostExecute(STATUS_CANCEL);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);

			progressDialog.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);

			progressDialog.dismiss();
			if (finishListener != null) {
				finishListener.onFinish(result);
			}
		}

	}
	
	public static class ShowImageThumb extends AsyncTask<File, Void, Bitmap> {

		private final ImageView imageView;
		private final MemoryCache cache;
		private OnAsyncTaskFinish finishListener;
		
		
		public ShowImageThumb(ImageView pImageView, MemoryCache pCache){
			this(pImageView, pCache, null);
		}
		
		public ShowImageThumb(ImageView pImageView, MemoryCache pCache, OnAsyncTaskFinish pFinishListener){
			imageView = pImageView;
			cache = pCache;
			finishListener = pFinishListener;
		}
		
		public void setOnFinish(OnAsyncTaskFinish onFinish){
			this.finishListener = onFinish;
		}
		
		@Override
		protected Bitmap doInBackground(File... params) {
			Bitmap image = Helpers.decodeFile(params[0], Helpers.getThumbSize(SafeCameraApplication.getAppContext()));
			//image = Helpers.getThumbFromBitmap(image, Helpers.getThumbSize(SafeCameraApplication.getAppContext()));
			
			if(image != null){
				cache.put(params[0].getPath(), image);
				return image;
			}
			
			image = ThumbnailUtils.createVideoThumbnail(params[0].getPath(), Thumbnails.MICRO_KIND);
			
			if(image != null){
				cache.put(params[0].getPath(), image);
				return image;
			}
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);
			if(result != null){
				imageView.setImageBitmap(result);
			}
			if (finishListener != null) {
				finishListener.onFinish();
			}
		}

	}
	
	public static class ShowSystemImageThumb extends AsyncTask<File, Void, Bitmap> {

		private final ImageView imageView;
		private final MemoryCache cache;
		private OnAsyncTaskFinish finishListener;
		private final int imgId;
		private final int orientation;
		
		
		public ShowSystemImageThumb(ImageView pImageView, int pImgId, int pOrientation, MemoryCache pCache){
			this(pImageView, pImgId, pOrientation, pCache, null);
		}
		
		public ShowSystemImageThumb(ImageView pImageView, int pImgId, int pOrientation, MemoryCache pCache, OnAsyncTaskFinish pFinishListener){
			imageView = pImageView;
			cache = pCache;
			imgId = pImgId;
			orientation = pOrientation;
			finishListener = pFinishListener;
		}
		
		public void setOnFinish(OnAsyncTaskFinish onFinish){
			this.finishListener = onFinish;
		}
		
		private Bitmap getBitmap(Bitmap image){
			if(orientation != 0){
				Matrix matrix = new Matrix();
				matrix.postRotate(orientation);
				image = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);
			}
			return image;
		}
		
		@Override
		protected Bitmap doInBackground(File... params) {
			
			// Get from cache
			Bitmap image = cache.get(params[0].getPath());
			if(image != null){
				return image;
			}
			
			try{
				// Get from MediaStore
				image = MediaStore.Images.Thumbnails.getThumbnail(SafeCameraApplication.getAppContext().getContentResolver(), imgId, MediaStore.Images.Thumbnails.MINI_KIND, null);
				if(image != null){
					image = getBitmap(image);
					cache.put(params[0].getPath(), image);
					return image;
				}
				
				// Get from File and Resize it
				image = Helpers.decodeFile(params[0], Helpers.getThumbSize(SafeCameraApplication.getAppContext()));
				
				if(image != null){
					cache.put(params[0].getPath(), image);
					return image;
				}
				
				// Get from Video
				image = ThumbnailUtils.createVideoThumbnail(params[0].getPath(), Thumbnails.MINI_KIND);
				if(image != null){
					cache.put(params[0].getPath(), image);
					return image;
				}
			}
			catch(OutOfMemoryError e){}
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);
			if(result != null){
				imageView.setImageBitmap(result);
			}
			if (finishListener != null) {
				finishListener.onFinish();
			}
		}

	}
}

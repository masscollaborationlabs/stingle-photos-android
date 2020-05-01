package org.stingle.photos.Gallery.Albums;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.squareup.picasso3.Picasso;
import com.squareup.picasso3.RequestHandler;

import org.stingle.photos.Crypto.Crypto;
import org.stingle.photos.Crypto.CryptoException;
import org.stingle.photos.Db.Objects.StingleContact;
import org.stingle.photos.Db.Objects.StingleDbAlbum;
import org.stingle.photos.Db.Objects.StingleDbFile;
import org.stingle.photos.Db.Query.AlbumFilesDb;
import org.stingle.photos.Db.Query.AlbumsDb;
import org.stingle.photos.Db.Query.ContactsDb;
import org.stingle.photos.Db.StingleDb;
import org.stingle.photos.Files.FileManager;
import org.stingle.photos.R;
import org.stingle.photos.StinglePhotosApplication;
import org.stingle.photos.Sync.SyncManager;
import org.stingle.photos.Util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class AlbumsPicassoLoader extends RequestHandler {

	public static int MEMBERS_IN_SUBTITLE = 3;

	private Context context;
	private AlbumsDb db;
	private AlbumFilesDb filesDb;
	private ContactsDb contactsDb;
	private int view;
	private int thumbSize;
	private Crypto crypto;

	public AlbumsPicassoLoader(Context context, AlbumsDb db, AlbumFilesDb filesDb, int view, int thumbSize){
		this.context = context;
		this.db = db;
		this.filesDb = filesDb;
		this.thumbSize = thumbSize;
		this.view = view;

		this.contactsDb = new ContactsDb(context);
		this.crypto = StinglePhotosApplication.getCrypto();
	}

	@Override
	public boolean canHandleRequest(com.squareup.picasso3.Request data) {
		if(data.uri.toString().startsWith("a")){
			return true;
		}
		return false;
	}

	@Override
	public void load(@NonNull Picasso picasso, @NonNull com.squareup.picasso3.Request request, @NonNull Callback callback) throws IOException {
		String uri = request.uri.toString();
		String position = uri.substring(1);


			try {
				StingleDbAlbum album = db.getAlbumAtPosition(Integer.parseInt(position), StingleDb.SORT_DESC, AlbumsFragment.getAlbumIsHiddenByView(view), AlbumsFragment.getAlbumIsSharedByView(view));
				Crypto.AlbumData albumData = StinglePhotosApplication.getCrypto().parseAlbumData(album.publicKey, album.encPrivateKey, album.metadata);

				Result result = null;

				StingleDbFile albumDbFile = filesDb.getFileAtPosition(0, album.albumId, StingleDb.SORT_ASC);

				if(albumDbFile != null){
					byte[] decryptedData;
					if(albumDbFile.isLocal) {
						File fileToDec = new File(FileManager.getThumbsDir(context) + "/" + albumDbFile.filename);
						FileInputStream input = new FileInputStream(fileToDec);
						decryptedData = crypto.decryptFile(input, crypto.getThumbHeaderFromHeadersStr(albumDbFile.headers, albumData.privateKey, albumData.publicKey));
					}
					else{
						byte[] encFile = FileManager.getAndCacheThumb(context, albumDbFile.filename, SyncManager.ALBUM);
						decryptedData = crypto.decryptFile(encFile, crypto.getThumbHeaderFromHeadersStr(albumDbFile.headers, albumData.privateKey, albumData.publicKey));
					}

					if (decryptedData != null) {
						Bitmap bitmap = Helpers.decodeBitmap(decryptedData, thumbSize);
						bitmap = Helpers.getThumbFromBitmap(bitmap, thumbSize);

						result = new Result(bitmap, Picasso.LoadedFrom.DISK);

					}
				}

				if(result == null) {
					Drawable drawable = context.getDrawable(R.drawable.ic_no_image);
					if (drawable == null) {
						return;
					}
					result = new Result(drawable, Picasso.LoadedFrom.DISK);
				}

				AlbumsAdapterPisasso.AlbumProps props = new AlbumsAdapterPisasso.AlbumProps();

				props.name = albumData.name;
				if(view == AlbumsFragment.VIEW_SHARES){
					props.subtitle = getMembersString(album);
					if(album.isOwner){
						props.rightIcon = AlbumsAdapterPisasso.AlbumProps.ICON_SENT;
					}
					else{
						props.rightIcon = AlbumsAdapterPisasso.AlbumProps.ICON_RECEIVED;
					}
				}
				else if(view == AlbumsFragment.VIEW_ALL && album.isShared && album.isHidden){
					props.subtitle = getMembersString(album);
				}
				else{
					props.subtitle = context.getString(R.string.album_items_count, filesDb.getTotalFilesCount(album.albumId));
				}
				result.addProperty("albumProps", props);

				callback.onSuccess(result);

			} catch (IOException | CryptoException e) {
				e.printStackTrace();
			}

	}

	private String getMembersString(StingleDbAlbum album){
		String myUserId = Helpers.getPreference(context, StinglePhotosApplication.USER_ID, "");
		StringBuilder membersStr = new StringBuilder();
		int count = 0;
		boolean addOthers = false;
		for(String userId : album.members){
			if(userId.equals(myUserId)){
				continue;
			}
			if(count >= MEMBERS_IN_SUBTITLE){
				addOthers = true;
				break;
			}
			StingleContact contact = contactsDb.getContactByUserId(Long.parseLong(userId));
			if(contact != null){
				if(membersStr.length() > 0){
					membersStr.append(", ");
				}
				membersStr.append(contact.email);
			}
			count++;
		}

		if(addOthers){
			int othersCount = album.members.size() - count;
			if(othersCount > 0){
				membersStr.append(" " + context.getString(R.string.count_others, othersCount));
			}
		}

		return membersStr.toString();
	}
}
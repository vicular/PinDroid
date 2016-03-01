/*
 * PinDroid - http://code.google.com/p/PinDroid/
 *
 * Copyright (C) 2010 Matt Schmidt
 *
 * PinDroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * PinDroid is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PinDroid; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */

package com.pindroid.providers;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import android.accounts.AccountManager;
import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import com.pindroid.Constants;
import com.pindroid.R;
import com.pindroid.event.AccountChangedEvent;
import com.pindroid.model.Bookmark;
import com.pindroid.model.Note;
import com.pindroid.model.SearchSuggestion;
import com.pindroid.model.Tag;

import org.greenrobot.eventbus.EventBus;

public class BookmarkContentProvider extends ContentProvider {

	private DatabaseHelper dbHelper;
	private static final String BOOKMARK_TABLE_NAME = "bookmark";
	private static final String TAG_TABLE_NAME = "tag";
	private static final String NOTE_TABLE_NAME = "note";
	
	private static final int Bookmarks = 1;
	private static final int SearchSuggest = 2;
	private static final int Tags = 3;
	private static final int TagSearchSuggest = 4;
	private static final int BookmarkSearchSuggest = 5;
	private static final int Notes = 6;
	private static final int NoteSearchSuggest = 7;
	private static final int GlobalSearchSuggest = 8;
	private static final int UnreadCount = 9;
	private static final int NoteId = 10;
	private static final int BookmarkId = 11;
	
	
	private static final String SuggestionLimit = "10";
	
	private static final UriMatcher sURIMatcher = buildUriMatcher();
	
	public static final String AUTHORITY = "com.pindroid.providers.BookmarkContentProvider";
	
	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		int count;
		switch (sURIMatcher.match(uri)) {
			case Bookmarks:
				count = db.delete(BOOKMARK_TABLE_NAME, where, whereArgs);
				getContext().getContentResolver().notifyChange(uri, null, false);
				break;
			case Tags:
				count = db.delete(TAG_TABLE_NAME, where, whereArgs);
				getContext().getContentResolver().notifyChange(uri, null, false);
				break;
			case Notes:
				count = db.delete(NOTE_TABLE_NAME, where, whereArgs);
				getContext().getContentResolver().notifyChange(uri, null, false);
				break;
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
		
		return count;
	}

	@Override
	public String getType(Uri uri) {
		switch(sURIMatcher.match(uri)){
			case Bookmarks:
			case BookmarkId:
				return Bookmark.CONTENT_TYPE;
			case SearchSuggest:
				return SearchManager.SUGGEST_MIME_TYPE;
			case Tags:
				return Tag.CONTENT_TYPE;
			case NoteId:
			case Notes:
				return Note.CONTENT_TYPE;
			case UnreadCount:
				return Bookmark.CONTENT_TYPE;
			default:
				throw new IllegalArgumentException("Unknown Uri " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		
		switch(sURIMatcher.match(uri)) {
			case Bookmarks:
				return insertBookmark(uri, values);
			case Tags:
				return insertTag(uri, values);
			case Notes:
				return insertNote(uri, values);
			default:
				throw new IllegalArgumentException("Unknown Uri: " + uri);
		}
	}
	
	private Uri insertBookmark(Uri uri, ContentValues values){
        SQLiteDatabase db = dbHelper.getWritableDatabase();
		long rowId = db.insert(BOOKMARK_TABLE_NAME, "", values);
		if(rowId > 0) {
			Uri rowUri = ContentUris.appendId(Bookmark.CONTENT_URI.buildUpon(), rowId).build();
			getContext().getContentResolver().notifyChange(rowUri, null, true);
			return rowUri;
		}
		throw new SQLException("Failed to insert row into " + uri);
	}

	private Uri insertTag(Uri uri, ContentValues values){
        SQLiteDatabase db = dbHelper.getWritableDatabase();
		long rowId = db.insert(TAG_TABLE_NAME, "", values);
		if(rowId > 0) {
			Uri rowUri = ContentUris.appendId(Tag.CONTENT_URI.buildUpon(), rowId).build();
			getContext().getContentResolver().notifyChange(rowUri, null);
			return rowUri;
		}
		throw new SQLException("Failed to insert row into " + uri);
	}
	
	private Uri insertNote(Uri uri, ContentValues values){
        SQLiteDatabase db = dbHelper.getWritableDatabase();
		long rowId = db.insert(NOTE_TABLE_NAME, "", values);
		if(rowId > 0) {
			Uri rowUri = ContentUris.appendId(Note.CONTENT_URI.buildUpon(), rowId).build();
			getContext().getContentResolver().notifyChange(rowUri, null);
			return rowUri;
		}
		throw new SQLException("Failed to insert row into " + uri);
	}
	
	@Override
	public boolean onCreate() {
		dbHelper = new DatabaseHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,	String[] selectionArgs, String sortOrder) {
		switch(sURIMatcher.match(uri)) {
			case Bookmarks:
				return getBookmarks(uri, projection, selection, selectionArgs, sortOrder);
			case BookmarkId:
				return getBookmark(uri, projection, selection, selectionArgs, sortOrder);
			case GlobalSearchSuggest:
				String globalQquery = uri.getLastPathSegment().toLowerCase(Locale.ENGLISH);
				return getSearchSuggestions(globalQquery, false);
			case SearchSuggest:
				String query = uri.getLastPathSegment().toLowerCase(Locale.ENGLISH);
				return getSearchSuggestions(query, true);
			case Tags:
				return getTags(uri, projection, selection, selectionArgs, sortOrder);
			case TagSearchSuggest:
				String tagQuery = uri.getLastPathSegment().toLowerCase(Locale.ENGLISH);
				return getSearchCursor(getTagSearchSuggestions(tagQuery, true));
			case BookmarkSearchSuggest:
				String bookmarkQuery = uri.getLastPathSegment().toLowerCase(Locale.ENGLISH);
				return getSearchCursor(getBookmarkSearchSuggestions(bookmarkQuery, true));
			case Notes:
				return getNotes(uri, projection, selection, selectionArgs, sortOrder);
			case NoteId:
				return getNote(uri, projection, selection, selectionArgs, sortOrder);
			case NoteSearchSuggest:
				String noteQuery = uri.getLastPathSegment().toLowerCase(Locale.ENGLISH);
				return getSearchCursor(getNoteSearchSuggestions(noteQuery, true));
			case UnreadCount:
				SQLiteDatabase rdb = dbHelper.getReadableDatabase();
				return rdb.rawQuery("select count(*) as Count, ACCOUNT as Account from " + BOOKMARK_TABLE_NAME + " where " + Bookmark.ToRead + "=1 group by " + Bookmark.Account, null);
			default:
				throw new IllegalArgumentException("Unknown Uri: " + uri);
		}
	}
	
	private int getAccountCount() {
		return AccountManager.get(getContext()).getAccountsByType(Constants.ACCOUNT_TYPE).length;
	}
	
	private Cursor getBookmark(Uri uri, String[] projection, String selection,	String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		SQLiteDatabase rdb = dbHelper.getReadableDatabase();
		qb.setTables(BOOKMARK_TABLE_NAME);
		qb.appendWhere(Bookmark._ID + "=" + uri.getPathSegments().get(Bookmark.BOOKMARK_ID_PATH_POSITION));
		Cursor c = qb.query(rdb, projection, selection, selectionArgs, null, null, sortOrder, null);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}
	
	private Cursor getBookmarks(Uri uri, String[] projection, String selection,	String[] selectionArgs, String sortOrder) {
		return getBookmarks(uri, projection, selection, selectionArgs, sortOrder, null);
	}
	
	private Cursor getBookmarks(Uri uri, String[] projection, String selection,	String[] selectionArgs, String sortOrder, String limit) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		SQLiteDatabase rdb = dbHelper.getReadableDatabase();
		qb.setTables(BOOKMARK_TABLE_NAME);
		Cursor c = qb.query(rdb, projection, selection, selectionArgs, null, null, sortOrder, limit);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}
	
	private Cursor getTags(Uri uri, String[] projection, String selection,	String[] selectionArgs, String sortOrder) {
		return getTags(uri, projection, selection, selectionArgs, sortOrder, null);
	}
	
	private Cursor getTags(Uri uri, String[] projection, String selection,	String[] selectionArgs, String sortOrder, String limit) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		SQLiteDatabase rdb = dbHelper.getReadableDatabase();
		qb.setTables(TAG_TABLE_NAME);
		Cursor c = qb.query(rdb, projection, selection, selectionArgs, null, null, sortOrder, limit);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}
	
	private Cursor getNote(Uri uri, String[] projection, String selection,	String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		SQLiteDatabase rdb = dbHelper.getReadableDatabase();
		qb.setTables(NOTE_TABLE_NAME);
		qb.appendWhere(Note._ID + "=" + uri.getPathSegments().get(Note.NOTE_ID_PATH_POSITION));
		Cursor c = qb.query(rdb, projection, selection, selectionArgs, null, null, sortOrder, null);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}
	
	private Cursor getNotes(Uri uri, String[] projection, String selection,	String[] selectionArgs, String sortOrder) {
		return getNotes(uri, projection, selection, selectionArgs, sortOrder, null);
	}
	
	private Cursor getNotes(Uri uri, String[] projection, String selection,	String[] selectionArgs, String sortOrder, String limit) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		SQLiteDatabase rdb = dbHelper.getReadableDatabase();
		qb.setTables(NOTE_TABLE_NAME);
		Cursor c = qb.query(rdb, projection, selection, selectionArgs, null, null, sortOrder, limit);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}
	
	private Cursor getSearchSuggestions(String query, boolean accountSpecific) {
		Log.d("getSearchSuggestions", query);
		
		Map<String, SearchSuggestion> tagSuggestions = getTagSearchSuggestions(query, accountSpecific);
		Map<String, SearchSuggestion> bookmarkSuggestions = getBookmarkSearchSuggestions(query, accountSpecific);
		Map<String, SearchSuggestion> noteSuggestions = getNoteSearchSuggestions(query, accountSpecific);

		SortedMap<String, SearchSuggestion> s = new TreeMap<>();
		s.putAll(tagSuggestions);
		s.putAll(bookmarkSuggestions);
		s.putAll(noteSuggestions);
		
		return getSearchCursor(s);
	}
	
	private Map<String, SearchSuggestion> getBookmarkSearchSuggestions(String query, boolean accountSpecific) {
		String[] bookmarks = query.split(" ");
		
		Map<String, SearchSuggestion> suggestions = new TreeMap<>();
				
		// Title/description/notes search suggestions
		SQLiteQueryBuilder bookmarkqb = new SQLiteQueryBuilder();	
		bookmarkqb.setTables(BOOKMARK_TABLE_NAME);
		
		ArrayList<String> bookmarkList = new ArrayList<>();
		final ArrayList<String> selectionlist = new ArrayList<>();
		
		for(String s : bookmarks) {
			bookmarkList.add("(" + Bookmark.Description + " LIKE ? OR " + 
					Bookmark.Notes + " LIKE ?)");
			
			selectionlist.add("%" + s + "%");
			selectionlist.add("%" + s + "%");
			
			if(accountSpecific){
				bookmarkList.add(Bookmark.Account + "=?");
				selectionlist.add(EventBus.getDefault().getStickyEvent(AccountChangedEvent.class).getNewAccount());
			}
		}
		
		String selection = TextUtils.join(" AND ", bookmarkList);
		
		String[] projection = new String[] {BaseColumns._ID, Bookmark.Description, Bookmark.Url, Bookmark.Account};

		Cursor c = getBookmarks(Bookmark.CONTENT_URI, projection, selection, selectionlist.toArray(new String[]{}), null, SuggestionLimit);
		
		if(c.moveToFirst()){
            Bookmark b = new Bookmark(c);
			int accountCount = getAccountCount();

			do {
				String account = b.getAccount();
		    	
				Uri data;
		    	Uri.Builder builder = new Uri.Builder();
	    		builder.scheme(Constants.CONTENT_SCHEME);
	    		builder.encodedAuthority(account + "@" + Constants.INTENT_URI);
	    		builder.appendEncodedPath("bookmarks");
	    		builder.appendEncodedPath(Integer.toString(b.getId()));
	    		data = builder.build();

				String line2 = b.getUrl();
				String url = b.getUrl();
				
				if(!accountSpecific && accountCount > 1) {
					line2 = account;
					url = null;
				}
				
				suggestions.put(b.getDescription() + "_bookmark_" + account, new SearchSuggestion(b.getDescription(),
					line2, url, R.drawable.ic_bookmark_blue_24dp,	data.toString(), Constants.ACTION_SEARCH_SUGGESTION_VIEW));
				
			} while(c.moveToNext());	
		}
		c.close();

		return suggestions;
	}
	
	private Map<String, SearchSuggestion> getTagSearchSuggestions(String query, boolean accountSpecific) {
		Log.d("getTagSearchSuggestions", query);
		
		Resources res = getContext().getResources();
		
		String[] tags = query.split(" ");
		
		Map<String, SearchSuggestion> suggestions = new TreeMap<>();
		
		// Tag search suggestions
		SQLiteQueryBuilder tagqb = new SQLiteQueryBuilder();	
		tagqb.setTables(TAG_TABLE_NAME);
		
		ArrayList<String> tagList = new ArrayList<>();
		final ArrayList<String> selectionlist = new ArrayList<>();
		
		for(String s : tags){
			tagList.add(Tag.Name + " LIKE ?");
			selectionlist.add("%" + s + "%");
			
			if(accountSpecific){
				tagList.add(Bookmark.Account + "=?");
				selectionlist.add(EventBus.getDefault().getStickyEvent(AccountChangedEvent.class).getNewAccount());
			}
		}
		
		String selection = TextUtils.join(" AND ", tagList);

		String[] projection = new String[] {BaseColumns._ID, Tag.Name, Tag.Count, Tag.Account};

		Cursor c = getTags(Tag.CONTENT_URI, projection, selection, selectionlist.toArray(new String[]{}), null, SuggestionLimit);
		
		if(c.moveToFirst()){
			Tag t = new Tag(c);
			
			int accountCount = getAccountCount();

			do {
				String account = t.getAccount();
				String name = t.getTagName();
				
				Uri.Builder data = new Uri.Builder();
				data.scheme(Constants.CONTENT_SCHEME);
				data.encodedAuthority(account + "@" + Constants.INTENT_URI);
				data.appendEncodedPath("bookmarks");
				data.appendQueryParameter("tagname", name);
				
				String tagCount = Integer.toString(t.getCount()) + " " + res.getString(R.string.bookmark_count);
				
				if(!accountSpecific && accountCount > 1)
					tagCount = account;
				
				suggestions.put(name + "_tag_" + account, new SearchSuggestion(name,
					tagCount, R.drawable.ic_label_gray_24dp, data.build().toString(), Constants.ACTION_SEARCH_SUGGESTION_VIEW));
				
			} while(c.moveToNext());	
		}
		c.close();

		return suggestions;
	}
	
	private Map<String, SearchSuggestion> getNoteSearchSuggestions(String query, boolean accountSpecific) {
		String[] notes = query.split(" ");
		
		Map<String, SearchSuggestion> suggestions = new TreeMap<>();
		
		// Tag search suggestions
		SQLiteQueryBuilder noteqb = new SQLiteQueryBuilder();	
		noteqb.setTables(NOTE_TABLE_NAME);
		
		ArrayList<String> noteList = new ArrayList<>();
		final ArrayList<String> selectionlist = new ArrayList<>();
		
		for(String s : notes) {
			noteList.add("(" + Note.Title + " LIKE ? OR " + 
					Note.Text + " LIKE ?)");
			selectionlist.add("%" + s + "%");
			selectionlist.add("%" + s + "%");
			
			if(accountSpecific){
				noteList.add(Bookmark.Account + "=?");
				selectionlist.add(EventBus.getDefault().getStickyEvent(AccountChangedEvent.class).getNewAccount());
			}
		}
		
		String selection = TextUtils.join(" AND ", noteList);

		String[] projection = new String[] {BaseColumns._ID, Note.Title, Note.Text, Note.Account};

		Cursor c = getNotes(Tag.CONTENT_URI, projection, selection, selectionlist.toArray(new String[]{}), null, SuggestionLimit);
		
		if(c.moveToFirst()){
            Note n = new Note(c);
			int accountCount = getAccountCount();

			do {
				String text = n.getText();
				String account = n.getAccount();
				
				Uri data;
				Uri.Builder builder = new Uri.Builder();
				builder.scheme(Constants.CONTENT_SCHEME);
				builder.encodedAuthority(account + "@" + Constants.INTENT_URI);
				builder.appendEncodedPath("notes");
				builder.appendEncodedPath(Integer.toString(n.getId()));
	    		data = builder.build();
				
				if(!accountSpecific && accountCount > 1)
					text = account;
				
				suggestions.put(n.getTitle() + "_note_" + account, new SearchSuggestion(n.getTitle(),
					text, R.drawable.ic_note_brown_24dp, data.toString(), Constants.ACTION_SEARCH_SUGGESTION_VIEW));
				
			} while(c.moveToNext());	
		}
		c.close();

		return suggestions;
	}
	
	private Cursor getSearchCursor(Map<String, SearchSuggestion> list) {
    	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this.getContext());
    	Boolean icons = settings.getBoolean("pref_searchicons", true);

    	MatrixCursor mc;
    	
    	if(icons) {
			mc = new MatrixCursor(new String[] {BaseColumns._ID, 
					SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2, SearchManager.SUGGEST_COLUMN_TEXT_2_URL,
					SearchManager.SUGGEST_COLUMN_INTENT_DATA, SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
					SearchManager.SUGGEST_COLUMN_ICON_2});
	
			int i = 0;
			
			for(SearchSuggestion s : list.values()) {
				mc.addRow(new Object[]{ i++, s.getText1(), s.getText2(), s.getText2Url(), s.getIntentData(), s.getIntentAction(),
					s.getIcon2() });
			}
    	} else {
			mc = new MatrixCursor(new String[] {BaseColumns._ID, 
					SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2, SearchManager.SUGGEST_COLUMN_TEXT_2_URL,
					SearchManager.SUGGEST_COLUMN_INTENT_DATA, SearchManager.SUGGEST_COLUMN_INTENT_ACTION});
	
			int i = 0;
			
			for(SearchSuggestion s : list.values()) {
				mc.addRow(new Object[]{ i++, s.getText1(), s.getText2(), s.getText2Url(), s.getIntentData(), s.getIntentAction() });
			}
    	}
		
		return mc;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		int count;
		switch (sURIMatcher.match(uri)) {
			case Bookmarks:
			case BookmarkId:
				count = db.update(BOOKMARK_TABLE_NAME, values, selection, selectionArgs);
				break;
			case Tags:
				count = db.update(TAG_TABLE_NAME, values, selection, selectionArgs);
				break;
			case Notes:
				count = db.update(NOTE_TABLE_NAME, values, selection, selectionArgs);
				break;
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
		
		boolean syncOnly = values.size() == 1 && values.containsKey(Bookmark.Synced) && values.getAsInteger(Bookmark.Synced) == 1;
		
		getContext().getContentResolver().notifyChange(uri, null, !syncOnly);
		return count;
	}
	
	public DatabaseHelper getDatabaseHelper(){
		return dbHelper;
	}
	
	@Override
	public int bulkInsert(Uri uri, ContentValues[] values){
		
		int result = 0;
		
		switch(sURIMatcher.match(uri)) {
			case Bookmarks:
				result = bulkLoad(BOOKMARK_TABLE_NAME, values);
				break;
			case Tags:
				result = bulkLoad(TAG_TABLE_NAME, values);
				break;
			case Notes:
				result = bulkLoad(NOTE_TABLE_NAME, values);
				break;
			default:
				throw new IllegalArgumentException("Unknown Uri: " + uri);
		}
		
		getContext().getContentResolver().notifyChange(uri, null, false);
		
		return result;
	}
	
	private int bulkLoad(String table, ContentValues[] values){
        SQLiteDatabase db = dbHelper.getWritableDatabase();
		int inserted = 0;
		
		db.beginTransaction();
		
		try{
			for(ContentValues v : values) {
				db.insert(table, null, v);
			}
			
			db.setTransactionSuccessful();
			inserted = values.length;
		}
		finally{
			db.endTransaction();
		}

		return inserted;
	}
	
    private static UriMatcher buildUriMatcher() {
        UriMatcher matcher =  new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(AUTHORITY, "bookmark", Bookmarks);
        matcher.addURI(AUTHORITY, "bookmark/#", BookmarkId);
        matcher.addURI(AUTHORITY, "tag", Tags);
        matcher.addURI(AUTHORITY, "note", Notes);
        matcher.addURI(AUTHORITY, "note/#", NoteId);
        matcher.addURI(AUTHORITY, "unreadcount", UnreadCount);
        matcher.addURI(AUTHORITY, "global/" + SearchManager.SUGGEST_URI_PATH_QUERY, GlobalSearchSuggest);
        matcher.addURI(AUTHORITY, "global/" + SearchManager.SUGGEST_URI_PATH_QUERY + "/*", GlobalSearchSuggest);
        matcher.addURI(AUTHORITY, "main/" + SearchManager.SUGGEST_URI_PATH_QUERY, SearchSuggest);
        matcher.addURI(AUTHORITY, "main/" + SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SearchSuggest);
        matcher.addURI(AUTHORITY, "tag/" + SearchManager.SUGGEST_URI_PATH_QUERY, TagSearchSuggest);
        matcher.addURI(AUTHORITY, "tag/" + SearchManager.SUGGEST_URI_PATH_QUERY + "/*", TagSearchSuggest);
        matcher.addURI(AUTHORITY, "bookmark/" + SearchManager.SUGGEST_URI_PATH_QUERY, BookmarkSearchSuggest);
        matcher.addURI(AUTHORITY, "bookmark/" + SearchManager.SUGGEST_URI_PATH_QUERY + "/*", BookmarkSearchSuggest);
        matcher.addURI(AUTHORITY, "note/" + SearchManager.SUGGEST_URI_PATH_QUERY, NoteSearchSuggest);
        matcher.addURI(AUTHORITY, "note/" + SearchManager.SUGGEST_URI_PATH_QUERY + "/*", NoteSearchSuggest);
        return matcher;
    }

}
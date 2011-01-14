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

package com.pindroid.activity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import org.apache.http.auth.AuthenticationException;

import com.pindroid.R;
import com.pindroid.action.BookmarkTaskArgs;
import com.pindroid.client.PinboardApi;
import com.pindroid.client.NetworkUtilities;
import com.pindroid.platform.BookmarkManager;
import com.pindroid.platform.TagManager;
import com.pindroid.providers.ContentNotFoundException;
import com.pindroid.providers.BookmarkContent.Bookmark;
import com.pindroid.providers.TagContent.Tag;
import com.pindroid.ui.TagSpan;
import com.pindroid.util.StringUtils;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class AddBookmark extends AppBaseActivity implements View.OnClickListener{

	private EditText mEditUrl;
	private EditText mEditDescription;
	private ProgressBar mDescriptionProgress;
	private EditText mEditNotes;
	private EditText mEditTags;
	private TextView mRecommendedTags;
	private ProgressBar mRecommendedProgress;
	private TextView mPopularTags;
	private ProgressBar mPopularProgress;
	private CheckBox mPrivate;
	private CheckBox mToRead;
	private Button mButtonSave;
	private Button mButtonCancel;
	private Bookmark bookmark;
	Thread background;
	private Boolean update = false;
	
	private Bookmark oldBookmark;
	
	private long updateTime = 0;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.add_bookmark);
		mEditUrl = (EditText) findViewById(R.id.add_edit_url);
		mEditDescription = (EditText) findViewById(R.id.add_edit_description);
		mDescriptionProgress = (ProgressBar) findViewById(R.id.add_description_progress);
		mEditNotes = (EditText) findViewById(R.id.add_edit_notes);
		mEditTags = (EditText) findViewById(R.id.add_edit_tags);
		mRecommendedTags = (TextView) findViewById(R.id.add_recommended_tags);
		mRecommendedProgress = (ProgressBar) findViewById(R.id.add_recommended_tags_progress);
		mPopularTags = (TextView) findViewById(R.id.add_popular_tags);
		mPopularProgress = (ProgressBar) findViewById(R.id.add_popular_tags_progress);
		mPrivate = (CheckBox) findViewById(R.id.add_edit_private);
		mToRead = (CheckBox) findViewById(R.id.add_edit_toread);
		mButtonSave = (Button) findViewById(R.id.add_button_save);
		mButtonCancel = (Button) findViewById(R.id.add_button_cancel);
		
		mRecommendedTags.setMovementMethod(LinkMovementMethod.getInstance());
		mPopularTags.setMovementMethod(LinkMovementMethod.getInstance());

		if(savedInstanceState ==  null){
			Intent intent = getIntent();
			
			if(Intent.ACTION_SEND.equals(intent.getAction())){
				String extraData = intent.getStringExtra(Intent.EXTRA_TEXT);
				
				String url = StringUtils.getUrl(extraData);
				
				mEditUrl.setText(url);
				
				new GetWebpageTitleTask().execute(url);
				
				setDefaultValues();
			} else if(Intent.ACTION_EDIT.equals(intent.getAction())){
				int id = Integer.parseInt(intent.getData().getLastPathSegment());
				try {
					Bookmark b = BookmarkManager.GetById(id, mContext);
					oldBookmark = b.copy();
					
					mEditUrl.setText(b.getUrl());
					mEditDescription.setText(b.getDescription());
					mEditNotes.setText(b.getNotes());
					mEditTags.setText(b.getTagString());
					mPrivate.setChecked(!b.getShared());
					mToRead.setChecked(b.getToRead());
					updateTime = b.getTime();
					
					update = true;
				} catch (ContentNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				mEditUrl.requestFocus();
				setDefaultValues();
			}
		}
		
		if(update)
			setTitle(res.getString(R.string.add_bookmark_edit_title));
		else setTitle(res.getString(R.string.add_bookmark_add_title));
		
		mEditUrl.setOnFocusChangeListener(new OnFocusChangeListener(){
			public void onFocusChange(View v, boolean hasFocus) {
				if(!hasFocus){
					String url = mEditUrl.getText().toString();
					
					if(mEditDescription.getText().toString().equals("")) {
						new GetWebpageTitleTask().execute(url);
					}
					new GetTagSuggestionsTask().execute(url);
				}
			}
		});

		mButtonSave.setOnClickListener(this);
		mButtonCancel.setOnClickListener(this);
	}
	
	private void setDefaultValues(){   	
    	mPrivate.setChecked(privateDefault);
    	mToRead.setChecked(toreadDefault);
	}
	
    private void save() {

		String url = mEditUrl.getText().toString();
		
		if(mEditDescription.getText().toString().equals("")) {
			mEditDescription.setText(url);
		}
		
		if(!url.startsWith("http")){
			url = "http://" + url;
		}

		if(!update) {
			Date d = new Date();
			updateTime = d.getTime();
		}
		
		bookmark = new Bookmark(url, mEditDescription.getText().toString(), 
				mEditNotes.getText().toString(), mEditTags.getText().toString(),
				!mPrivate.isChecked(), mToRead.isChecked(), updateTime);
		
		BookmarkTaskArgs args = new BookmarkTaskArgs(bookmark, mAccount, mContext);
		
		new AddBookmarkTask().execute(args);
    }

    /**
     * {@inheritDoc}
     */
    public void onClick(View v) {
        if (v == mButtonSave) {
            save();
        } else if(v == mButtonCancel) {
        	finish();
        }
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    return true;
	}
    
    TagSpan.OnTagClickListener tagOnClickListener = new TagSpan.OnTagClickListener() {
        public void onTagClick(String tag) {
        	String currentTagString = mEditTags.getText().toString();
        	
        	ArrayList<String> currentTags = new ArrayList<String>();
        	Collections.addAll(currentTags, currentTagString.split(" "));
        	
        	if(tag != null && tag != "") {
        		if(!currentTags.contains(tag)) {
		        	currentTags.add(tag);
        		} else {
        			currentTags.remove(tag);
        		}
        		mEditTags.setText(TextUtils.join(" ", currentTags.toArray()).trim());
        	}
        }
    };

    private class AddBookmarkTask extends AsyncTask<BookmarkTaskArgs, Integer, Boolean>{
    	private Context context;
    	private Bookmark bookmark;
    	private Account account;
    	private ProgressDialog progress;
    	
        protected void onPreExecute() {
	        progress = new ProgressDialog(mContext);
	        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
	        progress.setMessage(res.getString(R.string.add_bookmark_task_progress));
	        progress.setCancelable(true);
	        progress.show();
        }
    	
    	@Override
    	protected Boolean doInBackground(BookmarkTaskArgs... args) {
    		context = args[0].getContext();
    		bookmark = args[0].getBookmark();
    		account = args[0].getAccount();
    		
    		try {
    			Boolean success = PinboardApi.addBookmark(bookmark, account, context);
    			if(success){
    				if(update){
    					BookmarkManager.UpdateBookmark(bookmark, account.name, context);
    				} else {
    					BookmarkManager.AddBookmark(bookmark, account.name, context);
    				}
    				return true;
    			} else return false;
    		} catch (Exception e) {
    			Log.d("addBookmark error", e.toString());
    			return false;
    		}
    	}

        protected void onPostExecute(Boolean result) {
        	progress.dismiss();
        	
    		if(result){
    			for(Tag t : bookmark.getTags()){   				
    				TagManager.UpsertTag(t, account.name, context);
    			}
    			
    			if(update) {
        			for(Tag t : oldBookmark.getTags()){
        				if(!bookmark.getTags().contains(t)) {
        					TagManager.UpleteTag(t, account.name, context);
        				}
        			}
    			}
    			
    			String msg = null;
    			if(update)
    				msg = res.getString(R.string.edit_bookmark_success_msg);
    			else msg = res.getString(R.string.add_bookmark_success_msg);
    			
    			Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    		} else {
    			Toast.makeText(context, res.getString(R.string.add_bookmark_error_msg), Toast.LENGTH_SHORT).show();
    		}
    		
    		finish();
        }
    }
    
    public class GetWebpageTitleTask extends AsyncTask<String, Integer, String>{
    	private String url;
    	
    	@Override
    	protected String doInBackground(String... args) {
    		
    		if(args.length > 0 && args[0] != null && args[0] != "") {
	    		url = args[0];
		
	    		return NetworkUtilities.getWebpageTitle(url);
    		} else return "";
    		
    	}
    	
    	protected void onPreExecute(){
    		mDescriptionProgress.setVisibility(View.VISIBLE);
    	}
    	
        protected void onPostExecute(String result) {
        	mEditDescription.setText(Html.fromHtml(result));
        	mDescriptionProgress.setVisibility(View.GONE);
        }
    }
    
    public class GetTagSuggestionsTask extends AsyncTask<String, Integer, ArrayList<Tag>>{
    	private String url;
    	
    	@Override
    	protected ArrayList<Tag> doInBackground(String... args) {
    		url = args[0];
	
    		try {
				return PinboardApi.getSuggestedTags(url, mAccount, mContext);
			} catch (AuthenticationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
    	}
    	
    	protected void onPreExecute() {
    		mRecommendedTags.setVisibility(View.GONE);
    		mPopularTags.setVisibility(View.GONE);
    		mRecommendedProgress.setVisibility(View.VISIBLE);
    		mPopularProgress.setVisibility(View.VISIBLE);
    	}
    	
        protected void onPostExecute(ArrayList<Tag> result) {
        	        	
        	if(result != null) {
        		SpannableStringBuilder recommendedBuilder = new SpannableStringBuilder();
        		SpannableStringBuilder popularBuilder = new SpannableStringBuilder();

        		for(Tag t : result) {
        			if(t.getType().equals("recommended")) {
        				addTag(recommendedBuilder, t);
        			} else if(t.getType().equals("popular")) {
        				addTag(popularBuilder, t);
        			}
        		}
        		
        		mRecommendedTags.setText(recommendedBuilder);
        		mPopularTags.setText(popularBuilder);
        		
        		mRecommendedTags.setVisibility(View.VISIBLE);
        		mPopularTags.setVisibility(View.VISIBLE);
        		mRecommendedProgress.setVisibility(View.GONE);
        		mPopularProgress.setVisibility(View.GONE);
        	} 	
        }

		private void addTag(SpannableStringBuilder builder, Tag t) {
			int flags = 0;
			
			if (builder.length() != 0) {
				builder.append("  ");
			}
			
			int start = builder.length();
			builder.append(t.getTagName());
			int end = builder.length();
			
			TagSpan span = new TagSpan(t.getTagName());
			span.setOnTagClickListener(tagOnClickListener);

			builder.setSpan(span, start, end, flags);
		}
    }
}
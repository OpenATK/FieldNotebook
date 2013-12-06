package com.openatk.fieldnotebook.notelist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.List;
import java.util.UUID;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.openatk.fieldnotebook.FragmentDrawing;
import com.openatk.fieldnotebook.R;
import com.openatk.fieldnotebook.ScrollAutoView;
import com.openatk.fieldnotebook.FragmentDrawing.DrawingListener;
import com.openatk.fieldnotebook.db.DatabaseHelper;
import com.openatk.fieldnotebook.db.Field;
import com.openatk.fieldnotebook.db.Image;
import com.openatk.fieldnotebook.db.Note;
import com.openatk.fieldnotebook.db.TableImages;
import com.openatk.fieldnotebook.db.TableNotes;
import com.openatk.fieldnotebook.drawing.MyMarker;
import com.openatk.fieldnotebook.drawing.MyPolygon;
import com.openatk.fieldnotebook.drawing.MyPolyline;
import com.openatk.fieldnotebook.imageviewer.FragmentImageViewer;
import com.openatk.fieldnotebook.imageviewer.ImageViewerListener;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Align;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TableLayout.LayoutParams;

public class FragmentNoteList extends Fragment implements OnClickListener, DrawingListener, ImageViewerListener {
	private static String TAG = FragmentNoteList.class.getName();
	
	private FragmentDrawing fragmentDrawing = null;
	private FragmentNoteList me = null;
	private GoogleMap map;

	private ScrollAutoView svNotes;
	private LinearLayout listNotes;
	
	private NoteListListener listener;
	private Field currentField = null;
	private List<Note> notes = null;
	
	private DatabaseHelper dbHelper;
	private Note currentNote = null;
	OpenNoteView currentOpenNoteView = null;
	private RelativeLayout currentNoteView = null;
	
	LayoutInflater vi;
	
	private Boolean addingPolygon = false;
	private Boolean addingPolyline = false;
	private Boolean addingPoint = false;

	private Boolean addingNote = false;  //Or editing note
	
	private MyPolyline currentPolyline = null;
	private MyMarker currentPoint = null;

	private String imagePath = null;
	private Image currentImage = null; //Current image for imageviewer
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_note_list, container,
				false);

		me = this;
		
		svNotes = (ScrollAutoView) view.findViewById(R.id.note_list_scrollView);
		listNotes = (LinearLayout) view.findViewById(R.id.note_list_listNotes);
		
		dbHelper = new DatabaseHelper(this.getActivity());
		vi = (LayoutInflater) this.getActivity().getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		return view;
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		Fragment parentFragment = getParentFragment();
		if (parentFragment != null && parentFragment instanceof NoteListListener) {
			// Check if parent fragment (if there is one) is listener
			listener = (NoteListListener) parentFragment;
		} else if (activity != null && activity instanceof NoteListListener) {
			// Otherwise, check if parent activity is listener
			listener = (NoteListListener) activity;
		} else if(parentFragment != null && parentFragment instanceof NoteListParentListener){
			//Otherwise check if parent fragment knows who the listener is
			listener = ((NoteListParentListener)parentFragment).NoteListGetListener();
		} else if(activity != null && activity instanceof NoteListParentListener){
			//Otherwise check if parent activity knows who the listener is
			listener = ((NoteListParentListener)activity).NoteListGetListener();
		}
		else if (listener == null) {
			Log.w("FragmentNoteList", "onAttach: neither the parent fragment or parent activity implement NoteListListener");
			throw new ClassCastException("Parent Activity or parent fragment must implement NoteListListener");
		}
		Log.d("FragmentNoteList", "Attached");
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		listener.NoteListRequestData(this);
	}

	public void populateData(Integer currentFieldId, GoogleMap map) {
		Log.d("FragmentNoteList", "PopulateData");
		this.map = map;
				
		//Clear current
		listNotes.removeAllViews();
		this.onClose();
		
		//Get current field
		currentField = null;
		if(currentFieldId != null){
			currentField = Field.FindFieldById(dbHelper.getReadableDatabase(), currentFieldId);
			dbHelper.close();
		}
		if (currentField != null) {
			//Add all notes for this field
			notes = Note.FindNotesByFieldName(dbHelper.getReadableDatabase(), currentField.getName());
			dbHelper.close();
			for(int i=0; i<notes.size(); i++){
				//Add note to list
				listNotes.addView(inflateNote(notes.get(i)));
			}
		} else {
			notes = null;
		}
	}
		
	public void finishPolygon(MyPolygon newPolygon){
		if(currentNote != null){
			//TODO handle edit finish? Maybe not, i think i removed on edit?
			newPolygon.setStrokeColor(Field.STROKE_COLOR);
			currentNote.addMyPolygon(newPolygon); //Adds a mypolygon
			currentNote.setColor(currentNote.getColor());
		}
	}
	
	private View inflateNote(Note note){
		View view = vi.inflate(R.layout.note, null);
		NoteView noteView = new NoteView();
		noteView.layNote = (RelativeLayout) view.findViewById(R.id.note);
		noteView.imgColor = (ImageView) view.findViewById(R.id.note_imgColor);
		noteView.butShowHide = (ImageButton) view.findViewById(R.id.note_butShowHide);
		noteView.tvComment = (TextView) view.findViewById(R.id.note_txtComment);
		noteView.imgPoints = (ImageView) view.findViewById(R.id.note_imgPoints);
		noteView.imgLines = (ImageView) view.findViewById(R.id.note_imgLines);
		noteView.imgPolygons = (ImageView) view.findViewById(R.id.note_imgPolygons);
		noteView.row1 = (RelativeLayout) view.findViewById(R.id.note_row1);
		noteView.row2 = (RelativeLayout) view.findViewById(R.id.note_row2);
		noteView.note = note;
		
		noteView.tvComment.setText(note.getComment());
		
		noteView.tvComment.setTag(noteView);
		noteView.butShowHide.setTag(noteView);
		noteView.layNote.setTag(noteView);
		
		noteView.tvComment.setOnClickListener(noteClickListener);
		noteView.butShowHide.setOnClickListener(noteClickListener);
		noteView.layNote.setOnClickListener(noteClickListener);
		
		if(note.getVisible() == 1){
			noteView.butShowHide.setImageResource(R.drawable.note_but_hide);
		} else {
			noteView.butShowHide.setImageResource(R.drawable.note_but_show);
		}
		
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(Color.BLACK);
		paint.setShadowLayer(2f, 0f, 2f, Color.LTGRAY);
		paint.setTextAlign(Align.RIGHT);
		paint.setTextSize(20);
		paint.setStrokeWidth(20);
		
		//Bitmap.Config conf = Bitmap.Config.ARGB_8888;
		//Bitmap bitmap = Bitmap.createBitmap(bounds.width() + 5, bounds.height(), conf); //TODO create blank new bitmap
		if(note.getVisible() == 1){
			addNoteObjects(note);
		} else {
			removeNoteObjects(note);
		}
		noteView.imgColor.setBackgroundColor(note.getColor());
		
		//Show icon and draw number on icon
		Integer numberOfPolygons = note.getPolygons().size();
		if(numberOfPolygons == 0){
			noteView.imgPolygons.setVisibility(View.GONE);
		} else {
			noteView.imgPolygons.setVisibility(View.VISIBLE);
			String label = Integer.toString(numberOfPolygons);
			Bitmap bitmap = decodeMutableBitmapFromResourceId(this.getActivity(), R.drawable.polygon);
			Rect bounds = new Rect();
			paint.getTextBounds(label, 0, label.length(), bounds);
			float x = bitmap.getWidth() - 2.0f;
			float y = -1.0f * bounds.top + (bitmap.getHeight() * 0.06f);
			Canvas canvas = new Canvas(bitmap);
			canvas.drawText(label, x, y, paint);
			BitmapDrawable ob = new BitmapDrawable(getResources(), bitmap);
			noteView.imgPolygons.setBackgroundDrawable(ob);
		}
		Integer numberOfPolylines = note.getPolylines().size();
		if(numberOfPolylines == 0){
			noteView.imgLines.setVisibility(View.GONE);
		} else {
			noteView.imgLines.setVisibility(View.VISIBLE);
			String label = Integer.toString(numberOfPolylines);
			Bitmap bitmap = decodeMutableBitmapFromResourceId(this.getActivity(), R.drawable.line_v1);
			Rect bounds = new Rect();
			paint.getTextBounds(label, 0, label.length(), bounds);
			float x = bitmap.getWidth() - 2.0f;
			float y = -1.0f * bounds.top + (bitmap.getHeight() * 0.06f);
			Canvas canvas = new Canvas(bitmap);
			canvas.drawText(label, x, y, paint);
			BitmapDrawable ob = new BitmapDrawable(getResources(), bitmap);
			noteView.imgLines.setBackgroundDrawable(ob);
		}
		Integer numberOfPoints = note.getMarkers().size();
		if(numberOfPoints == 0){
			noteView.imgPoints.setVisibility(View.GONE);
		} else {
			noteView.imgPoints.setVisibility(View.VISIBLE);
			String label = Integer.toString(numberOfPoints);
			Bitmap bitmap = decodeMutableBitmapFromResourceId(this.getActivity(), R.drawable.point);
			Rect bounds = new Rect();
			paint.getTextBounds(label, 0, label.length(), bounds);
			float x = bitmap.getWidth() - 2.0f;
			float y = -1.0f * bounds.top + (bitmap.getHeight() * 0.06f);
			Canvas canvas = new Canvas(bitmap);
			canvas.drawText(label, x, y, paint);
			BitmapDrawable ob = new BitmapDrawable(getResources(), bitmap);
			noteView.imgPoints.setBackgroundDrawable(ob);
		}
		
		noteView.me = view;
		view.setTag(noteView);
		return view;
	}
	
	private void addNoteObjects(Note note){
		//Add polygons from note to map
		List<MyPolygon> myPolygons = note.getMyPolygons();
		if(myPolygons.isEmpty()){
			List<PolygonOptions> polygons = note.getPolygons(); //Gets map polygons
			for(int i=0; i<polygons.size(); i++){
				Polygon newPolygon = map.addPolygon(polygons.get(i));
				note.addMyPolygon(new MyPolygon(map, newPolygon)); //Adds back my polygons
			}
		} else {
			for(int i =0; i<myPolygons.size(); i++){
				myPolygons.get(i).unselect();
			}
		}
		//Add polylines from note to map
		List<MyPolyline> myPolylines = note.getMyPolylines();
		if(myPolylines.isEmpty()){
			List<PolylineOptions> polylines = note.getPolylines(); //Gets map polygons
			for(int i=0; i<polylines.size(); i++){
				note.addMyPolyline(new MyPolyline(map.addPolyline(polylines.get(i)), map)); //Adds back my polygons
			}
		} else {
			for(int i =0; i<myPolylines.size(); i++){
				myPolylines.get(i).unselect();
			}
		}
		//Add points from note to map
		List<MyMarker> myMarkers = note.getMyMarkers();
		if(myMarkers.isEmpty()){
			List<MarkerOptions> markers = note.getMarkers(); //Gets map markers
			for(int i=0; i<markers.size(); i++){
				note.addMyMarker(new MyMarker(map.addMarker(markers.get(i)), map)); //Adds back my markers
			}
		} else {
			for(int i =0; i<myMarkers.size(); i++){
				myMarkers.get(i).unselect();
			}
		}
		note.setColor(note.getColor());
	}
	private void removeNoteObjects(Note note){
		note.removePolygonsFromMap();
		note.removePolylinesFromMap();
		note.removeMarkersFromMap();
		note.removePolygons();
		note.removePolylines();
		note.removeMarkers();
	}
	private OnClickListener noteClickListener = new OnClickListener(){
		@Override
		public void onClick(View v) {
			Log.d("FragmentNoteList", "noteClickListener");
			NoteView noteView = (NoteView) v.getTag();
			if(v.getId() == R.id.note_butShowHide){
				Note note = noteView.note;
				if(note.getVisible() == 1){
					//Hide all things to do with this note
					removeNoteObjects(note);
					note.setVisible(0);
					noteView.butShowHide.setImageResource(R.drawable.note_but_show);
				} else {
					addNoteObjects(note);
					note.setVisible(1);
					noteView.butShowHide.setImageResource(R.drawable.note_but_hide);
				}
				SQLiteDatabase database = dbHelper.getWritableDatabase();
				ContentValues values = new ContentValues();
				values.put(TableNotes.COL_VISIBLE,note.getVisible());
				String where = TableNotes.COL_ID + " = " + note.getId();
				database.update(TableNotes.TABLE_NAME, values, where, null);
				database.close();
			} else if(v.getId() == R.id.note_txtComment){ 
				if(addingNote == false){
					addingNote = true;
					//Set focus
					OpenNoteView newView = openNote(noteView);
					newView.etComment.requestFocus();
					//Show keyboard
					InputMethodManager inputMethodManager = (InputMethodManager) me.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
				    if (inputMethodManager != null) {
				        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
				    }
				}  else {
					OpenNoteDone();
				}
			} else if(v.getId() == R.id.note){
				if(addingNote == false){
					addingNote = true;
					openNote(noteView);
				} else {
					OpenNoteDone();
				}
			}
		}
	};
	
	private OpenNoteView openNote(NoteView noteView){
		//svNotes.setScrollingEnabled(false);
		svNotes.scrollToAfterAdd(noteView.me.getTop());
		//Edit this note
		int index = listNotes.indexOfChild(noteView.me);
		currentNote = noteView.note;
		listNotes.removeView(noteView.me);
		View newView = inflateOpenNote(currentNote);
		listNotes.addView(newView, index);
		currentOpenNoteView = (OpenNoteView) newView.getTag();

		//Show drawing fragment
		fragmentDrawing = listener.NoteListShowDrawing();
		fragmentDrawing.setListener(me);
		return currentOpenNoteView;
	}
	
	static class NoteView
    {
		ImageView imgColor;
		ImageButton butShowHide;
		TextView tvComment;
		ImageView imgPoints;
		ImageView imgLines;
		ImageView imgPolygons;
		RelativeLayout row1;
		RelativeLayout row2;
		RelativeLayout layNote;
		Note note;
		View me;
    }
	
	private View inflateOpenNote(Note note){
		View view = vi.inflate(R.layout.note_open, null);
		final OpenNoteView noteView = new OpenNoteView();
		noteView.layFullNote = (RelativeLayout) view.findViewById(R.id.note_open_note);
		noteView.layImageView = (FrameLayout) view.findViewById(R.id.note_open_imageViewer);
		noteView.layNote = (RelativeLayout) view.findViewById(R.id.note_open);
		noteView.butDelete = (ImageButton) view.findViewById(R.id.note_open_butDelete);
		noteView.etComment = (EditText) view.findViewById(R.id.note_open_etComment);
		noteView.layObjects = (LinearLayout) view.findViewById(R.id.note_open_lay_objects);
		noteView.svObjects = (HorizontalScrollView) view.findViewById(R.id.note_open_sv_objects);

		noteView.etComment.setText(note.getComment());
		
		
		if(note.getVisible() == 0) addNoteObjects(note); //Add objects if they are hidden
		
		List<MyPolygon> polygons = note.getMyPolygons();
		List<MyPolyline> polylines = note.getMyPolylines();
		List<MyMarker> markers = note.getMyMarkers();
		
		for(int i=0; i<polygons.size(); i++){
			ImageView img = new ImageView(this.getActivity());
			img.setBackgroundResource(R.drawable.polygon);
			noteView.layObjects.addView(img);
		}
		for(int i=0; i<polylines.size(); i++){
			ImageView img = new ImageView(this.getActivity());
			img.setBackgroundResource(R.drawable.line_v1);
			noteView.layObjects.addView(img);
		}
		for(int i=0; i<markers.size(); i++){
			ImageView img = new ImageView(this.getActivity());
			img.setBackgroundResource(R.drawable.point);
			noteView.layObjects.addView(img);
		}
		
		//Add images
		List<Image> images = note.getImages();		
		if(images != null){
			Log.d("FragmentNoteList - inflateOpenNote", "Images not null : " + Integer.toString(images.size()));
			for(int i=0; i<images.size(); i++){
				ImageView img = new ImageView(this.getActivity());
				Drawable d = new BitmapDrawable(this.getResources(), images.get(i).getThumb());
				
				img.setBackgroundDrawable(d);
				img.setTag(images.get(i));
				img.setOnClickListener(imageClickListener);
				
				float scale = getResources().getDisplayMetrics().density;
				int dpAsPixels = (int) (7*scale + 0.5f); //Margin
				int widthAndHeight = (int) (50*scale + 0.5f);
				LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(widthAndHeight, widthAndHeight);
				layoutParams.setMargins(dpAsPixels, 0, dpAsPixels, 0);				
				noteView.layObjects.addView(img, layoutParams);
			}
		} else {
			Log.d("FragmentNoteList - inflateOpenNote", "Images null");
		}
		
		noteView.note = note;

		/*noteView.etComment.setImeActionLabel("Done", KeyEvent.KEYCODE_ENTER);
		noteView.etComment.setOnEditorActionListener(new OnEditorActionListener() {
		    @Override
		    public boolean onEditorAction(TextView v, int keyCode, KeyEvent event) {
		    	Log.d("EtEvent", "EtEvent");
		        if (event != null && (event.getAction() == KeyEvent.ACTION_DOWN) &&  (event.getKeyCode() == KeyEvent.KEYCODE_ENTER))
		        {               
		           // hide virtual keyboard
		           InputMethodManager imm =  (InputMethodManager)getActivity().getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		           imm.hideSoftInputFromWindow(noteView.etComment.getWindowToken(), 0);
		           return true;
		        }
		        return false;
		    }
		});*/
		
	
		noteView.butDelete.setTag(noteView);
		noteView.butDelete.setOnClickListener(openNoteClickListener);
		noteView.me = view;
		
		view.setTag(noteView);
		return view;
	}
	
	private void OpenNoteDone(){
		if(currentNote != null){
			//svNotes.setScrollingEnabled(true);
			if(addingPolygon){
				fragmentDrawing.setPolygonIcon(R.drawable.add_polygon, false);
				listener.NoteListCompletePolygon();
				addingPolygon = false;
			}
			
			if(addingPolyline){
				if(currentPolyline != null) currentPolyline.complete();
				map.setOnMapClickListener((OnMapClickListener) listener);
				map.setOnMarkerClickListener((OnMarkerClickListener) listener);
				map.setOnMarkerDragListener((OnMarkerDragListener) listener);
				
				//TODO handle edit finish? Maybe not, i think i removed on edit?
				currentPolyline.setColor(Field.STROKE_COLOR);
				currentNote.addMyPolyline(currentPolyline); //Adds a myPolyline
					
				fragmentDrawing.setPolylineIcon(R.drawable.add_line_v1, false);
				addingPolyline = false;
			}
			
			// hide virtual keyboard
	        InputMethodManager imm =  (InputMethodManager)getActivity().getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
	        imm.hideSoftInputFromWindow(this.currentOpenNoteView.etComment.getWindowToken(), 0);
	        
			//Save the note
			currentNote.setComment(this.currentOpenNoteView.etComment.getText().toString());
			SaveNote(currentNote);
			
			//Close the note
			int index = listNotes.indexOfChild(this.currentOpenNoteView.me);
			listNotes.removeView(this.currentOpenNoteView.me);
			listNotes.addView(inflateNote(currentNote), index);
			
			//Hide drawing fragment
			listener.NoteListHideDrawing();
			fragmentDrawing = null;
			
			currentNote = null;
			this.addingNote = false;
		}
	}
	private OnClickListener openNoteClickListener = new OnClickListener(){
		@Override
		public void onClick(View v) {
			OpenNoteView noteView = (OpenNoteView) v.getTag();
			if(v.getId() == R.id.note_open_butDelete){
				
			}
		}
	};
	static class OpenNoteView
    {
		RelativeLayout layFullNote;
		FrameLayout layImageView;
		ImageButton butDelete;
		EditText etComment;
		RelativeLayout layNote;
		HorizontalScrollView svObjects;
		LinearLayout layObjects;
		Note note;
		View me;
    }
	
	private void SaveNote(Note note){		
		SQLiteDatabase database = dbHelper.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(TableNotes.COL_COMMENT,note.getComment());
		values.put(TableNotes.COL_FIELD_NAME,note.getFieldName());
		values.put(TableNotes.COL_COLOR,note.getColor());

		//Save current my polygons to strpolygons
		note.myPolygonsToStringPolygons();
		//Save the polygons
		values.put(TableNotes.COL_POLYGONS, note.getStrPolygons());
		Log.d("SaveNote", "StrPolygons:" + note.getStrPolygons());
		//Save current my polylines to strpolylines
		note.myPolylinesToStringPolylines();
		//Save the polylines
		values.put(TableNotes.COL_LINES, note.getStrPolylines());
		Log.d("SaveNote", "StrPolylines:" + note.getStrPolylines());
		//Save current my polylines to strpolylines
		note.myMarkersToStringMarkers();
		//Save the polylines
		values.put(TableNotes.COL_POINTS, note.getStrMarkers());
		Log.d("SaveNote", "StrPoints:" + note.getStrMarkers());

		//TODO more stuff
		if(note.getId() == null){
			//New note
			Integer newId = (int) database.insert(TableNotes.TABLE_NAME, null, values);
			note.setId(newId);
		} else {
			//Editing note
			String where = TableNotes.COL_ID + " = " + note.getId();
			database.update(TableNotes.TABLE_NAME, values, where, null);
		}
		
		List<Image> images = note.getImages();
		if(images != null){
			for(int i=0; i<images.size(); i++){
				ContentValues values2 = new ContentValues();
				Image image = images.get(i);
				if(image.getId() == null){
					//New image, need to save in database
					ByteArrayOutputStream stream = new ByteArrayOutputStream();
					image.getThumb().compress(Bitmap.CompressFormat.PNG, 100, stream);
					byte[] byteArray = stream.toByteArray();
					values2.put(TableImages.COL_IMAGE, byteArray);
					values2.put(TableImages.COL_NOTE_ID, note.getId());
					values2.put(TableImages.COL_PATH, image.getPath());
					Integer newId = (int) database.insert(TableImages.TABLE_NAME, null, values2);
					image.setId(newId);
				}
			}
		} else {
			Log.d("FragmentNoteList", "Images null");
		}
		
		database.close();
		dbHelper.close();
	}
	
	public void onMapClick(LatLng position){
		Log.d("Here", "FragmentSlider - onMapClick");
		//Check if clicked on any of current notes objects
		if(this.currentNote == null){
			Log.d("onMapClick", "currentNote is null");
			//Loop through all notes checking if touched anything
			for(int i=0; i< this.listNotes.getChildCount(); i++){
				View view = this.listNotes.getChildAt(i);
				NoteView noteView = (NoteView) view.getTag();
				if(noteView != null){
					Note note = noteView.note;
					if(touchedPolylineInNote(note, position) != null || touchedPolygonInNote(note, position) != null){
						//Find noteview in listnotes
						//Open note
						addingNote = true;
						openNote(noteView);
						//break;
					}
				}
			}
		} else {
			Log.d("onMapClick", "Note is open checking if clicked note object");
			//Loop through current notes polygons checking if touched
			//Check if touched polyline
			MyPolyline touchedPolyline = touchedPolylineInNote(this.currentNote, position);
			if(touchedPolyline != null){
				//Touched a polyline, edit it
				touchedPolyline.edit();
				if(this.currentOpenNoteView != null){
					if(fragmentDrawing != null) fragmentDrawing.setPolylineIcon(R.drawable.close_line_v1, true);
				}
				currentPolyline = touchedPolyline;
				this.currentNote.removePolyline(touchedPolyline);
				addingPolyline = true;
			} else {
				//Check if touched polygon
				Log.d("onMapClick", "Checking if touched polygon");
				MyPolygon touchedPoly = touchedPolygonInNote(this.currentNote, position);
				if(touchedPoly != null){
					Log.d("onMapClick", "Touched a polygon");
					touchedPoly.edit();
					if(this.currentOpenNoteView != null){
						if(fragmentDrawing != null) fragmentDrawing.setPolygonIcon(R.drawable.close_polygon, true);
					}
					//Shouldn't recieve touch if already adding so this is fine
					this.currentNote.removePolygon(touchedPoly);
					listener.NoteListEditPolygon(touchedPoly);
					addingPolygon = true;
				} else {
					Log.d("onMapClick", "Did not touch a polygon");
					//Touched the map. Close current note
					OpenNoteDone();
				}
			}
		}
	}
	private MyPolygon touchedPolygonInNote(Note note, LatLng position){
		//Check if touched polygon
		List<MyPolygon> polys = note.getMyPolygons();
		MyPolygon touchedPoly = null;
		for(int i=0; i<polys.size(); i++){
			if(polys.get(i).wasTouched(position)){
				touchedPoly = polys.get(i);
				break;
			}
		}
		return touchedPoly;
	}
	private MyPolyline touchedPolylineInNote(Note note, LatLng position){
		List<MyPolyline> polylines = note.getMyPolylines();
		MyPolyline touchedPolyline = null;
		for(int i=0; i<polylines.size(); i++){
			if(polylines.get(i).wasTouched(position)){
				touchedPolyline = polylines.get(i);
				break;
			}
		}
		return touchedPolyline;
	}

	
	public void onClose(){
		if(this.currentNote != null){
			OpenNoteDone();
		}
		//Remove all notes polygons
		Log.d("FragmentSlider", "onClose");
		if(notes != null){
			for(int i=0; i<notes.size(); i++){
				notes.get(i).removePolygonsFromMap();
				notes.get(i).removePolylinesFromMap();
				notes.get(i).removeMarkersFromMap();
			}
		}
	}
	
	public int oneNoteHeight() {
		if(currentNoteView != null){
			RelativeLayout layout = (RelativeLayout) currentNoteView.findViewById(R.id.note_open);
			RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) layout.getLayoutParams();
			Log.d("Height:", Integer.toString(params.height));
			return params.height;
		}
		return 0;
	}
	
	public boolean hasNotes(){
		if(notes != null && notes.size() > 0){
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.slider_butShowElevation) {
			
		} else if (v.getId() == R.id.slider_butShowSoilType) {
			
		}
	}
	
	public boolean isAddingNote(){
		return this.addingNote;
	}
	
	private OnMapClickListener noteListMapClickListener = new OnMapClickListener(){
		@Override
		public void onMapClick(LatLng arg0) {
			Log.d("FragmentNoteList", "OnMapClick");
			currentPoint = new MyMarker(map, arg0);
			if(currentPoint != null){
				currentNote.addMyMarker(currentPoint); //Adds a myPoint
			}
			map.setOnMapClickListener((OnMapClickListener) listener);
			if(fragmentDrawing != null) fragmentDrawing.setPointIcon(R.drawable.add_point_v1, false);
			addingPoint = false;
		}
	};

	@Override
	public void DrawingClickPoint() {
		if(addingPoint == false){
			map.setOnMapClickListener(noteListMapClickListener);
			fragmentDrawing.setPointIcon(R.drawable.cancel_point_v1, true);
			addingPoint = true;
		} else {
			map.setOnMapClickListener((OnMapClickListener) listener);
			fragmentDrawing.setPointIcon(R.drawable.add_point_v1, false);
			currentNote.setColor(currentNote.getColor());
			addingPoint = false;
		}		
	}

	@Override
	public void DrawingClickPolyline() {
		if(addingPolyline == false){
			currentPolyline = new MyPolyline(map);
			currentPolyline.edit();
			fragmentDrawing.setPolylineIcon(R.drawable.close_line_v1, true);
			addingPolyline = true;
		} else {
			if(currentPolyline != null) currentPolyline.complete();
			map.setOnMapClickListener((OnMapClickListener) listener);
			map.setOnMarkerClickListener((OnMarkerClickListener) listener);
			map.setOnMarkerDragListener((OnMarkerDragListener) listener);
			
			if(currentNote != null){
				//TODO handle edit finish? Maybe not, i think i removed on edit?
				currentPolyline.setColor(Field.STROKE_COLOR);
				currentNote.addMyPolyline(currentPolyline); //Adds a myPolyline
			}
			fragmentDrawing.setPolylineIcon(R.drawable.add_line_v1, false);
			currentNote.setColor(currentNote.getColor());
			addingPolyline = false;
		}
	}

	@Override
	public void DrawingClickPolygon() {
		if(addingPolygon == false){
			fragmentDrawing.setPolygonIcon(R.drawable.close_polygon, true);
			listener.NoteListAddPolygon();
			addingPolygon = true;
		} else {
			fragmentDrawing.setPolygonIcon(R.drawable.add_polygon, false);
			listener.NoteListCompletePolygon();
			currentNote.setColor(currentNote.getColor());
			addingPolygon = false;
		}		
	}

	@Override
	public void DrawingClickColor() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("Pick a color");
		CharSequence colors[] = {"Red", "Yellow", "Blue", "Green"};
		builder.setItems(colors, new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int which) {
            	   int intColor = Color.GREEN;
            	   if(which == 0){
            		   //Red
            		   intColor = Color.RED;
            	   } else if(which == 1){
            		   //Yellow
            		   intColor = Color.YELLOW;
            	   } else if(which == 2){
            		   //Blue
            		   intColor = Color.BLUE;
            	   } else {
            		   //Green
            		   intColor = Color.GREEN;
            	   }
            	   //redraw polygons/polylines/points with new color
            	   currentNote.setColor(intColor);
               }
		});
		AlertDialog dialog = builder.create();
		dialog.show();		
	}

	@Override
	public void DrawingClickCamera() {
        // path to /data/data/yourapp/app_data/imageDir
		String file = UUID.randomUUID().toString() + ".jpg"; //Random filename
		// Create imageDir
        File f = new File(this.getActivity().getFilesDir(), file);
        imagePath = f.getAbsolutePath();
        //Create new file
        FileOutputStream fos;
		try {
			fos = this.getActivity().openFileOutput(file, Context.MODE_WORLD_WRITEABLE);
	        fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
        //Get reference to the file
        File newf = new File(this.getActivity().getFilesDir(), file);
        
        Uri outputFileUri = Uri.fromFile(newf);
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); 
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
        this.getActivity().startActivityForResult(cameraIntent, 999);
	}
	@Override
	public void DrawingUndo() {
		if(addingPolygon){
			this.listener.NoteListUndoPolygon();
		} else if(addingPolyline){
			if(this.currentPolyline != null) this.currentPolyline.undo();
		} else if(addingPoint){
			//TODO tricky
		}
	}
	@Override
	public void DrawingDelete() {
		Log.d("FragmentNoteList", "DrawingDelete");
		if(addingPolygon){
			Log.d("FragmentNoteList", "DrawingDelete Polygon");
			this.listener.NoteListDeletePolygon();
			fragmentDrawing.setPolygonIcon(R.drawable.add_polygon, false);
			addingPolygon = false;
		} else if(addingPolyline){
			if(this.currentPolyline != null) { 
				this.currentPolyline.delete();
				currentNote.removePolyline(this.currentPolyline);
				map.setOnMapClickListener((OnMapClickListener) listener);
				map.setOnMarkerClickListener((OnMarkerClickListener) listener);
				map.setOnMarkerDragListener((OnMarkerDragListener) listener);
			}
			fragmentDrawing.setPolylineIcon(R.drawable.add_line_v1, false);
			addingPolyline = false;
		} else if(addingPoint){
			//TODO tricky
		}
	}
	public void deletePolygon(MyPolygon poly){
		//Called by MainActivity from NoteListDeletePolygon
		if(this.currentNote != null){
			this.currentNote.removePolygon(poly);
		}
	}
	public void ImageCaptured(){
		if(imagePath != null){
			Log.d(TAG, "ImageCaptured");
			Bitmap fullImage = BitmapFactory.decodeFile(imagePath);
			Bitmap thumb = ThumbnailUtils.extractThumbnail(fullImage, 100, 100);
			ImageView img = new ImageView(this.getActivity());
			img.setOnClickListener(imageClickListener);
			img.setTag(new Image(thumb, imagePath));
			
			float scale = getResources().getDisplayMetrics().density;
			int dpAsPixels = (int) (7*scale + 0.5f); //Margin
			int widthAndHeight = (int) (50*scale + 0.5f);
			LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(widthAndHeight, widthAndHeight);
			layoutParams.setMargins(dpAsPixels, 0, dpAsPixels, 0);
			
			Drawable d = new BitmapDrawable(getResources(), thumb);
			img.setBackgroundDrawable(d);			
			this.currentOpenNoteView.layObjects.addView(img, layoutParams);
			
			//Save path and thumbnail in database
			if(currentNote != null){
				currentNote.addImage(thumb, imagePath);
			}
		}
	}
	private OnClickListener imageClickListener = new OnClickListener(){
		@Override
		public void onClick(View v) {
			currentImage = (Image) v.getTag(); 
			int height = currentOpenNoteView.layFullNote.getHeight();
			currentOpenNoteView.layFullNote.setVisibility(View.GONE);
			currentOpenNoteView.layImageView.setVisibility(View.VISIBLE);
			//currentOpenNoteView.me.setBackgroundResource(R.drawable.note_image_viewer);

			// Gets the layout params that will allow you to resize the layout
			RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) currentOpenNoteView.layImageView.getLayoutParams();
			// Changes the height and width to the specified *pixels*
			params.height = height;
			currentOpenNoteView.layImageView.setLayoutParams(params);
			
			// Prepare a transaction to add fragments to this fragment
			FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();
			// Add the list fragment to this fragment's layout
			Log.i(TAG, "onCreate: adding FragmentNoteList to FragmentSidebar");
			// Add the fragment to the this fragment's container layout
			FragmentImageViewer fragmentImageViewer = new FragmentImageViewer();
			fragmentTransaction.replace(currentOpenNoteView.layImageView.getId(), fragmentImageViewer, FragmentImageViewer.class.getName());
			// Commit the transaction
			fragmentTransaction.commit();
		}
	};
	public void ImageViewerRequestDataFullsize(FragmentImageViewer requester) {
		if(this.currentNote != null){
			requester.populateData(this.currentNote.getImages(), currentImage, 1);
		}
	}
	@Override
	public void ImageViewerRequestData(FragmentImageViewer requester) {
		if(this.currentNote != null){
			requester.populateData(this.currentNote.getImages(), currentImage, 0);
		}
	}
	@Override
	public void ImageViewerDone(Image image) {
		currentOpenNoteView.layFullNote.setVisibility(View.VISIBLE);
		currentOpenNoteView.layImageView.setVisibility(View.GONE);
	}
	@Override
	public void ImageViewerClick(Image image) {
		//Fullscreen
		Log.d(TAG, "ImageViewerClick");
		currentOpenNoteView.layFullNote.setVisibility(View.VISIBLE);
		currentOpenNoteView.layImageView.setVisibility(View.GONE);
		listener.NoteListShowImageViewer();
		//TODO pass images
		
	}
	public Boolean AddNote() {
		if(addingNote == false){
			Log.d("FragmentNoteList", "AddNote");
			this.addingNote = true;
			//Add a new note
			Note newNote = new Note(currentField.getName());
			notes.add(newNote);
			currentNote = newNote;
			
			View newView = inflateOpenNote(newNote);
			currentOpenNoteView = (OpenNoteView) newView.getTag();
			listNotes.addView(newView, 0);
			listener.NoteListAddNote();
			
			currentOpenNoteView.etComment.requestFocus();
			InputMethodManager inputMethodManager = (InputMethodManager) me.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		    if (inputMethodManager != null) {
		        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
		    }
		    
			svNotes.scrollTo(0, 0);
			
			//Show drawing fragment
			fragmentDrawing = listener.NoteListShowDrawing();
			fragmentDrawing.setListener(me);
			return true;
		}		
		return false;
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public static Bitmap decodeMutableBitmapFromResourceId(final Context context, final int bitmapResId) {
	    final Options bitmapOptions = new Options();
	    if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB)
	        bitmapOptions.inMutable = true;
	    Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), bitmapResId, bitmapOptions);
	    if (!bitmap.isMutable())
	        bitmap = convertToMutable(context, bitmap);
	    return bitmap;
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	public static Bitmap convertToMutable(final Context context, final Bitmap imgIn) {
	    final int width = imgIn.getWidth(), height = imgIn.getHeight();
	    final Config type = imgIn.getConfig();
	    File outputFile = null;
	    final File outputDir = context.getCacheDir();
	    try {
	        outputFile = File.createTempFile(Long.toString(System.currentTimeMillis()), null, outputDir);
	        outputFile.deleteOnExit();
	        final RandomAccessFile randomAccessFile = new RandomAccessFile(outputFile, "rw");
	        final FileChannel channel = randomAccessFile.getChannel();
	        final MappedByteBuffer map = channel.map(MapMode.READ_WRITE, 0, imgIn.getRowBytes() * height);
	        imgIn.copyPixelsToBuffer(map);
	        imgIn.recycle();
	        final Bitmap result = Bitmap.createBitmap(width, height, type);
	        map.position(0);
	        result.copyPixelsFromBuffer(map);
	        channel.close();
	        randomAccessFile.close();
	        outputFile.delete();
	        return result;
	    } catch (final Exception e) {
	    } finally {
	        if (outputFile != null)
	            outputFile.delete();
	    }
	    return null;
	}
}
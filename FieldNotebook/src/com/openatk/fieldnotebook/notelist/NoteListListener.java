package com.openatk.fieldnotebook.notelist;

import com.openatk.fieldnotebook.FragmentDrawing;
import com.openatk.fieldnotebook.drawing.MyPolygon;
import com.openatk.fieldnotebook.imageviewer.FragmentImageViewer;

/**
 * Interface to dispatch image selections.
 * 
 * @author Cyrus Bowman
 */
public interface NoteListListener {

	/**
	 * Inform the listener that an image has been selected.
	 * 
	 * @param imageItem
	 * @param position
	 */
	//public void SliderEditField();

	public void NoteListRequestData(FragmentNoteList requester);
	public void NoteListCompletePolygon();
	public void NoteListDeletePolygon();
	public void NoteListUndoPolygon();
	public void NoteListAddPolygon();
	public void NoteListEditPolygon(MyPolygon poly);
	public FragmentDrawing NoteListShowDrawing();
	public void NoteListHideDrawing();
	public FragmentImageViewer NoteListShowImageViewer();
	public void NoteListHideImageViewer();
	public void NoteListAddNote();

}
package fragmentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import streaming.StreamChunk;

public class SADFragmentation {
	
	// equated to per chunk size and index level size
	public static final int NO_OF_CHUNKS_PER_FRAG = 200;
	public static final int INDEX_LEVEL=1;
	public static final int TRANS_LEVEL=2;

	/*
	 * bluetooth: 3200 kBps = 25Mbps
	 * wifidirect: 32000 kBps = 250Mbps
	 */
	private int id=0;

	private HashMap<Integer, Fragment> fragments;
	
	public SADFragmentation(){
		fragments = new HashMap<Integer, Fragment>();
	}
	
	public void createFragment(ArrayList<StreamChunk> chunks) { //mainly used by broadcaster
		System.out.println("CREATED FRAGMENT "+ id + " with last chunk: " + chunks.get(chunks.size()-1).getChunkID());
		fragments.put(id, new Fragment(id++, chunks));
	}
	
	public void createFragment( int id, ArrayList<StreamChunk> chunks){ //mainly used by watcher 
		fragments.put(id, new Fragment(id, chunks));
		System.out.println( " created new fragment " + id + " firstchunkid: " + fragments.get(id).getFirstChunkID());
		if (chunks.size() == SADFragmentation.NO_OF_CHUNKS_PER_FRAG){
			fragments.get(id).setIndexComplete();
		}
	}
	
//	public int getFragSize(int id){
//		return fragments.get(id).gets;
//	}
	
	public Fragment getFragment(int id){
		return fragments.get(id);
	}
	
	public boolean doesExist(int id){
		if(fragments.get(id) != null){
			return true;
		}
		return false;
	}
	
	public double getTimeCreated(int id){
		return fragments.get(id).getTimeCreated();
	}
	
	public ArrayList<Integer> getFragments(){
		ArrayList<Integer> fIds = new ArrayList<Integer>();
		for(int key: fragments.keySet()){
			fIds.add(fragments.get(key).getId());
		}
		Collections.sort(fIds);
		return fIds;
	}
	
	public int getCurrIndex(){
		return fragments.size();
	}

	public void initTransLevelFrag(int id){
		ArrayList<StreamChunk> temp = new ArrayList<StreamChunk>(NO_OF_CHUNKS_PER_FRAG);
		for (int i=0; i<NO_OF_CHUNKS_PER_FRAG; i++){
			temp.add(null);
		}
		fragments.put(id, new Fragment(id, temp));
	}
	
	public void addChunkToFragment(int id, int pos, StreamChunk c){ //used by watcher. for adding transmission level chunks
		fragments.get(id).updateBundle(pos, c);
	}
	
	public void getBitrate(){
		
	}
}

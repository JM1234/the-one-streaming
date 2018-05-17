package fragmentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import streaming.StreamChunk;

public class SADFragmentation {
	
	// equated to per chunk size and index level size
	public static final int INDEX_LEVEL=1;
	public static final int TRANS_LEVEL=2;

	/*
	 * bluetooth: 3200 kBps = 25Mbps
	 * wifidirect: 32000 kBps = 250Mbps
	 */
	private int id=0;
	private int noOfChunksPerFrag;

	private HashMap<Integer, Fragment> fragments;
	ArrayList<StreamChunk> temp;
	
	public SADFragmentation(){
		fragments = new HashMap<Integer, Fragment>();
		temp = new ArrayList<StreamChunk>();
	}
	
	public void createFragment(List<StreamChunk> chunks) { //mainly used by broadcaster
		fragments.put(id, new Fragment(id++, chunks));
	}
	
	public void createFragment( int id, ArrayList<StreamChunk> chunks){ //mainly used by watcher 
		fragments.put(id, new Fragment(id, chunks));
		if (chunks.size() == noOfChunksPerFrag){
			fragments.get(id).setIndexComplete();
		}
	}
	
	public void setNoOfChunksPerFrag(int noOfChunksPerFrag){
		this.noOfChunksPerFrag=noOfChunksPerFrag;
	}
	
	public int getNoOfChunksPerFrag(){
		return noOfChunksPerFrag;
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
	
//	public ArrayList<Integer> getFragments(){
//		ArrayList<Integer> fIds = new ArrayList<Integer>();
//		for(int key: fragments.keySet()){
//			fIds.add(fragments.get(key).getId());
//		}
//		Collections.sort(fIds);
//		return fIds;
//	}
	
	public int getCurrIndex(){
		return fragments.size();
	}

	public void initTransLevelFrag(int id){
		temp.clear();
		for (int i=0; i<noOfChunksPerFrag; i++){
			temp.add(null);
		}
		fragments.put(id, new Fragment(id, temp));
	}
	
	public void addChunkToFragment(int id, int pos, StreamChunk c){ //used by watcher. for adding transmission level chunks
		fragments.get(id).updateBundle(pos, c);
	}
}

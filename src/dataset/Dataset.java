package dataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import featuresCalculation.Featurable;

public class Dataset extends Featurable {
	
	//Constructors----------------------------------------------------
	
	public Dataset() {
		super();
		
		this.slots = new ArrayList<Slot>();
	}
	
	//Properties------------------------------------------------------
	
	private List<Slot> slots;

	public List<Slot> getSlots() {
		List<Slot> result;
		
		result = Collections.unmodifiableList(slots);
		
		return result;
	}
	
	public void addSlot(Slot slot) {
		assert slot != null;
		
		slots.add(slot);
		slot.setDataset(this);
		slot.setRecord(null);
	}
	
	public void removeSlot(Slot slot) {
		assert slot != null;
		assert contains(slot);
		
		slots.remove(slot);
		slot.setDataset(null);
	}
	
	//Interface methods------------------------------------------------
	
	public boolean contains(Slot slot) {
		assert slot != null;
		
		boolean result;
		
		result = slots.contains(slot);
		
		return result;
	}
	
	public JSONObject toJSONObject(){
		JSONObject res;
		JSONArray children;
		
		res = new JSONObject();
		
		children = new JSONArray();
		for (Slot child : this.getSlots()) {
			children.add(child.getJSONObject());
		}
		res.put("children", children);
		
		return res;
	}
	
}
